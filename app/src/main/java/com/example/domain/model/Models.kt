package com.example.domain.model

enum class ShiftPeriod(val displayName: String) {
    MANHA("Manhã"),
    NOITE("Noite")
}

enum class ShiftStatus(val displayName: String) {
    OPEN("Aberto"),
    CLOSED("Encerrado")
}

enum class AttendanceType(val displayName: String, val fixedMembers: Int?) {
    SOLO("Solo", 1),
    DUPLA("Dupla", 2),
    GRUPO("Grupo", null)
}

enum class ClientClassification(val displayName: String) {
    NOVO("Novo"),
    RECORRENTE("Recorrente")
}

object ClientOrigins {
    val ALL = listOf(
        "Instagram",
        "TikTok",
        "Indicação",
        "iFood",
        "Google",
        "Já foi na antiga loja",
        "Gringo"
    )
}

enum class SyncState(val label: String) {
    SINCRONIZADO("Conectado e sincronizado"),
    OFFLINE("Offline"),
    PENDENTE("Sincronização pendente"),
    SINCRONIZANDO("Sincronizando..."),
    ERRO("Erro de sincronização")
}

data class Hoster(
    val id: String,
    val name: String,
    val authUserId: String? = null,
    val pinHash: String = "",
    val pinSalt: String = "",
    val isActivated: Boolean = false,
    val active: Boolean = true,
    val isAdmin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Shift(
    val id: String,
    val hosterId: String,
    val hosterName: String,
    val period: ShiftPeriod,
    val openedAt: Long,
    val closedAt: Long? = null,
    val status: ShiftStatus = ShiftStatus.OPEN,
    val deviceCreatedAt: Long = openedAt,
    val syncedAt: Long? = null,
    val syncStatus: String = "SINCRONIZADO"
)

data class Attendance(
    val id: String,
    val shiftId: String,
    val hosterId: String,
    val hosterName: String,
    val type: AttendanceType,
    val totalMembers: Int,
    val newMembers: Int,
    val returningMembers: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isEdited: Boolean = false,
    val isPreferential: Boolean = false,
    val syncStatus: String = "PENDENTE",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val deletedBy: String? = null,
    val deletionReason: String? = null
)

data class AttendanceMember(
    val id: String,
    val attendanceId: String,
    val memberNumber: Int,
    val classification: ClientClassification,
    val origin: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AttendanceWithMembers(
    val attendance: Attendance,
    val members: List<AttendanceMember>
)

data class AttendanceAudit(
    val id: String,
    val attendanceId: String,
    val hosterId: String,
    val hosterName: String,
    val previousData: String,
    val newData: String,
    val changedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDENTE"
)

data class QueueEvent(
    val id: String,
    val shiftId: String,
    val hosterId: String,
    val waitingDuos: Int,
    val waitingGroups: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDENTE"
)

enum class LogResult(val label: String) {
    SUCCESS("Sucesso"),
    FAILURE("Falha")
}

object SystemLogActions {
    const val LOGIN_SUCCESS = "LOGIN_SUCESSO"
    const val LOGIN_FAILURE = "LOGIN_FALHA"
    const val LOGOUT = "LOGOUT"
    const val FIRST_ACCESS_ACTIVATION = "ATIVACAO_PRIMEIRO_ACESSO"
    const val PROFILE_NAME_UPDATED = "PERFIL_NOME_ALTERADO"
    const val PROFILE_PASSWORD_UPDATED = "PERFIL_SENHA_ALTERADA"

    const val SHIFT_OPEN = "TURNO_ABERTO"
    const val SHIFT_CLOSE = "TURNO_ENCERRADO"
    const val SHIFT_INVALID_OPEN = "TURNO_ABERTURA_INVALIDA"
    const val SHIFT_RESUME = "TURNO_RETOMADO"

    const val ATTENDANCE_CREATE = "ATENDIMENTO_REGISTRADO"
    const val ATTENDANCE_EDIT = "ATENDIMENTO_EDITADO"
    const val ATTENDANCE_DELETE = "ATENDIMENTO_EXCLUIDO"
    const val ATTENDANCE_SAVE_FAILURE = "ATENDIMENTO_FALHA_SALVAR"
    const val ATTENDANCE_DELETE_FAILURE = "ATENDIMENTO_FALHA_EXCLUIR"

    const val QUEUE_UPDATE = "FILA_ATUALIZADA"
    const val QUEUE_UPDATE_FAILURE = "FILA_FALHA_ATUALIZAR"

    const val ADMIN_ACCESS = "ADMIN_ACESSO"
    const val ADMIN_FILTER_APPLIED = "ADMIN_FILTRO_APLICADO"
    const val REPORT_EXPORTED = "RELATORIO_EXPORTADO"
    const val REPORT_EXPORT_FAILED = "RELATORIO_FALHA_EXPORTAR"

    const val SYNC_STARTED = "SYNC_INICIADA"
    const val SYNC_FINISHED = "SYNC_CONCLUIDA"
    const val SYNC_FAILED = "SYNC_FALHOU"
    const val RECONNECT = "RECONEXAO_REDE"
    const val REMOTE_UPDATE_RECEIVED = "ATUALIZACAO_REMOTA_RECEBIDA"
    const val CONFLICT_DETECTED = "CONFLITO_SINCRONIZACAO"

    const val VERSION_CHECK = "VERSAO_VERIFICADA"
    const val UPDATE_AVAILABLE = "ATUALIZACAO_DISPONIVEL"
    const val MANDATORY_UPDATE_DETECTED = "ATUALIZACAO_OBRIGATORIA_DETECTADA"
    const val UPDATE_DOWNLOAD_STARTED = "ATUALIZACAO_DOWNLOAD_INICIADO"
    const val UPDATE_DOWNLOAD_COMPLETED = "ATUALIZACAO_DOWNLOAD_CONCLUIDO"
    const val UPDATE_DOWNLOAD_FAILED = "ATUALIZACAO_DOWNLOAD_FALHOU"
    const val UPDATE_INSTALL_REQUESTED = "ATUALIZACAO_INSTALACAO_SOLICITADA"
}

data class SystemLog(
    val id: String,
    val userId: String?,
    val userName: String?,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val deviceName: String,
    val result: LogResult,
    val description: String,
    val createdAt: Long,
    val syncedAt: Long?
)

data class AppReleasePolicy(
    val id: String,
    val versionName: String,
    val versionCode: Int,
    val minSupportedCode: Int,
    val mandatory: Boolean,
    val apkPath: String,
    val sha256: String,
    val changelog: String,
    val releaseReason: String,
    val active: Boolean,
    val createdAt: Long,
    val confirmedAt: Long
)
