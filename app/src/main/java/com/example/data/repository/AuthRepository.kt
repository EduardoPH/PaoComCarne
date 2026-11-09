package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.remote.SupabaseClient
import com.example.data.sync.SyncManager
import com.example.domain.model.Hoster
import com.example.domain.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val syncManager: SyncManager
) {
    private val hosterDao = database.hosterDao()
    private val syncQueueDao = database.syncQueueDao()
    private val prefs = context.getSharedPreferences("pao_auth_prefs", Context.MODE_PRIVATE)

    private val _currentHoster = MutableStateFlow<Hoster?>(null)
    val currentHoster: StateFlow<Hoster?> = _currentHoster.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedId = prefs.getString("current_hoster_id", null)
        val savedName = prefs.getString("current_hoster_name", null)
        if (savedId != null && savedName != null) {
            val savedIsAdmin = prefs.getBoolean("current_hoster_is_admin", false)
            _currentHoster.value = Hoster(
                id = savedId,
                name = savedName,
                isActivated = true,
                isAdmin = savedIsAdmin
            )
        }
    }

    fun getActiveHostersFlow(): Flow<List<Hoster>> {
        return hosterDao.getAllActiveHostersFlow().map { entities ->
            entities.map { entity ->
                Hoster(
                    id = entity.id,
                    name = entity.name,
                    authUserId = entity.authUserId,
                    pinHash = entity.pinHash,
                    pinSalt = entity.pinSalt,
                    isActivated = entity.isActivated,
                    active = entity.active,
                    isAdmin = entity.isAdmin,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    suspend fun createHoster(name: String, pin: String? = null): Result<Hoster> = withContext(Dispatchers.IO) {
        val id = "h-" + java.util.UUID.randomUUID().toString().take(8)
        val salt: String
        val hash: String
        val isActivated: Boolean

        if (!pin.isNullOrBlank()) {
            salt = SecurityUtils.generateSalt()
            hash = SecurityUtils.hashPassword(pin, salt)
            isActivated = true
        } else {
            salt = ""
            hash = ""
            isActivated = false
        }

        val entity = HosterEntity(
            id = id,
            name = name.trim(),
            authUserId = null,
            pinHash = hash,
            pinSalt = salt,
            isActivated = isActivated,
            active = true,
            isAdmin = false,
            createdAt = System.currentTimeMillis()
        )
        hosterDao.insertOrUpdateHoster(entity)

        // Queue to Supabase
        val dto = com.example.data.remote.SupabaseHosterDto(
            id = entity.id,
            name = entity.name,
            authUserId = null,
            pinHash = if (hash.isNotBlank()) hash else null,
            pinSalt = if (salt.isNotBlank()) salt else null,
            isActivated = isActivated,
            active = true,
            isAdmin = false
        )
        val json = SupabaseClient.moshi.adapter(com.example.data.remote.SupabaseHosterDto::class.java).toJson(dto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "HOSTER_INSERT",
                entityId = id,
                operation = "INSERT",
                payloadJson = json,
                status = "PENDING"
            )
        )
        syncManager.triggerSync()

        Result.success(
            Hoster(
                id = entity.id,
                name = entity.name,
                isActivated = isActivated,
                active = true,
                isAdmin = false
            )
        )
    }

    suspend fun updateHosterName(id: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = hosterDao.getHosterById(id) ?: return@withContext Result.failure(Exception("Hoster não encontrado"))
        val updated = existing.copy(name = newName.trim())
        hosterDao.insertOrUpdateHoster(updated)

        val map = mapOf("name" to newName.trim())
        val json = SupabaseClient.moshi.adapter(Map::class.java).toJson(map)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "HOSTER_UPDATE",
                entityId = id,
                operation = "UPDATE",
                payloadJson = json,
                status = "PENDING"
            )
        )
        syncManager.triggerSync()
        Result.success(Unit)
    }

    suspend fun updateHosterPin(id: String, newPin: String): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = hosterDao.getHosterById(id) ?: return@withContext Result.failure(Exception("Hoster não encontrado"))
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(newPin, salt)
        val updated = existing.copy(
            pinSalt = salt,
            pinHash = hash,
            isActivated = true
        )
        hosterDao.insertOrUpdateHoster(updated)

        val map = mapOf(
            "pin_salt" to salt,
            "pin_hash" to hash,
            "is_activated" to true
        )
        val json = SupabaseClient.moshi.adapter(Map::class.java).toJson(map)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "HOSTER_UPDATE",
                entityId = id,
                operation = "UPDATE",
                payloadJson = json,
                status = "PENDING"
            )
        )
        syncManager.triggerSync()
        Result.success(Unit)
    }

    suspend fun deleteHoster(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (id == "admin") {
            return@withContext Result.failure(Exception("Não é permitido excluir o usuário Administrador."))
        }
        hosterDao.deleteHosterById(id)

        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "HOSTER_DELETE",
                entityId = id,
                operation = "DELETE",
                payloadJson = "{}",
                status = "PENDING"
            )
        )
        syncManager.triggerSync()
        Result.success(Unit)
    }

    suspend fun getHoster(id: String): Hoster? {
        val entity = hosterDao.getHosterById(id) ?: return null
        return Hoster(
            id = entity.id,
            name = entity.name,
            authUserId = entity.authUserId,
            pinHash = entity.pinHash,
            pinSalt = entity.pinSalt,
            isActivated = entity.isActivated,
            active = entity.active,
            createdAt = entity.createdAt
        )
    }

    suspend fun activateHoster(hosterId: String, pin: String): Result<Hoster> = withContext(Dispatchers.IO) {
        val entity = hosterDao.getHosterById(hosterId)
            ?: return@withContext Result.failure(Exception("Hoster não encontrado no banco."))

        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(pin, salt)

        val updated = entity.copy(
            pinSalt = salt,
            pinHash = hash,
            isActivated = true
        )
        hosterDao.insertOrUpdateHoster(updated)

        // Queue activation update to Supabase
        val map = mapOf(
            "pin_salt" to salt,
            "pin_hash" to hash,
            "is_activated" to true
        )
        val json = SupabaseClient.moshi.adapter(Map::class.java).toJson(map)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "HOSTER_ACTIVATION",
                entityId = hosterId,
                operation = "UPDATE",
                payloadJson = json,
                status = "PENDING"
            )
        )

        val hoster = Hoster(
            id = updated.id,
            name = updated.name,
            isActivated = true
        )
        saveSession(hoster)
        syncManager.triggerSync()
        Result.success(hoster)
    }

    suspend fun authenticate(hosterId: String, pin: String): Result<Hoster> = withContext(Dispatchers.IO) {
        val entity = hosterDao.getHosterById(hosterId)
            ?: return@withContext Result.failure(Exception("Hoster não encontrado."))

        if (!entity.isActivated) {
            return@withContext Result.failure(Exception("Primeiro acesso pendente."))
        }

        val isValid = SecurityUtils.verifyPassword(pin, entity.pinSalt, entity.pinHash)
        if (!isValid) {
            return@withContext Result.failure(Exception("Senha incorreta."))
        }

        val hoster = Hoster(
            id = entity.id,
            name = entity.name,
            isActivated = true,
            isAdmin = entity.isAdmin
        )
        saveSession(hoster)
        Result.success(hoster)
    }

    private fun saveSession(hoster: Hoster) {
        prefs.edit()
            .putString("current_hoster_id", hoster.id)
            .putString("current_hoster_name", hoster.name)
            .putBoolean("current_hoster_is_admin", hoster.isAdmin)
            .apply()
        _currentHoster.value = hoster
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentHoster.value = null
    }
}
