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
    private var isSyncInProgress = false

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
                systemLogRepository?.recordLog(
                    action = SystemLogActions.REMOTE_UPDATE_RECEIVED,
                    entityType = table.uppercase(),
                    result = LogResult.SUCCESS,
                    description = "Atualização remota recebida via Realtime na tabela $table ($eventType).",
                    triggerSync = false
                )
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
                fetchAllRemoteData()
                syncPendingQueue()
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
                    fetchAllRemoteData()
                    syncPendingQueue()
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

                        val entities = remoteHosters.map { dto ->
                            val local = database.hosterDao().getHosterById(dto.id)
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
                        database.hosterDao().insertOrUpdateHosters(entities)
                        log("Hosters sincronizados do Supabase (${entities.size} membros da equipe).")
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
                        val entities = remoteShifts.map { dto ->
                            val local = database.shiftDao().getShiftById(dto.id)
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
                                syncStatus = local?.syncStatus ?: "SINCRONIZADO"
                            )
                        }
                        database.shiftDao().insertOrUpdateShifts(entities)
                        log("Turnos sincronizados e guardados em cache (${entities.size} turnos).")
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
                        val entities = remoteAttendances.map { dto ->
                            val local = database.attendanceDao().getAttendanceById(dto.id)
                            val remoteUpdatedAt = DateTimeUtils.fromIsoUtc(dto.updatedAt)
                            if (local != null && local.syncStatus == "PENDENTE" && local.updatedAt != remoteUpdatedAt) {
                                conflictCount++
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
                                isDeleted = dto.isDeleted || (local?.isDeleted == true && local.syncStatus == "PENDENTE"),
                                deletedAt = dto.deletedAt?.let { DateTimeUtils.fromIsoUtc(it) } ?: local?.deletedAt,
                                deletedBy = dto.deletedBy ?: local?.deletedBy,
                                deletionReason = dto.deletionReason ?: local?.deletionReason,
                                syncStatus = if (local?.syncStatus == "PENDENTE") "PENDENTE" else "SINCRONIZADO"
                            )
                        }
                        database.attendanceDao().insertOrUpdateAttendances(entities)
                        log("Atendimentos sincronizados em cache (${entities.size} registros).")
                        if (conflictCount > 0) {
                            systemLogRepository?.recordLog(
                                action = SystemLogActions.CONFLICT_DETECTED,
                                entityType = "ATTENDANCE",
                                result = LogResult.FAILURE,
                                description = "Detectados $conflictCount conflito(s) de sincronização entre cache local pendente e servidor.",
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
            log("Sincronização manual solicitada...")
            fetchAllRemoteData()
            syncPendingQueue()
        }
    }

    suspend fun retryFailedOperations() {
        withContext(Dispatchers.IO) {
            database.syncQueueDao().resetFailedToPending()
            log("Reenviando operações com falha...")
            updatePendingCount()
            syncPendingQueue()
        }
    }

    private suspend fun updatePendingCount() {
        val count = database.syncQueueDao().getPendingCount()
        _pendingCount.value = count
    }

    suspend fun syncPendingQueue() {
        if (!isOnline) {
            updatePendingCount()
            _syncState.value = if (_pendingCount.value > 0) SyncState.PENDENTE else SyncState.OFFLINE
            log("Não há conexão. ${_pendingCount.value} itens aguardando na fila.")
            return
        }

        if (isSyncInProgress) return
        isSyncInProgress = true

        withContext(Dispatchers.IO) {
            try {
                val pendingList = database.syncQueueDao().getPendingOperations()
                _pendingCount.value = pendingList.size

                if (pendingList.isEmpty()) {
                    _syncState.value = SyncState.SINCRONIZADO
                    _lastSyncTime.value = System.currentTimeMillis()
                    isSyncInProgress = false
                    log("Fila vazia. Todos os dados sincronizados.")
                    return@withContext
                }

                _syncState.value = SyncState.SINCRONIZANDO
                log("Enviando ${pendingList.size} operações pendentes para o Supabase...")
                val nonLogOpsCount = pendingList.count { it.entityType != "SYSTEM_LOG" }
                if (nonLogOpsCount > 0) {
                    systemLogRepository?.recordLog(
                        action = SystemLogActions.SYNC_STARTED,
                        entityType = "SYNC",
                        result = LogResult.SUCCESS,
                        description = "Sincronização iniciada com $nonLogOpsCount operação(ões) operacional(is) pendente(s).",
                        triggerSync = false
                    )
                }

                val service = SupabaseClient.getService(context)
                val moshi = SupabaseClient.moshi

                var hasError = false

                for (op in pendingList) {
                    try {
                        database.syncQueueDao().updateStatus(op.id, "SYNCING")
                        val success = processQueueItem(op, service, moshi)
                        if (success) {
                            database.syncQueueDao().delete(op.id)
                            log("Operação ${op.entityType} [${op.entityId.take(8)}] enviada com sucesso!")
                        } else {
                            hasError = true
                            val err = "Resposta com código de erro do servidor"
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
                        hasError = true
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

                val remaining = database.syncQueueDao().getPendingCount()
                _pendingCount.value = remaining

                _syncState.value = when {
                    hasError && remaining > 0 -> SyncState.ERRO
                    remaining > 0 -> SyncState.PENDENTE
                    else -> {
                        _lastSyncTime.value = System.currentTimeMillis()
                        SyncState.SINCRONIZADO
                    }
                }

                if (nonLogOpsCount > 0) {
                    systemLogRepository?.recordLog(
                        action = if (hasError) SystemLogActions.SYNC_FAILED else SystemLogActions.SYNC_FINISHED,
                        entityType = "SYNC",
                        result = if (hasError) LogResult.FAILURE else LogResult.SUCCESS,
                        description = if (hasError) {
                            "Sincronização concluída com falhas. $remaining item(ns) restante(s) na fila."
                        } else {
                            "Sincronização concluída com sucesso ($nonLogOpsCount operação(ões) enviada(s))."
                        },
                        triggerSync = false
                    )
                }

                // If sync completed without errors, refresh cache
                if (!hasError) {
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
                isSyncInProgress = false
            }
        }
    }

    private suspend fun processQueueItem(
        op: SyncQueueEntity,
        service: com.example.data.remote.SupabaseService,
        moshi: com.squareup.moshi.Moshi
    ): Boolean {
        return when (op.entityType) {
            "SHIFT" -> {
                val adapter = moshi.adapter(SupabaseShiftDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val res = service.upsertShift(dto)
                res.isSuccessful
            }
            "SHIFT_CLOSE" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return false
                val res = service.patchShift("eq.${op.entityId}", map)
                res.isSuccessful
            }
            "ATTENDANCE" -> {
                val adapter = moshi.adapter(SupabaseAttendanceDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val res = service.upsertAttendance(dto)
                res.isSuccessful
            }
            "ATTENDANCE_MEMBERS" -> {
                val type = Types.newParameterizedType(List::class.java, SupabaseMemberDto::class.java)
                val adapter = moshi.adapter<List<SupabaseMemberDto>>(type)
                val list = adapter.fromJson(op.payloadJson) ?: return false

                // Sanitize: recurrent members MUST have null origin so Postgres CHECK and Moshi match
                val sanitizedList = list.map { m ->
                    val cleanOrigin = if (m.classification == "NOVO") {
                        m.origin?.trim()?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    m.copy(origin = cleanOrigin)
                }

                // Delete previous members if re-syncing an edit
                try {
                    service.deleteMembersForAttendance("eq.${op.entityId}")
                } catch (e: Exception) {
                    Log.w("SyncManager", "Note deleting previous members: ${e.message}")
                }

                // 1. Try bulk upsert first
                val res = service.upsertMembers(sanitizedList)
                if (res.isSuccessful) {
                    true
                } else {
                    val bulkErr = res.errorBody()?.string() ?: "HTTP ${res.code()}"
                    log("Aviso no envio em lote de integrantes ($bulkErr). Tentando envio individual...", isError = true)

                    // 2. Fallback: send members individually
                    var allOk = true
                    var lastErr: String? = null
                    for (member in sanitizedList) {
                        val singleRes = service.upsertMember(member)
                        if (!singleRes.isSuccessful) {
                            allOk = false
                            lastErr = singleRes.errorBody()?.string() ?: "HTTP ${singleRes.code()}"
                            log("Falha ao enviar integrante ${member.memberNumber}: $lastErr", isError = true)
                        }
                    }
                    if (!allOk && lastErr != null) {
                        throw Exception(lastErr)
                    }
                    allOk
                }
            }
            "ATTENDANCE_AUDIT" -> {
                val adapter = moshi.adapter(SupabaseAuditDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val res = service.insertAudit(dto)
                res.isSuccessful
            }
            "QUEUE_EVENT" -> {
                val adapter = moshi.adapter(SupabaseQueueDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val res = service.insertQueueEvent(dto)
                res.isSuccessful
            }
            "HOSTER_ACTIVATION" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return false
                val res = service.updateHoster("eq.${op.entityId}", map)
                res.isSuccessful
            }
            "HOSTER_INSERT" -> {
                val adapter = moshi.adapter(SupabaseHosterDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val res = service.upsertHoster(dto)
                res.isSuccessful
            }
            "HOSTER_UPDATE" -> {
                val mapAdapter = moshi.adapter<Map<String, Any?>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                )
                val map = mapAdapter.fromJson(op.payloadJson) ?: return false
                val res = service.updateHoster("eq.${op.entityId}", map)
                res.isSuccessful
            }
            "HOSTER_DELETE" -> {
                val res = service.deleteHoster("eq.${op.entityId}")
                res.isSuccessful
            }
            "SYSTEM_LOG" -> {
                val adapter = moshi.adapter(SupabaseSystemLogDto::class.java)
                val dto = adapter.fromJson(op.payloadJson) ?: return false
                val nowIso = DateTimeUtils.toIsoUtc(System.currentTimeMillis())
                val res = service.upsertSystemLog(dto.copy(syncedAt = nowIso))
                if (res.isSuccessful) {
                    database.systemLogDao().markSynced(dto.id, System.currentTimeMillis())
                }
                res.isSuccessful
            }
            else -> true
        }
    }
}
