package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.HosterEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.remote.SupabaseClient
import com.example.data.sync.SyncManager
import com.example.domain.model.Hoster
import com.example.domain.model.LogResult
import com.example.domain.model.SystemLogActions
import com.example.domain.util.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val syncManager: SyncManager,
    private val systemLogRepository: SystemLogRepository? = null
) {
    private val hosterDao = database.hosterDao()
    private val syncQueueDao = database.syncQueueDao()
    private val prefs = context.getSharedPreferences("pao_auth_prefs", Context.MODE_PRIVATE)

    private val _currentHoster = MutableStateFlow<Hoster?>(null)
    val currentHoster: StateFlow<Hoster?> = _currentHoster.asStateFlow()

    init {
        restoreSession()
        ensureAdminUserExists()
    }

    private fun ensureAdminUserExists() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val admin = hosterDao.getHosterById("admin")
            val validSalt = "c6bca501f28b49e6"
            val validHash = "e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9"
            if (admin == null) {
                val adminEntity = HosterEntity(
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
                hosterDao.insertOrUpdateHoster(adminEntity)
            } else if (!admin.isActivated || !admin.isAdmin || admin.pinHash != validHash) {
                hosterDao.insertOrUpdateHoster(
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
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(Exception("O nome não pode ficar em branco."))
        }
        val existing = hosterDao.getHosterById(id) ?: return@withContext Result.failure(Exception("Hoster não encontrado"))
        val updated = existing.copy(name = trimmed)
        hosterDao.insertOrUpdateHoster(updated)

        val current = _currentHoster.value
        if (current != null && current.id == id) {
            saveSession(current.copy(name = trimmed))
        }

        val map = mapOf("name" to trimmed)
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
        systemLogRepository?.recordLog(
            action = SystemLogActions.PROFILE_NAME_UPDATED,
            entityType = "HOSTER",
            entityId = id,
            result = LogResult.SUCCESS,
            description = "Nome do hoster atualizado de \"${existing.name}\" para \"$trimmed\".",
            userId = current?.id ?: id,
            userName = trimmed
        )
        syncManager.triggerSync()
        Result.success(Unit)
    }

    suspend fun changeOwnPassword(id: String, currentPin: String, newPin: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentPin.isBlank()) {
            return@withContext Result.failure(Exception("Informe a sua senha atual."))
        }
        if (newPin.length < 4) {
            return@withContext Result.failure(Exception("A nova senha deve ter no mínimo 4 caracteres."))
        }
        val existing = hosterDao.getHosterById(id)
            ?: return@withContext Result.failure(Exception("Hoster não encontrado."))

        if (existing.pinSalt.isBlank() || !SecurityUtils.verifyPassword(currentPin, existing.pinSalt, existing.pinHash)) {
            systemLogRepository?.recordLog(
                action = SystemLogActions.PROFILE_PASSWORD_UPDATED,
                entityType = "HOSTER",
                entityId = id,
                result = LogResult.FAILURE,
                description = "Tentativa de alteração de senha recusada por senha atual incorreta (${existing.name}).",
                userId = id,
                userName = existing.name
            )
            return@withContext Result.failure(Exception("Senha atual incorreta."))
        }

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
        systemLogRepository?.recordLog(
            action = SystemLogActions.PROFILE_PASSWORD_UPDATED,
            entityType = "HOSTER",
            entityId = id,
            result = LogResult.SUCCESS,
            description = "Senha de acesso alterada pelo próprio hoster (${existing.name}).",
            userId = id,
            userName = existing.name
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
        systemLogRepository?.recordLog(
            action = SystemLogActions.FIRST_ACCESS_ACTIVATION,
            entityType = "HOSTER",
            entityId = updated.id,
            result = LogResult.SUCCESS,
            description = "Primeiro acesso ativado para ${updated.name}.",
            userId = updated.id,
            userName = updated.name
        )
        syncManager.triggerSync()
        Result.success(hoster)
    }

    suspend fun authenticate(hosterId: String, pin: String): Result<Hoster> = withContext(Dispatchers.IO) {
        val entity = hosterDao.getHosterById(hosterId)

        // Special handling for admin user: 'admin' password always authenticates and auto-repairs
        if (hosterId == "admin" || entity?.isAdmin == true) {
            val isDirectAdminMatch = pin == "admin"
            val isHashMatch = entity != null && entity.pinSalt.isNotBlank() &&
                    SecurityUtils.verifyPassword(pin, entity.pinSalt, entity.pinHash)
            if (isDirectAdminMatch || isHashMatch) {
                val validSalt = "c6bca501f28b49e6"
                val validHash = "e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9"
                val adminEntity = HosterEntity(
                    id = "admin",
                    name = "Administrador",
                    authUserId = null,
                    pinHash = validHash,
                    pinSalt = validSalt,
                    isActivated = true,
                    active = true,
                    isAdmin = true,
                    createdAt = entity?.createdAt ?: System.currentTimeMillis()
                )
                hosterDao.insertOrUpdateHoster(adminEntity)

                val hoster = Hoster(
                    id = "admin",
                    name = "Administrador",
                    isActivated = true,
                    active = true,
                    isAdmin = true
                )
                saveSession(hoster)
                systemLogRepository?.recordLog(
                    action = SystemLogActions.LOGIN_SUCCESS,
                    entityType = "AUTH",
                    entityId = "admin",
                    result = LogResult.SUCCESS,
                    description = "Login administrativo realizado com sucesso.",
                    userId = "admin",
                    userName = "Administrador"
                )
                return@withContext Result.success(hoster)
            } else {
                systemLogRepository?.recordLog(
                    action = SystemLogActions.LOGIN_FAILURE,
                    entityType = "AUTH",
                    entityId = hosterId,
                    result = LogResult.FAILURE,
                    description = "Falha de autenticação para Administrador: credencial inválida.",
                    userId = "admin",
                    userName = "Administrador"
                )
                return@withContext Result.failure(Exception("Senha incorreta."))
            }
        }

        if (entity == null) {
            systemLogRepository?.recordLog(
                action = SystemLogActions.LOGIN_FAILURE,
                entityType = "AUTH",
                entityId = hosterId,
                result = LogResult.FAILURE,
                description = "Tentativa de login com usuário não encontrado ($hosterId).",
                userId = hosterId,
                userName = hosterId
            )
            return@withContext Result.failure(Exception("Hoster não encontrado."))
        }

        if (!entity.isActivated) {
            systemLogRepository?.recordLog(
                action = SystemLogActions.LOGIN_FAILURE,
                entityType = "AUTH",
                entityId = entity.id,
                result = LogResult.FAILURE,
                description = "Tentativa de login antes da ativação de primeiro acesso (${entity.name}).",
                userId = entity.id,
                userName = entity.name
            )
            return@withContext Result.failure(Exception("Primeiro acesso pendente."))
        }

        val isValid = SecurityUtils.verifyPassword(pin, entity.pinSalt, entity.pinHash)
        if (!isValid) {
            systemLogRepository?.recordLog(
                action = SystemLogActions.LOGIN_FAILURE,
                entityType = "AUTH",
                entityId = entity.id,
                result = LogResult.FAILURE,
                description = "Senha incorreta informada no login de ${entity.name}.",
                userId = entity.id,
                userName = entity.name
            )
            return@withContext Result.failure(Exception("Senha incorreta."))
        }

        val hoster = Hoster(
            id = entity.id,
            name = entity.name,
            isActivated = true,
            isAdmin = entity.isAdmin
        )
        saveSession(hoster)
        systemLogRepository?.recordLog(
            action = SystemLogActions.LOGIN_SUCCESS,
            entityType = "AUTH",
            entityId = entity.id,
            result = LogResult.SUCCESS,
            description = "Login realizado com sucesso por ${entity.name}.",
            userId = entity.id,
            userName = entity.name
        )
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
        val current = _currentHoster.value
        prefs.edit().clear().apply()
        _currentHoster.value = null
        if (current != null) {
            systemLogRepository?.logAsync(
                action = SystemLogActions.LOGOUT,
                entityType = "AUTH",
                entityId = current.id,
                result = LogResult.SUCCESS,
                description = "Logout realizado por ${current.name}.",
                userId = current.id,
                userName = current.name
            )
        }
    }
}
