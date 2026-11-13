package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseHosterDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "auth_user_id") val authUserId: String? = null,
    @Json(name = "pin_hash") val pinHash: String? = null,
    @Json(name = "pin_salt") val pinSalt: String? = null,
    @Json(name = "is_activated") val isActivated: Boolean = false,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "is_admin") val isAdmin: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseShiftDto(
    @Json(name = "id") val id: String,
    @Json(name = "hoster_id") val hosterId: String,
    @Json(name = "hoster_name") val hosterName: String,
    @Json(name = "period") val period: String,
    @Json(name = "opened_at") val openedAt: String,
    @Json(name = "closed_at") val closedAt: String? = null,
    @Json(name = "status") val status: String,
    @Json(name = "device_created_at") val deviceCreatedAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAttendanceDto(
    @Json(name = "id") val id: String,
    @Json(name = "shift_id") val shiftId: String,
    @Json(name = "hoster_id") val hosterId: String,
    @Json(name = "hoster_name") val hosterName: String,
    @Json(name = "type") val type: String,
    @Json(name = "total_members") val totalMembers: Int,
    @Json(name = "new_members") val newMembers: Int,
    @Json(name = "returning_members") val returningMembers: Int,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "is_preferential") val isPreferential: Boolean = false,
    @Json(name = "is_deleted") val isDeleted: Boolean = false,
    @Json(name = "deleted_at") val deletedAt: String? = null,
    @Json(name = "deleted_by") val deletedBy: String? = null,
    @Json(name = "deletion_reason") val deletionReason: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseMemberDto(
    @Json(name = "id") val id: String,
    @Json(name = "attendance_id") val attendanceId: String,
    @Json(name = "member_number") val memberNumber: Int,
    @Json(name = "classification") val classification: String,
    @Json(name = "origin") val origin: String? = null,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuditDto(
    @Json(name = "id") val id: String,
    @Json(name = "attendance_id") val attendanceId: String,
    @Json(name = "hoster_id") val hosterId: String,
    @Json(name = "hoster_name") val hosterName: String,
    @Json(name = "previous_data") val previousData: String,
    @Json(name = "new_data") val newData: String,
    @Json(name = "changed_at") val changedAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseQueueDto(
    @Json(name = "id") val id: String,
    @Json(name = "shift_id") val shiftId: String,
    @Json(name = "hoster_id") val hosterId: String,
    @Json(name = "waiting_duos") val waitingDuos: Int,
    @Json(name = "waiting_groups") val waitingGroups: Int,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAppReleaseDto(
    @Json(name = "id") val id: String,
    @Json(name = "version_name") val versionName: String,
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "min_supported_code") val minSupportedCode: Int,
    @Json(name = "mandatory") val mandatory: Boolean = false,
    @Json(name = "apk_path") val apkPath: String,
    @Json(name = "sha256") val sha256: String = "",
    @Json(name = "changelog") val changelog: String = "",
    @Json(name = "release_reason") val releaseReason: String? = null,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSystemLogDto(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "action") val action: String,
    @Json(name = "entity_type") val entityType: String,
    @Json(name = "entity_id") val entityId: String? = null,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "result") val result: String,
    @Json(name = "description") val description: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "synced_at") val syncedAt: String? = null
)
