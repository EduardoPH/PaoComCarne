package com.example.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppReleaseEntity
import com.example.data.remote.SupabaseAppReleaseDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.SystemLogRepository
import com.example.domain.model.AppReleasePolicy
import com.example.domain.model.LogResult
import com.example.domain.model.SystemLogActions
import com.example.domain.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class UpdateStatus {
    CHECKING,
    UP_TO_DATE,
    OPTIONAL_AVAILABLE,
    MANDATORY_BLOCKED,
    DOWNLOADING,
    INSTALLING,
    NEEDS_INSTALL_PERMISSION,
    ERROR
}

data class UpdateUiState(
    val installedVersionCode: Int = 1,
    val installedVersionName: String = "1.0",
    val policy: AppReleasePolicy? = null,
    val status: UpdateStatus = UpdateStatus.UP_TO_DATE,
    val isBlocked: Boolean = false,
    val isOptionalBannerDismissed: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val downloadedApkPath: String? = null,
    val isOfflinePolicy: Boolean = false
)

class AppUpdateManager(
    private val context: Context,
    private val database: AppDatabase,
    private val systemLogRepository: SystemLogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val releaseDao = database.appReleaseDao()
    private val prefs = context.getSharedPreferences("pao_update_prefs", Context.MODE_PRIVATE)
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(loadInitialUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        activeManagerInstance = this
    }

    private fun getInstalledVersionInfo(): Pair<Int, String> {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            val name = info.versionName ?: "1.0"
            code to name
        } catch (_: Exception) {
            1 to "1.0"
        }
    }

    private fun loadInitialUiState(): UpdateUiState {
        val (installedCode, installedName) = getInstalledVersionInfo()
        val cachedMin = prefs.getInt(KEY_MIN_SUPPORTED_CODE, 0)
        val cachedLatest = prefs.getInt(KEY_VERSION_CODE, 0)
        val cachedMandatory = prefs.getBoolean(KEY_MANDATORY, false)

        return if (cachedLatest > 0 || cachedMin > 0) {
            val policy = AppReleasePolicy(
                id = prefs.getString(KEY_RELEASE_ID, "cached") ?: "cached",
                versionName = prefs.getString(KEY_VERSION_NAME, installedName) ?: installedName,
                versionCode = cachedLatest,
                minSupportedCode = cachedMin,
                mandatory = cachedMandatory,
                apkPath = prefs.getString(KEY_APK_PATH, "") ?: "",
                sha256 = prefs.getString(KEY_SHA256, "") ?: "",
                changelog = prefs.getString(KEY_CHANGELOG, "") ?: "",
                releaseReason = prefs.getString(KEY_REASON, "") ?: "",
                active = true,
                createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis()),
                confirmedAt = prefs.getLong(KEY_CONFIRMED_AT, System.currentTimeMillis())
            )
            val blocked = isVersionBlocked(installedCode, policy)
            val optional = !blocked && installedCode < policy.versionCode
            UpdateUiState(
                installedVersionCode = installedCode,
                installedVersionName = installedName,
                policy = policy,
                status = when {
                    blocked -> UpdateStatus.MANDATORY_BLOCKED
                    optional -> UpdateStatus.OPTIONAL_AVAILABLE
                    else -> UpdateStatus.UP_TO_DATE
                },
                isBlocked = blocked,
                isOfflinePolicy = true
            )
        } else {
            UpdateUiState(
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )
        }
    }

    fun checkVersionPolicy(isOnline: Boolean, userId: String? = null, userName: String? = null) {
        scope.launch {
            val (installedCode, installedName) = getInstalledVersionInfo()

            // First evaluate persisted Room/Prefs policy immediately
            val localEntity = releaseDao.getLatestCachedRelease()
            if (localEntity != null && _uiState.value.policy == null) {
                applyEvaluatedPolicy(
                    installedCode = installedCode,
                    installedName = installedName,
                    policy = localEntity.toDomain(),
                    isOffline = !isOnline,
                    logAction = false,
                    userId = userId,
                    userName = userName
                )
            }

            if (!isOnline) {
                val existingPolicy = _uiState.value.policy
                val blocked = existingPolicy != null && isVersionBlocked(installedCode, existingPolicy)
                _uiState.value = _uiState.value.copy(
                    installedVersionCode = installedCode,
                    installedVersionName = installedName,
                    isBlocked = blocked,
                    status = if (blocked) UpdateStatus.MANDATORY_BLOCKED else _uiState.value.status,
                    isOfflinePolicy = true
                )
                return@launch
            }

            try {
                val service = SupabaseClient.getService(context)
                val res = service.getActiveAppReleases()
                if (res.isSuccessful) {
                    val list = res.body().orEmpty()
                    val best = list.filter { it.active }.maxByOrNull { it.versionCode }
                        ?: list.maxByOrNull { it.versionCode }

                    if (best != null) {
                        val now = System.currentTimeMillis()
                        val entity = best.toEntity(now)
                        releaseDao.insertOrUpdateRelease(entity)
                        persistPrefsPolicy(entity)

                        val policy = entity.toDomain()
                        applyEvaluatedPolicy(
                            installedCode = installedCode,
                            installedName = installedName,
                            policy = policy,
                            isOffline = false,
                            logAction = true,
                            userId = userId,
                            userName = userName
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            installedVersionCode = installedCode,
                            installedVersionName = installedName,
                            isBlocked = false,
                            status = UpdateStatus.UP_TO_DATE,
                            isOfflinePolicy = false
                        )
                    }
                }
            } catch (_: Exception) {
                // Keep last confirmed cached policy when remote check fails
            }
        }
    }

    private suspend fun applyEvaluatedPolicy(
        installedCode: Int,
        installedName: String,
        policy: AppReleasePolicy,
        isOffline: Boolean,
        logAction: Boolean,
        userId: String?,
        userName: String?
    ) {
        val blocked = isVersionBlocked(installedCode, policy)
        val optional = !blocked && installedCode < policy.versionCode

        val previousStatus = _uiState.value.status
        val keepDownloading = previousStatus == UpdateStatus.DOWNLOADING || previousStatus == UpdateStatus.INSTALLING

        _uiState.value = _uiState.value.copy(
            installedVersionCode = installedCode,
            installedVersionName = installedName,
            policy = policy,
            isBlocked = blocked,
            status = when {
                keepDownloading -> previousStatus
                blocked -> UpdateStatus.MANDATORY_BLOCKED
                optional -> UpdateStatus.OPTIONAL_AVAILABLE
                else -> UpdateStatus.UP_TO_DATE
            },
            isOfflinePolicy = isOffline,
            errorMessage = if (!keepDownloading) null else _uiState.value.errorMessage
        )

        if (logAction) {
            val action = when {
                blocked -> SystemLogActions.MANDATORY_UPDATE_DETECTED
                optional -> SystemLogActions.UPDATE_AVAILABLE
                else -> SystemLogActions.VERSION_CHECK
            }
            val desc = when {
                blocked -> "Versão bloqueada ($installedName/$installedCode < mínimo ${policy.minSupportedCode} ou obrigatória ${policy.versionName}/${policy.versionCode})."
                optional -> "Nova versão opcional disponível: ${policy.versionName} (código ${policy.versionCode})."
                else -> "Versão verificada com sucesso: $installedName ($installedCode)."
            }
            systemLogRepository.recordLog(
                action = action,
                entityType = "APP_RELEASE",
                entityId = policy.id,
                result = LogResult.SUCCESS,
                description = desc,
                userId = userId,
                userName = userName
            )
        }
    }

    fun dismissOptionalBanner() {
        if (!_uiState.value.isBlocked) {
            _uiState.value = _uiState.value.copy(isOptionalBannerDismissed = true)
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun resumePendingInstallIfPermitted(userId: String? = null, userName: String? = null) {
        val state = _uiState.value
        val path = state.downloadedApkPath ?: return
        if (state.status == UpdateStatus.NEEDS_INSTALL_PERMISSION && canRequestPackageInstalls()) {
            val file = File(path)
            if (file.exists()) {
                scope.launch {
                    installApkWithPackageInstaller(file, userId, userName)
                }
            }
        }
    }

    fun startDownloadAndInstall(userId: String? = null, userName: String? = null) {
        val policy = _uiState.value.policy ?: return
        if (_uiState.value.status == UpdateStatus.DOWNLOADING) return

        scope.launch {
            _uiState.value = _uiState.value.copy(
                status = UpdateStatus.DOWNLOADING,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorMessage = null,
                statusMessage = "Baixando versão ${policy.versionName}..."
            )

            systemLogRepository.recordLog(
                action = SystemLogActions.UPDATE_DOWNLOAD_STARTED,
                entityType = "APP_RELEASE",
                entityId = policy.id,
                result = LogResult.SUCCESS,
                description = "Iniciado download do APK ${policy.versionName} (${policy.versionCode}).",
                userId = userId,
                userName = userName
            )

            try {
                val downloadUrl = buildApkDownloadUrl(
                    supabaseUrl = SupabaseConfig.getUrl(context),
                    apkPath = policy.apkPath
                )
                val anonKey = SupabaseConfig.getKey(context)
                val request = Request.Builder()
                    .url(downloadUrl)
                    .apply {
                        if (downloadUrl.startsWith(SupabaseConfig.getUrl(context))) {
                            addHeader("apikey", anonKey)
                            addHeader("Authorization", "Bearer $anonKey")
                        }
                    }
                    .build()

                val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
                val targetFile = File(updatesDir, "pao_com_carne_v${policy.versionCode}.apk")
                if (targetFile.exists()) targetFile.delete()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Falha no download (HTTP ${response.code}).")
                    }
                    val body = response.body ?: throw IllegalStateException("Resposta de download vazia.")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastEmit = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastEmit > 120L || (contentLength > 0 && totalRead >= contentLength)) {
                                    lastEmit = now
                                    val progress = if (contentLength > 0) {
                                        (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    _uiState.value = _uiState.value.copy(
                                        downloadedBytes = totalRead,
                                        totalBytes = contentLength.coerceAtLeast(0L),
                                        downloadProgress = progress
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                }

                // Verify SHA-256 integrity
                if (policy.sha256.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Verificando integridade SHA-256..."
                    )
                    val actualHash = computeFileSha256(targetFile)
                    if (!verifySha256(actualHash, policy.sha256)) {
                        targetFile.delete()
                        throw IllegalStateException("Falha na verificação de integridade (SHA-256 divergente).")
                    }
                }

                systemLogRepository.recordLog(
                    action = SystemLogActions.UPDATE_DOWNLOAD_COMPLETED,
                    entityType = "APP_RELEASE",
                    entityId = policy.id,
                    result = LogResult.SUCCESS,
                    description = "APK ${policy.versionName} baixado e validado com sucesso.",
                    userId = userId,
                    userName = userName
                )

                if (!canRequestPackageInstalls()) {
                    _uiState.value = _uiState.value.copy(
                        status = UpdateStatus.NEEDS_INSTALL_PERMISSION,
                        downloadedApkPath = targetFile.absolutePath,
                        downloadProgress = 1f,
                        statusMessage = "Conceda permissão para instalar atualizações do aplicativo."
                    )
                    return@launch
                }

                installApkWithPackageInstaller(targetFile, userId, userName)
            } catch (e: Exception) {
                val msg = e.message ?: "Erro desconhecido ao baixar atualização."
                systemLogRepository.recordLog(
                    action = SystemLogActions.UPDATE_DOWNLOAD_FAILED,
                    entityType = "APP_RELEASE",
                    entityId = policy.id,
                    result = LogResult.FAILURE,
                    description = "Falha no download/validação da atualização: $msg",
                    userId = userId,
                    userName = userName
                )
                _uiState.value = _uiState.value.copy(
                    status = if (_uiState.value.isBlocked) UpdateStatus.ERROR else UpdateStatus.ERROR,
                    errorMessage = msg,
                    statusMessage = null
                )
            }
        }
    }

    private suspend fun installApkWithPackageInstaller(
        apkFile: File,
        userId: String?,
        userName: String?
    ) = withContext(Dispatchers.IO) {
        val policy = _uiState.value.policy
        _uiState.value = _uiState.value.copy(
            status = UpdateStatus.INSTALLING,
            downloadedApkPath = apkFile.absolutePath,
            downloadProgress = 1f,
            statusMessage = "Preparando instalação pelo sistema Android..."
        )

        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }

            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
            session.close()

            systemLogRepository.recordLog(
                action = SystemLogActions.UPDATE_INSTALL_REQUESTED,
                entityType = "APP_RELEASE",
                entityId = policy?.id,
                result = LogResult.SUCCESS,
                description = "Instalação da versão ${policy?.versionName ?: ""} solicitada via PackageInstaller.",
                userId = userId,
                userName = userName
            )
        } catch (e: Exception) {
            val msg = e.message ?: "Não foi possível iniciar o instalador do Android."
            _uiState.value = _uiState.value.copy(
                status = UpdateStatus.ERROR,
                errorMessage = msg,
                statusMessage = null
            )
        }
    }

    internal fun onPackageInstallerResult(status: Int, message: String?) {
        val blocked = _uiState.value.isBlocked
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                _uiState.value = _uiState.value.copy(
                    status = UpdateStatus.UP_TO_DATE,
                    statusMessage = "Atualização concluída com sucesso!",
                    errorMessage = null
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    status = if (blocked) UpdateStatus.MANDATORY_BLOCKED else UpdateStatus.OPTIONAL_AVAILABLE,
                    errorMessage = message?.takeIf { it.isNotBlank() }
                        ?: "Instalação cancelada ou não concluída. Tente novamente.",
                    statusMessage = null
                )
            }
        }
    }

    private fun persistPrefsPolicy(entity: AppReleaseEntity) {
        prefs.edit()
            .putString(KEY_RELEASE_ID, entity.id)
            .putString(KEY_VERSION_NAME, entity.versionName)
            .putInt(KEY_VERSION_CODE, entity.versionCode)
            .putInt(KEY_MIN_SUPPORTED_CODE, entity.minSupportedCode)
            .putBoolean(KEY_MANDATORY, entity.mandatory)
            .putString(KEY_APK_PATH, entity.apkPath)
            .putString(KEY_SHA256, entity.sha256)
            .putString(KEY_CHANGELOG, entity.changelog)
            .putString(KEY_REASON, entity.releaseReason)
            .putLong(KEY_CREATED_AT, entity.createdAt)
            .putLong(KEY_CONFIRMED_AT, entity.confirmedAt)
            .apply()
    }

    companion object {
        private const val KEY_RELEASE_ID = "release_id"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_MIN_SUPPORTED_CODE = "min_supported_code"
        private const val KEY_MANDATORY = "mandatory"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_CHANGELOG = "changelog"
        private const val KEY_REASON = "release_reason"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_CONFIRMED_AT = "confirmed_at"

        @Volatile
        var activeManagerInstance: AppUpdateManager? = null

        fun isVersionBlocked(installedCode: Int, policy: AppReleasePolicy): Boolean {
            if (!policy.active) return false
            if (installedCode < policy.minSupportedCode) return true
            if (policy.mandatory && installedCode < policy.versionCode) return true
            return false
        }

        fun buildApkDownloadUrl(supabaseUrl: String, apkPath: String): String {
            val trimmed = apkPath.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed
            }
            val cleanBase = supabaseUrl.trimEnd('/')
            val normalizedPath = trimmed.trimStart('/')
            val withBucket = if (normalizedPath.contains("/")) {
                normalizedPath
            } else {
                "app-releases/$normalizedPath"
            }
            return "$cleanBase/storage/v1/object/public/$withBucket"
        }

        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun verifySha256(actualHex: String, expectedHex: String): Boolean {
            val cleanExpected = expectedHex.trim().lowercase()
            if (cleanExpected.isBlank()) return true
            return actualHex.trim().lowercase() == cleanExpected
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppUpdateManager.activeManagerInstance?.onPackageInstallerResult(status, message)
            }
        }
    }
}

private fun SupabaseAppReleaseDto.toEntity(confirmedAt: Long) = AppReleaseEntity(
    id = id,
    versionName = versionName,
    versionCode = versionCode,
    minSupportedCode = minSupportedCode,
    mandatory = mandatory,
    apkPath = apkPath,
    sha256 = sha256,
    changelog = changelog,
    releaseReason = releaseReason.orEmpty(),
    active = active,
    createdAt = createdAt?.let { DateTimeUtils.fromIsoUtc(it) } ?: confirmedAt,
    confirmedAt = confirmedAt
)

private fun AppReleaseEntity.toDomain() = AppReleasePolicy(
    id = id,
    versionName = versionName,
    versionCode = versionCode,
    minSupportedCode = minSupportedCode,
    mandatory = mandatory,
    apkPath = apkPath,
    sha256 = sha256,
    changelog = changelog,
    releaseReason = releaseReason,
    active = active,
    createdAt = createdAt,
    confirmedAt = confirmedAt
)
