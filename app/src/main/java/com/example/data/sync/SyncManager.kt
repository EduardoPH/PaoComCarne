package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.remote.SupabaseAttendanceDto
import com.example.data.remote.SupabaseAuditDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHosterDto
import com.example.data.remote.SupabaseMemberDto
import com.example.data.remote.SupabaseQueueDto
import com.example.data.remote.SupabaseShiftDto
import com.example.domain.model.SyncState
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _syncState = MutableStateFlow(SyncState.OFFLINE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private var isOnline = false
    private var isSyncInProgress = false

    init {
        registerNetworkCallback()
        scope.launch {
            checkInitialNetwork()
            seedInitialHostersIfEmpty()
            updatePendingCount()
            if (isOnline) {
                fetchHostersFromRemote()
                syncPendingQueue()
            }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
                scope.launch {
                    fetchHostersFromRemote()
                    syncPendingQueue()
                }
            }

            override fun onLost(network: Network) {
                isOnline = false
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
            val count = database.hosterDao().countHosters()
            if (count == 0) {
                // Admin user with password 'admin'
                val salt = com.example.domain.util.SecurityUtils.generateSalt()
                val hash = com.example.domain.util.SecurityUtils.hashPassword("admin", salt)

                val adminUser = HosterEntity(
                    id = "admin",
                    name = "Administrador",
                    authUserId = null,
                    pinHash = hash,
                    pinSalt = salt,
                    isActivated = true,
                    active = true,
                    isAdmin = true,
                    createdAt = System.currentTimeMillis()
                )
                database.hosterDao().insertOrUpdateHoster(adminUser)
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
                        val entities = remoteHosters.map { dto ->
                            val local = database.hosterDao().getHosterById(dto.id)
                            HosterEntity(
                                id = dto.id,
                                name = dto.name,
                                authUserId = dto.authUserId,
                                pinHash = dto.pinHash ?: local?.pinHash ?: "",
                                pinSalt = dto.pinSalt ?: local?.pinSalt ?: "",
                                isActivated = dto.isActivated || (local?.isActivated == true),
                                active = dto.active,
                                isAdmin = dto.isAdmin || (dto.id == "admin") || (local?.isAdmin == true),
                                createdAt = System.currentTimeMillis()
                            )
                        }
                        database.hosterDao().insertOrUpdateHosters(entities)
                    }
                }
            } catch (e: Exception) {
                Log.w("SyncManager", "Remote hosters fetch note: ${e.message}")
            }
        }
    }

    fun triggerSync() {
        scope.launch {
            fetchHostersFromRemote()
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
                    isSyncInProgress = false
                    return@withContext
                }

                _syncState.value = SyncState.SINCRONIZANDO
                val service = SupabaseClient.getService(context)
                val moshi = SupabaseClient.moshi

                var hasError = false

                for (op in pendingList) {
                    try {
                        database.syncQueueDao().updateStatus(op.id, "SYNCING")
                        val success = processQueueItem(op, service, moshi)
                        if (success) {
                            database.syncQueueDao().delete(op.id)
                        } else {
                            hasError = true
                            database.syncQueueDao().update(
                                op.copy(
                                    status = "FAILED",
                                    attempts = op.attempts + 1,
                                    lastError = "Remote returned error response",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: Exception) {
                        hasError = true
                        Log.e("SyncManager", "Error syncing item ${op.id}: ${e.message}")
                        database.syncQueueDao().update(
                            op.copy(
                                status = "FAILED",
                                attempts = op.attempts + 1,
                                lastError = e.message ?: "Network error",
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
                    else -> SyncState.SINCRONIZADO
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Sync loop error: ${e.message}")
                _syncState.value = SyncState.ERRO
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
                // Delete previous members if re-syncing an edit
                service.deleteMembersForAttendance("eq.${op.entityId}")
                val res = service.upsertMembers(list)
                res.isSuccessful
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
            else -> true
        }
    }
}
