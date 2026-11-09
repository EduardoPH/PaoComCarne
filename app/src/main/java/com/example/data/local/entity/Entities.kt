package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hosters",
    indices = [Index(value = ["name"], unique = false)]
)
data class HosterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val authUserId: String?,
    val pinHash: String,
    val pinSalt: String,
    val isActivated: Boolean,
    val active: Boolean,
    val isAdmin: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "shifts",
    indices = [
        Index(value = ["hosterId"]),
        Index(value = ["status"])
    ]
)
data class ShiftEntity(
    @PrimaryKey val id: String,
    val hosterId: String,
    val hosterName: String,
    val period: String, // MANHA or NOITE
    val openedAt: Long,
    val closedAt: Long?,
    val status: String, // OPEN or CLOSED
    val deviceCreatedAt: Long,
    val syncedAt: Long?,
    val syncStatus: String // PENDENTE, SINCRONIZANDO, SINCRONIZADO, ERRO
)

@Entity(
    tableName = "attendances",
    indices = [
        Index(value = ["shiftId"]),
        Index(value = ["hosterId"]),
        Index(value = ["createdAt"])
    ]
)
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val hosterId: String,
    val hosterName: String,
    val type: String, // SOLO, DUPLA, GRUPO
    val totalMembers: Int,
    val newMembers: Int,
    val returningMembers: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isEdited: Boolean,
    val isPreferential: Boolean = false,
    val syncStatus: String
)

@Entity(
    tableName = "attendance_members",
    indices = [
        Index(value = ["attendanceId"]),
        Index(value = ["classification"])
    ]
)
data class AttendanceMemberEntity(
    @PrimaryKey val id: String,
    val attendanceId: String,
    val memberNumber: Int,
    val classification: String, // NOVO, RECORRENTE
    val origin: String?,
    val createdAt: Long
)

@Entity(
    tableName = "attendance_audit",
    indices = [Index(value = ["attendanceId"])]
)
data class AttendanceAuditEntity(
    @PrimaryKey val id: String,
    val attendanceId: String,
    val hosterId: String,
    val hosterName: String,
    val previousData: String,
    val newData: String,
    val changedAt: Long,
    val syncStatus: String
)

@Entity(
    tableName = "queue_events",
    indices = [Index(value = ["shiftId"]), Index(value = ["createdAt"])]
)
data class QueueEventEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val hosterId: String,
    val waitingDuos: Int,
    val waitingGroups: Int,
    val createdAt: Long,
    val syncStatus: String
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String, // SHIFT, ATTENDANCE, MEMBER, AUDIT, QUEUE_EVENT, HOSTER_SETUP
    val entityId: String,
    val operation: String, // INSERT, UPDATE
    val payloadJson: String,
    val status: String, // PENDING, SYNCING, FAILED, COMPLETED
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
