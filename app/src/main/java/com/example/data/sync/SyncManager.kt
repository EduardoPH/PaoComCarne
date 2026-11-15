package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.AttendanceMemberEntity
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.QueueEventEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.local.entity.SystemLogEntity
import com.example.data.remote.SupabaseAttendanceDto
import com.example.data.remote.SupabaseAuditDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHosterDto
import com.example.data.remote.SupabaseMemberDto
import com.example.data.remote.SupabaseQueueDto
import com.example.data.remote.SupabaseRealtimeClient
import com.example.data.remote.SupabaseShiftDto
import com.example.data.remote.SupabaseSystemLogDto
import com.example.data.repository.SystemLogRepository
import com.example.domain.model.LogResult
import com.example.domain.model.SyncState
import com.example.domain.model.SystemLogActions
import com.example.domain.util.DateTimeUtils
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncLogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)

class SyncManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _syncState = MutableStateFlow(SyncState.OFFLINE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val syncLogs: StateFlow<List<SyncLogEntry>> = _syncLogs.asStateFlow()

    val allQueueItems: Flow<List<SyncQueueEntity>> = database.syncQueueDao().getAllQueueFlow()

    var isOnline = false
        private set
    private val syncLock = Any()
    @Volatile
    private var isSyncInProgress = false
    @Volatile
    private var resyncRequested = false
    @Volatile
    private var pullAfterSyncRequested = false

    @Volatile
    private var systemLogRepository: SystemLogRepository? = null

    @Volatile
    private var onVersionCheckRequested: ((Boolean) -> Unit)? = null

    private val realtimeClient = SupabaseRealtimeClient(
        context = context,
        onDataTableChanged = { table, eventType ->
            scope.launch {
                log("Atualização em tempo real ($table • $eventType) recebida.")
                when (table) {
                    "attendances" -> {
                        fetchAttendancesFromRemote()
                        fetchAttendanceMembersFromRemote()
                    }
                    "shifts" -> fetchShiftsFromRemote()
                    "queue_events" -> fetchQueueEventsFromRemote()
                    "system_logs" -> fetchSystemLogsFromRemote()
                }
                if (table != "system_logs") {
                    systemLogRepository?.recordLog(
                        action = SystemLogActions.REMOTE_UPDATE_RECEIVED,
                        entityType = table.uppercase(),
                        result = LogResult.SUCCESS,
                        description = "Atualização remota recebida via Realtime na tabela $table ($eventType).",
                        triggerSync = true
                    )
                }
            }
        },
        onReleaseChanged = {
            log("Política de versão atualizada no servidor. Validando compatibilidade...")
            onVersionCheckRequested?.invoke(isOnline)
        }
    )

    fun attachDependencies(
        systemLogRepo: SystemLogRepository,
        versionCheckCallback: (Boolean) -> Unit
    ) {
        this.systemLogRepository = systemLogRepo
        this.onVersionCheckRequested = versionCheckCallback
        if (isOnline) {
            versionCheckCallback(true)
        }
    }

    init {
        registerNetworkCallback()
        scope.launch {
            checkInitialNetwork()
            seedInitialHostersIfEmpty()
            updatePendingCount()
            if (isOnline) {
                log("Conexão detectada. Inicializando sincronização e cache...")
                realtimeClient.start()
                onVersionCheckRequested?.invoke(true)
                syncPendingQueue(pullAfter = true)
            } else {
                log("Dispositivo offline. Operando em modo cache local.")
            }
        }
    }

    fun log(message: String, isError: Boolean = false) {
        val entry = SyncLogEntry(message = message, isError = isError)
        _syncLogs.value = (listOf(entry) + _syncLogs.value).take(100)
        if (isError) Log.e("SyncManager", message) else Log.i("SyncManager", message)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !isOnline
                isOnline = true
                log("Rede online restabelecida. Disparando sincronização...")
                realtimeClient.start()
                scope.launch {
                    if (wasOffline) {
                        systemLogRepository?.recordLog(
                            action = SystemLogActions.RECONNECT,
                            entityType = "SYNC",
                            result = LogResult.SUCCESS,
                            description = "Conectividade restabelecida. Iniciando verificação de versão e sincronização.",
                            triggerSync = false
                        )
                    }
                    onVersionCheckRequested?.invoke(true)
                    syncPendingQueue(pullAfter = true)
                }
            }

            override fun onLost(network: Network) {
                isOnline = false
                realtimeClient.stop()
                log("Rede perdida. Dados mantidos em cache local.", isError = true)
                scope.launch {
                    val count = database.syncQueueDao().getPendingCount()
                    _pendingCount.value = count
                    _syncState.value = if (count > 0) SyncState.PENDENTE else SyncState.OFFLINE
                }
            }
        })
    }

    private suspend fun checkInitialNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val count = database.syncQueueDao().getPendingCount()
        _pendingCount.value = count
        _syncState.value = when {
            !isOnline && count > 0 -> SyncState.PENDENTE
            !isOnline -> SyncState.OFFLINE
            count > 0 -> SyncState.PENDENTE
            else -> SyncState.SINCRONIZADO
        }
    }

    suspend fun seedInitialHostersIfEmpty() {
        withContext(Dispatchers.IO) {
            val validSalt = "c6bca501f28b49e6"
            val validHash = "e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9"

            // Clean up any legacy template dummy hosters
            val legacyDummyIds = listOf(
                "h-001-eduardo", "h-002-mariana", "h-003-lucas",
                "h-004-beatriz", "h-005-gabriel", "h-006-camila"
            )
            for (id in legacyDummyIds) {
                database.hosterDao().deleteHosterById(id)
            }

            // Ensure Admin user exists with verified hash and salt
            val admin = database.hosterDao().getHosterById("admin")
            if (admin == null) {
                val adminUser = HosterEntity(
                    id = "admin",
                    name = "Administrador",
                    authUserId = null,
                    pinHash = validHash,
                    pinSalt = validSalt,
                    isActivated = true,
                    active = true,
                    isAdmin = true,
                    createdAt = System.currentTimeMillis()
                )
                database.hosterDao().insertOrUpdateHoster(adminUser)
            } else if (!admin.isActivated || !admin.isAdmin || admin.pinHash != validHash) {
                database.hosterDao().insertOrUpdateHoster(
                    admin.copy(
                        name = "Administrador",
                        pinHash = validHash,
                        pinSalt = validSalt,
                        isActivated = true,
                        active = true,
                        isAdmin = true
                    )
                )
            }
        }
    }

    suspend fun fetchAllRemoteData() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                fetchHostersFromRemote()
                fetchShiftsFromRemote()
                fetchAttendancesFromRemote()
                fetchAttendanceMembersFromRemote()
                fetchQueueEventsFromRemote()
                fetchSystemLogsFromRemote()
                _lastSyncTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                log("Erro ao atualizar cache remoto: ${e.message}", isError = true)
            }
        }
    }

    suspend fun fetchHostersFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getHosters()
                if (response.isSuccessful) {
                    val remoteHosters = response.body().orEmpty()
                    if (remoteHosters.isNotEmpty()) {
                        val validSalt = "c6bca501f28b49e6"
                        val validHash = "e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9"
                        val remoteIds = remoteHosters.map { it.id }.toSet()

                        val entities = remoteHosters.mapNotNull { dto ->
                            val local = database.hosterDao().getHosterById(dto.id)
                            val hasPendingInQueue = database.syncQueueDao().countPendingForEntity(dto.id) > 0
                            if (local != null && hasPendingInQueue) {
                                // Keep pending local edits/deactivation until queue finishes
                                return@mapNotNull null
                            }

                            val isAdmin = dto.isAdmin || (dto.id == "admin") || (local?.isAdmin == true)
                            val pinHash = if (dto.id == "admin") validHash else (dto.pinHash ?: local?.pinHash ?: "")
                            val pinSalt = if (dto.id == "admin") validSalt else (dto.pinSalt ?: local?.pinSalt ?: "")
                            val isActivated = if (dto.id == "admin") true else (dto.isActivated || (local?.isActivated == true))

                            HosterEntity(
                                id = dto.id,
                                name = dto.name,
                                authUserId = dto.authUserId,
                                pinHash = pinHash,
                                pinSalt = pinSalt,
                                isActivated = isActivated,
                                active = dto.active,
                                isAdmin = isAdmin,
                                createdAt = System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) {
                            database.hosterDao().insertOrUpdateHosters(entities)
                        }

                        // Deactivate locally any non-admin hoster removed/deactivated on server
                        val localActives = database.hosterDao().getAllActiveHosters()
                        for (local in localActives) {
                            if (local.id != "admin" && local.id !in remoteIds) {
                                val hasPendingInQueue = database.syncQueueDao().countPendingForEntity(local.id) > 0
                                if (!hasPendingInQueue) {
                                    database.hosterDao().insertOrUpdateHoster(local.copy(active = false))
                                }
                            }
                        }
                        log("Hosters sincronizados do Supabase (${remoteHosters.size} membros ativos).")
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote hosters fetch note: ${e.message}")
            }
        }
    }

    suspend fun fetchShiftsFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getShifts()
                if (response.isSuccessful) {
                    val remoteShifts = response.body().orEmpty()
                    if (remoteShifts.isNotEmpty()) {
                        val entities = remoteShifts.mapNotNull { dto ->
                            val hasPendingInQueue = database.syncQueueDao().countPendingForEntity(dto.id) > 0
                            if (hasPendingInQueue) {
                                // Keep pending local shift untouched until local push completes
                                return@mapNotNull null
                            }
                            ShiftEntity(
                                id = dto.id,
                                hosterId = dto.hosterId,
                                hosterName = dto.hosterName,
                                period = dto.period,
                                openedAt = DateTimeUtils.fromIsoUtc(dto.openedAt),
                                closedAt = dto.closedAt?.let { DateTimeUtils.fromIsoUtc(it) },
                                status = dto.status,
                                deviceCreatedAt = DateTimeUtils.fromIsoUtc(dto.deviceCreatedAt),
                                syncedAt = System.currentTimeMillis(),
                                syncStatus = "SINCRONIZADO"
                            )
                        }
                        if (entities.isNotEmpty()) {
                            database.shiftDao().insertOrUpdateShifts(entities)
                        }
                        log("Turnos sincronizados e guardados em cache (${remoteShifts.size} turnos).")
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote shifts fetch note: ${e.message}")
            }
        }
    }

    suspend fun fetchAttendancesFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getAttendances()
                if (response.isSuccessful) {
                    val remoteAttendances = response.body().orEmpty()
                    if (remoteAttendances.isNotEmpty()) {
                        var conflictCount = 0
                        val entities = remoteAttendances.mapNotNull { dto ->
                            val local = database.attendanceDao().getAttendanceById(dto.id)
                            val hasPendingInQueue = database.syncQueueDao().countPendingForEntity(dto.id) > 0
                            val remoteUpdatedAt = DateTimeUtils.fromIsoUtc(dto.updatedAt)

                            if (local != null && hasPendingInQueue) {
                                // Only report a genuine conflict if remote was edited strictly after local (>2s)
                                if (remoteUpdatedAt > local.updatedAt + 2_000L) {
                                    conflictCount++
                                }
                                // Preserve pending local changes until queue pushes them
                                return@mapNotNull null
                            }

                            AttendanceEntity(
                                id = dto.id,
                                shiftId = dto.shiftId,
                                hosterId = dto.hosterId,
                                hosterName = dto.hosterName,
                                type = dto.type,
                                totalMembers = dto.totalMembers,
                                newMembers = dto.newMembers,
                                returningMembers = dto.returningMembers,
                                createdAt = DateTimeUtils.fromIsoUtc(dto.createdAt),
                                updatedAt = remoteUpdatedAt,
                                isEdited = local?.isEdited ?: false,
                                isPreferential = dto.isPreferential,
                                isDeleted = dto.isDeleted,
                                deletedAt = dto.deletedAt?.let { DateTimeUtils.fromIsoUtc(it) },
                                deletedBy = dto.deletedBy,
                                deletionReason = dto.deletionReason,
                                syncStatus = "SINCRONIZADO"
                            )
                        }
                        if (entities.isNotEmpty()) {
                            database.attendanceDao().insertOrUpdateAttendances(entities)
                        }
                        log("Atendimentos sincronizados em cache (${remoteAttendances.size} registros).")
                        if (conflictCount > 0) {
                            systemLogRepository?.recordLog(
                                action = SystemLogActions.CONFLICT_DETECTED,
                                entityType = "ATTENDANCE",
                                result = LogResult.FAILURE,
                                description = "Detectados $conflictCount conflito(s) de sincronização entre alterações locais pendentes e servidor.",
                                triggerSync = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote attendances fetch note: ${e.message}")
            }
        }
    }

    suspend fun fetchSystemLogsFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getSystemLogs()
                if (response.isSuccessful) {
                    val remoteLogs = response.body().orEmpty()
                    if (remoteLogs.isNotEmpty()) {
                        val entities = remoteLogs.map { dto ->
                            SystemLogEntity(
                                id = dto.id,
                                userId = dto.userId,
                                userName = dto.userName,
                                action = dto.action,
                                entityType = dto.entityType,
                                entityId = dto.entityId,
                                deviceName = dto.deviceName,
                                result = dto.result,
                                description = dto.description,
                                createdAt = DateTimeUtils.fromIsoUtc(dto.createdAt),
                                syncedAt = dto.syncedAt?.let { DateTimeUtils.fromIsoUtc(it) } ?: System.currentTimeMillis()
                            )
                        }
                        database.systemLogDao().insertLogs(entities)
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote system_logs fetch note: ${e.message}")
            }
        }
    }

    suspend fun fetchAttendanceMembersFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getAttendanceMembers()
                if (response.isSuccessful) {
                    val remoteMembers = response.body().orEmpty()
                    if (remoteMembers.isNotEmpty()) {
                        val entities = remoteMembers.map { dto ->
                            AttendanceMemberEntity(
                                id = dto.id,
                                attendanceId = dto.attendanceId,
                                memberNumber = dto.memberNumber,
                                classification = dto.classification,
                                origin = dto.origin,
                                createdAt = DateTimeUtils.fromIsoUtc(dto.createdAt)
                            )
                        }
                        database.attendanceMemberDao().insertMembers(entities)
                        log("Integrantes individuais carregados em cache (${entities.size} pessoas).")
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote members fetch note: ${e.message}")
            }
        }
    }

    suspend fun fetchQueueEventsFromRemote() {
        if (!isOnline) return
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val response = service.getQueueEvents()
                if (response.isSuccessful) {
                    val remoteQueue = response.body().orEmpty()
                    if (remoteQueue.isNotEmpty()) {
                        val entities = remoteQueue.map { dto ->
                            QueueEventEntity(
                                id = dto.id,
                                shiftId = dto.shiftId,
                                hosterId = dto.hosterId,
                                waitingDuos = dto.waitingDuos,
                                waitingGroups = dto.waitingGroups,
                                createdAt = DateTimeUtils.fromIsoUtc(dto.createdAt),
                                syncStatus = "SINCRONIZADO"
                            )
                        }
                        database.queueEventDao().insertQueueEvents(entities)
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote queue events note: ${e.message}")
            }
        }
    }

    fun triggerSync() {
        scope.launch {
            log("Sincronização solicitada...")
            syncPendingQueue(pullAfter = true)
        }
    }

    fun flushPendingQueueNow() {
        scope.launch {
            syncPendingQueue(pullAfter = false)
        }
    }

    suspend fun retryFailedOperations() {
        withContext(Dispatchers.IO) {
            database.syncQueueDao().resetFailedToPending()
            log("Reenviando operações com falha...")
            updatePendingCount()
            syncPendingQueue(pullAfter = true)
        }
    }

    private suspend fun updatePendingCount() {
        val count = database.syncQueueDao().getPendingCount()
        _pendingCount.value = count
    }

    suspend fun syncPendingQueue(pullAfter: Boolean = false) {
        if (!isOnline) {
            updatePendingCount()
            _syncState.value = if (_pendingCount.value > 0) SyncState.PENDENTE else SyncState.OFFLINE
            log("Não há conexão. ${_pendingCount.value} itens aguardando na fila.")
            return
        }

        synchronized(syncLock) {
            if (isSyncInProgress) {
                resyncRequested = true
                if (pullAfter) pullAfterSyncRequested = true
                return
            }
            isSyncInProgress = true
            resyncRequested = false
            pullAfterSyncRequested = pullAfter
        }

        var shouldTriggerFollowUp = false
        var followUpPullAfter = false

        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.getService(context)
                val moshi = SupabaseClient.moshi
                val attemptedIds = mutableSetOf<Long>()
                var totalBusinessOpsSynced = 0
                var hasAnyError = false

                // Drain loop: handles newly enqueued items & SYNC_STARTED/SYNC_FINISHED logs immediately
                while (isOnline) {
                    val allPending = database.syncQueueDao().getPendingOperations()
                    val batch = allPending.filter { op ->
                        op.status == "PENDING" || op.status == "SYNCING" || (op.status == "FAILED" && op.id !in attemptedIds)
                    }
                    if (batch.isEmpty()) break

                    _pendingCount.value = allPending.size
                    _syncState.value = SyncState.SINCRONIZANDO

                    val nonLogOpsCount = batch.count { it.entityType != "SYSTEM_LOG" }
                    if (nonLogOpsCount > 0) {
                        log("Enviando ${batch.size} operações pendentes para o Supabase...")
                        systemLogRepository?.recordLog(
                            action = SystemLogActions.SYNC_STARTED,
                            entityType = "SYNC",
                            result = LogResult.SUCCESS,
                            description = "Sincronização iniciada com $nonLogOpsCount operação(ões) operacional(is) pendente(s).",
                            triggerSync = false
                        )
                    }

                    var batchError = false
                    var batchBusinessSynced = 0

                    for (op in batch) {
                        attemptedIds.add(op.id)
                        try {
                            database.syncQueueDao().updateStatus(op.id, "SYNCING")
                            val (success, errorDetail) = processQueueItem(op, service, moshi)
                            if (success) {
                                database.syncQueueDao().delete(op.id)
                                markLocalEntitySynced(op)
                                if (op.entityType != "SYSTEM_LOG") {
                                    batchBusinessSynced++
                                    totalBusinessOpsSynced++
                                }
                                log("Operação ${op.entityType} [${op.entityId.take(8)}] enviada com sucesso!")
                            } else {
                                batchError = true
                                hasAnyError = true
                                val err = errorDetail ?: "Resposta com código de erro do servidor"
                                log("Falha ao enviar ${op.entityType} [${op.entityId.take(8)}]: $err", isError = true)
                                database.syncQueueDao().update(
                                    op.copy(
                                        status = "FAILED",
                                        attempts = op.attempts + 1,
                                        lastError = err,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            batchError = true
                            hasAnyError = true
                            val err = e.message ?: "Erro de rede"
                            log("Erro na operação ${op.entityType} [${op.entityId.take(8)}]: $err", isError = true)
                            database.syncQueueDao().update(
                                op.copy(
                                    status = "FAILED",
                                    attempts = op.attempts + 1,
                                    lastError = err,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    if (nonLogOpsCount > 0) {
                        val remainingNow = database.syncQueueDao().getPendingCount()
                        systemLogRepository?.recordLog(
                            action = if (batchError) SystemLogActions.SYNC_FAILED else SystemLogActions.SYNC_FINISHED,
                            entityType = "SYNC",
                            result = if (batchError) LogResult.FAILURE else LogResult.SUCCESS,
                            description = if (batchError) {
                                "Sincronização concluída com falhas. $remainingNow item(ns) restante(s) na fila."
                            } else {
                                "Sincronização concluída com sucesso ($batchBusinessSynced operação(ões) enviada(s))."
                            },
                            triggerSync = false
                        )
                    }
                }

                val remaining = database.syncQueueDao().getPendingCount()
                _pendingCount.value = remaining
                _syncState.value = when {
                    !isOnline && remaining > 0 -> SyncState.PENDENTE
                    !isOnline -> SyncState.OFFLINE
                    hasAnyError && remaining > 0 -> SyncState.ERRO
                    remaining > 0 -> SyncState.PENDENTE
                    else -> {
                        _lastSyncTime.value = System.currentTimeMillis()
                        SyncState.SINCRONIZADO
                    }
                }

                if (remaining == 0 && totalBusinessOpsSynced == 0 && !hasAnyError) {
                    log("Fila vazia. Todos os dados sincronizados.")
                }

                val shouldPull = synchronized(syncLock) { pullAfterSyncRequested }
                if (shouldPull && !hasAnyError && isOnline) {
                    fetchAllRemoteData()
                }
            } catch (e: Exception) {
                log("Erro no loop de sincronização: ${e.message}", isError = true)
                _syncState.value = SyncState.ERRO
                systemLogRepository?.recordLog(
                    action = SystemLogActions.SYNC_FAILED,
                    entityType = "SYNC",
                    result = LogResult.FAILURE,
                    description = "Erro crítico no loop de sincronização: ${e.message ?: "erro desconhecido"}",
                    triggerSync = false
                )
            } finally {
                synchronized(syncLock) {
                    isSyncInProgress = false
                    if (resyncRequested && isOnline) {
                        shouldTriggerFollowUp = true
                        followUpPullAfter = pullAfterSyncRequested
                        resyncRequested = false
                        pullAfterSyncRequested = false
                    } else {
                        resyncRequested = false
                        pullAfterSyncRequested = false
                    }
                }
            }
        }

        if (shouldTriggerFollowUp) {
            syncPendingQueue(pullAfter = followUpPullAfter)
        }
    }

    private suspend fun markLocalEntitySynced(op: SyncQueueEntity) {
        when (op.entityType) {
            "SHIFT", "SHIFT_CLOSE" -> {
                if (database.syncQueueDao().countPendingForEntity(op.entityId) == 0) {
                    database.shiftDao().updateSyncStatus(op.entityId, "SINCRONIZADO")
                }
            }
            "ATTENDANCE", "ATTENDANCE_MEMBERS" -> {
                if (database.syncQueueDao().countPendingForEntity(op.entityId) == 0) {
                    database.attendanceDao().updateSyncStatus(op.entityId, "SINCRONIZADO")
                }
            }
            "ATTENDANCE_AUDIT" -> {
                database.attendanceAuditDao().updateSyncStatus(op.entityId, "SINCRONIZADO")
            }
            "QUEUE_EVENT" -> {
                database.queueEventDao().updateSyncStatus(op.entityId, "SINCRONIZADO")
            }
        }
    }

    private fun extractError(res: retrofit2.Response<*>): String {
        val body = try {
            res.errorBody()?.string()?.take(250)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return if (body != null) "HTTP ${res.code()}: $body" else "HTTP ${res.code()}"
    }

    private suspend fun processQueueItem(
        op: SyncQueueEntity,
        service: com.example.data.remote.SupabaseService,
        moshi: com.squareup.moshi.Moshi
    ): Pair<Boolean, String?> {
        return when (op.entityType) {
            "SHIFT" -> {
                val adapter = moshi.adapter(SupabaseShiftDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de turno")
                val res = service.upsertShift(dto)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "SHIFT_CLOSE" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido ao encerrar turno")
                val res = service.patchShift("eq.${op.entityId}", map)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "ATTENDANCE" -> {
                val adapter = moshi.adapter(SupabaseAttendanceDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de atendimento")
                val res = service.upsertAttendance(dto)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "ATTENDANCE_MEMBERS" -> {
                val type = Types.newParameterizedType(List::class.java, SupabaseMemberDto::class.java)
                val adapter = moshi.adapter<List<SupabaseMemberDto>>(type)
                val list = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de integrantes")

                val sanitizedList = list.map { m ->
                    val cleanOrigin = if (m.classification == "NOVO") {
                        m.origin?.trim()?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    m.copy(origin = cleanOrigin)
                }

                try {
                    service.deleteMembersForAttendance("eq.${op.entityId}")
                } catch (e: Exception) {
                    Log.w("SyncManager", "Note deleting previous members: ${e.message}")
                }

                val res = service.upsertMembers(sanitizedList)
                if (res.isSuccessful) {
                    Pair(true, null)
                } else {
                    val bulkErr = extractError(res)
                    log("Aviso no envio em lote de integrantes ($bulkErr). Tentando envio individual...", isError = true)

                    var allOk = true
                    var lastErr: String? = bulkErr
                    for (member in sanitizedList) {
                        val singleRes = service.upsertMember(member)
                        if (!singleRes.isSuccessful) {
                            allOk = false
                            lastErr = extractError(singleRes)
                            log("Falha ao enviar integrante ${member.memberNumber}: $lastErr", isError = true)
                        }
                    }
                    Pair(allOk, if (allOk) null else lastErr)
                }
            }
            "ATTENDANCE_AUDIT" -> {
                val adapter = moshi.adapter(SupabaseAuditDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de auditoria")
                val res = service.insertAudit(dto)
                val ok = res.isSuccessful || res.code() == 409
                Pair(ok, if (ok) null else extractError(res))
            }
            "QUEUE_EVENT" -> {
                val adapter = moshi.adapter(SupabaseQueueDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de fila")
                val res = service.insertQueueEvent(dto)
                val ok = res.isSuccessful || res.code() == 409
                Pair(ok, if (ok) null else extractError(res))
            }
            "HOSTER_ACTIVATION" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de ativação")
                val res = service.updateHoster("eq.${op.entityId}", map)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "HOSTER_INSERT" -> {
                val adapter = moshi.adapter(SupabaseHosterDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de hoster")
                val res = service.upsertHoster(dto)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "HOSTER_UPDATE" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de atualização")
                val res = service.updateHoster("eq.${op.entityId}", map)
                Pair(res.isSuccessful, if (res.isSuccessful) null else extractError(res))
            }
            "HOSTER_DELETE" -> {
                val res = service.deleteHoster("eq.${op.entityId}")
                when {
                    res.isSuccessful || res.code() == 404 -> Pair(true, null)
                    res.code() == 409 -> {
                        // Hoster already has shifts/attendances/audits in Supabase (FK constraint 23503):
                        // deactivate (active = false) instead of hard delete to preserve historical records.
                        val patchRes = service.updateHoster("eq.${op.entityId}", mapOf("active" to false))
                        Pair(patchRes.isSuccessful, if (patchRes.isSuccessful) null else extractError(patchRes))
                    }
                    else -> Pair(false, extractError(res))
                }
            }
            "SYSTEM_LOG" -> {
                val adapter = moshi.adapter(SupabaseSystemLogDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return Pair(false, "Payload inválido de log")
                val nowIso = DateTimeUtils.toIsoUtc(System.currentTimeMillis())
                val res = service.upsertSystemLog(dto.copy(syncedAt = nowIso))
                val ok = res.isSuccessful || res.code() == 409
                if (ok) {
                    database.systemLogDao().markSynced(dto.id, System.currentTimeMillis())
                }
                Pair(ok, if (ok) null else extractError(res))
            }
            else -> Pair(true, null)
        }
    }
}
