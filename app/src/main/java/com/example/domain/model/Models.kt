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
        "Já foi na antiga loja"
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
    val syncStatus: String = "PENDENTE"
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
