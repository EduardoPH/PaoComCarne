package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DeviceIdentityManager
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.local.entity.SystemLogEntity
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseSystemLogDto
import com.example.domain.model.LogResult
import com.example.domain.model.SystemLog
import com.example.domain.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SystemLogRepository(
    private val database: AppDatabase,
    private val deviceIdentityManager: DeviceIdentityManager
) {
    private val logDao = database.systemLogDao()
    private val syncQueueDao = database.syncQueueDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var onEnqueueCallback: (() -> Unit)? = null

    fun setOnEnqueueCallback(callback: () -> Unit) {
        onEnqueueCallback = callback
    }

    fun getAllLogsFlow(): Flow<List<SystemLog>> {
        return logDao.getAllLogsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllLogs(): List<SystemLog> = withContext(Dispatchers.IO) {
        logDao.getAllLogs().map { it.toDomain() }
    }

    fun logAsync(
        action: String,
        entityType: String,
        result: LogResult,
        description: String,
        userId: String? = null,
        userName: String? = null,
        entityId: String? = null,
        triggerSync: Boolean = true
    ) {
        scope.launch {
            recordLog(
                action = action,
                entityType = entityType,
                result = result,
                description = description,
                userId = userId,
                userName = userName,
                entityId = entityId,
                triggerSync = triggerSync
            )
        }
    }

    suspend fun recordLog(
        action: String,
        entityType: String,
        result: LogResult,
        description: String,
        userId: String? = null,
        userName: String? = null,
        entityId: String? = null,
        triggerSync: Boolean = true
    ): SystemLog = withContext(Dispatchers.IO) {
        val logId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val deviceName = deviceIdentityManager.getDeviceDisplayName()
        val safeDescription = sanitizeSensitiveText(description)

        val entity = SystemLogEntity(
            id = logId,
            userId = userId,
            userName = userName,
            action = action,
            entityType = entityType,
            entityId = entityId,
            deviceName = deviceName,
            result = result.name,
            description = safeDescription,
            createdAt = now,
            syncedAt = null
        )
        logDao.insertLog(entity)

        val dto = SupabaseSystemLogDto(
            id = logId,
            userId = userId,
            userName = userName,
            action = action,
            entityType = entityType,
            entityId = entityId,
            deviceName = deviceName,
            result = result.name,
            description = safeDescription,
            createdAt = DateTimeUtils.toIsoUtc(now),
            syncedAt = null
        )
        val payloadJson = SupabaseClient.moshi
            .adapter(SupabaseSystemLogDto::class.java)
            .serializeNulls()
            .toJson(dto)

        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "SYSTEM_LOG",
                entityId = logId,
                operation = "INSERT",
                payloadJson = payloadJson,
                status = "PENDING",
                createdAt = now,
                updatedAt = now
            )
        )

        if (triggerSync) {
            onEnqueueCallback?.invoke()
        }

        entity.toDomain()
    }

    companion object {
        private val sensitivePatterns = listOf(
            Regex("""(?i)(password|senha|pin|token|apikey|authorization|hash|salt)\s*[:=]\s*\S+"""),
            Regex("""(?i)sb_publishable_\S+"""),
            Regex("""eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")
        )

        fun sanitizeSensitiveText(raw: String): String {
            var sanitized = raw
            for (pattern in sensitivePatterns) {
                sanitized = pattern.replace(sanitized, "[PROTEGIDO]")
            }
            return sanitized.take(500)
        }
    }
}

fun SystemLogEntity.toDomain() = SystemLog(
    id = id,
    userId = userId,
    userName = userName,
    action = action,
    entityType = entityType,
    entityId = entityId,
    deviceName = deviceName,
    result = if (result == LogResult.FAILURE.name) LogResult.FAILURE else LogResult.SUCCESS,
    description = description,
    createdAt = createdAt,
    syncedAt = syncedAt
)
