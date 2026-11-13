package com.example

import com.example.data.local.AppDatabase
import com.example.data.remote.SupabaseAppReleaseDto
import com.example.data.remote.SupabaseAttendanceDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseSystemLogDto
import com.example.data.repository.SystemLogRepository
import com.example.data.update.AppUpdateManager
import com.example.domain.model.AppReleasePolicy
import com.example.domain.model.Attendance
import com.example.domain.model.AttendanceMember
import com.example.domain.model.AttendanceType
import com.example.domain.model.ClientClassification
import com.example.domain.model.Shift
import com.example.domain.model.ShiftPeriod
import com.example.domain.model.ShiftStatus
import com.example.presentation.admin.AdminDateCalculator
import com.example.presentation.admin.AdminFilterState
import com.example.presentation.admin.DateFilterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class EvolutionFeaturesTest {

    @Test
    fun testVersionBlockingRules() {
        val basePolicy = AppReleasePolicy(
            id = "rel-2",
            versionName = "2.0",
            versionCode = 2,
            minSupportedCode = 2,
            mandatory = true,
            apkPath = "v2.apk",
            sha256 = "abc123",
            changelog = "Melhorias",
            releaseReason = "Atualização estrutural",
            active = true,
            createdAt = 1000L,
            confirmedAt = 1000L
        )

        assertTrue(AppUpdateManager.isVersionBlocked(1, basePolicy))
        assertFalse(AppUpdateManager.isVersionBlocked(2, basePolicy))
        assertFalse(AppUpdateManager.isVersionBlocked(3, basePolicy))

        // Mandatory flag with minSupportedCode = 1 blocks v1 when latest mandatory = 2
        val mandatoryOnlyLatest = basePolicy.copy(minSupportedCode = 1, mandatory = true, versionCode = 2)
        assertTrue(AppUpdateManager.isVersionBlocked(1, mandatoryOnlyLatest))

        // Optional update (not mandatory and installed >= minSupportedCode) does not block
        val optionalPolicy = basePolicy.copy(minSupportedCode = 1, mandatory = false, versionCode = 2)
        assertFalse(AppUpdateManager.isVersionBlocked(1, optionalPolicy))

        // Inactive policy does not block
        val inactivePolicy = basePolicy.copy(active = false)
        assertFalse(AppUpdateManager.isVersionBlocked(1, inactivePolicy))
    }

    @Test
    fun testSha256AndApkDownloadUrl() {
        val tmp = File.createTempFile("apk_hash_test", ".bin")
        tmp.writeText("pao-com-carne-apk-content")
        val computed = AppUpdateManager.computeFileSha256(tmp)
        assertEquals(64, computed.length)
        assertTrue(AppUpdateManager.verifySha256(computed, computed.uppercase()))
        assertFalse(AppUpdateManager.verifySha256(computed, "0".repeat(64)))
        assertTrue(AppUpdateManager.verifySha256(computed, ""))
        tmp.delete()

        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/app-releases/app-v2.apk",
            AppUpdateManager.buildApkDownloadUrl("https://example.supabase.co/", "app-v2.apk")
        )
        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/custom-bucket/app-v2.apk",
            AppUpdateManager.buildApkDownloadUrl("https://example.supabase.co", "/custom-bucket/app-v2.apk")
        )
        assertEquals(
            "https://cdn.example.com/app.apk",
            AppUpdateManager.buildApkDownloadUrl("https://example.supabase.co", "https://cdn.example.com/app.apk")
        )
    }

    @Test
    fun testAdminDateValidationAndPresets() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day10 = cal.timeInMillis
        cal.set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
        val day15 = cal.timeInMillis

        assertNull(AdminDateCalculator.validateDateRange(day10, day15))
        assertNull(AdminDateCalculator.validateDateRange(day10, day10))
        assertNotNull(AdminDateCalculator.validateDateRange(day15, day10))

        val (prevStart, prevEnd) = AdminDateCalculator.rangeForOption(
            option = DateFilterOption.MES_ANTERIOR,
            customStart = day10,
            customEnd = day15,
            referenceMillis = day15
        )
        val checkCal = Calendar.getInstance().apply { timeInMillis = prevStart }
        assertEquals(Calendar.OCTOBER, checkCal.get(Calendar.MONTH))
        assertEquals(1, checkCal.get(Calendar.DAY_OF_MONTH))
        checkCal.timeInMillis = prevEnd
        assertEquals(Calendar.OCTOBER, checkCal.get(Calendar.MONTH))
        assertEquals(31, checkCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testAdminMetricsExcludeDeletedAndApplyMultiFilters() {
        val now = 1_762_800_000_000L
        val shifts = listOf(
            Shift("s1", "h1", "Ana", ShiftPeriod.MANHA, now - 10000, status = ShiftStatus.CLOSED),
            Shift("s2", "h2", "Bruno", ShiftPeriod.NOITE, now - 5000, status = ShiftStatus.OPEN)
        )
        val attendances = listOf(
            Attendance("a1", "s1", "h1", "Ana", AttendanceType.DUPLA, 2, 1, 1, createdAt = now, isPreferential = false, isDeleted = false),
            Attendance("a2", "s2", "h2", "Bruno", AttendanceType.SOLO, 1, 1, 0, createdAt = now, isPreferential = true, isDeleted = false),
            // Logically deleted record must NEVER count in KPIs
            Attendance("a3", "s1", "h1", "Ana", AttendanceType.GRUPO, 4, 4, 0, createdAt = now, isPreferential = false, isDeleted = true)
        )
        val members = listOf(
            AttendanceMember("m1", "a1", 1, ClientClassification.NOVO, "Instagram", now),
            AttendanceMember("m2", "a1", 2, ClientClassification.RECORRENTE, null, now),
            AttendanceMember("m3", "a2", 1, ClientClassification.NOVO, "TikTok", now),
            AttendanceMember("m4", "a3", 1, ClientClassification.NOVO, "Google", now)
        )

        val allFilter = AdminFilterState(dateOption = DateFilterOption.TODOS)
        val (metricsAll, openShifts) = AdminDateCalculator.computeMetrics(shifts, attendances, members, allFilter)
        assertEquals(3, metricsAll.totalPeople)
        assertEquals(2, metricsAll.totalAttendances)
        assertEquals(2, metricsAll.totalNewClients)
        assertEquals(1, metricsAll.totalReturningClients)
        assertEquals(2, metricsAll.morningAttendances)
        assertEquals(1, metricsAll.nightAttendances)
        assertEquals(1, openShifts.size)
        assertFalse(metricsAll.originDistribution.containsKey("Google"))

        // Filter by Origin = TikTok + Preferential
        val originPrefFilter = AdminFilterState(
            dateOption = DateFilterOption.TODOS,
            selectedOrigin = "TikTok",
            preferentialOnly = true
        )
        val (filteredMetrics, _) = AdminDateCalculator.computeMetrics(shifts, attendances, members, originPrefFilter)
        assertEquals(1, filteredMetrics.totalPeople)
        assertEquals(1, filteredMetrics.totalAttendances)
        assertEquals(1, filteredMetrics.originDistribution["TikTok"])
    }

    @Test
    fun testSensitiveTextSanitizationAndDtoSerialization() {
        val raw = "Falha com pin=1234 e token: eyJhbGciOiJIUzI1NiJ9.aaa.bbb e sb_publishable_secret123"
        val clean = SystemLogRepository.sanitizeSensitiveText(raw)
        assertFalse(clean.contains("1234"))
        assertFalse(clean.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(clean.contains("sb_publishable_secret123"))

        val attDto = SupabaseAttendanceDto(
            id = "att-1",
            shiftId = "s-1",
            hosterId = "h-1",
            hosterName = "Ana",
            type = "SOLO",
            totalMembers = 1,
            newMembers = 1,
            returningMembers = 0,
            createdAt = "2026-11-12T10:00:00Z",
            updatedAt = "2026-11-12T10:05:00Z",
            isPreferential = false,
            isDeleted = true,
            deletedAt = "2026-11-12T10:05:00Z",
            deletedBy = "Ana",
            deletionReason = "Desistiu"
        )
        val attJson = SupabaseClient.moshi.adapter(SupabaseAttendanceDto::class.java).toJson(attDto)
        assertTrue(attJson.contains("\"is_deleted\":true"))
        assertTrue(attJson.contains("\"deletion_reason\":\"Desistiu\""))

        val logDto = SupabaseSystemLogDto(
            id = "log-1",
            userId = "h-1",
            userName = "Ana",
            action = "ATENDIMENTO_EXCLUIDO",
            entityType = "ATTENDANCE",
            entityId = "att-1",
            deviceName = "Tablet Recepção",
            result = "SUCCESS",
            description = "Teste",
            createdAt = "2026-11-12T10:05:00Z",
            syncedAt = null
        )
        val logJson = SupabaseClient.moshi.adapter(SupabaseSystemLogDto::class.java).toJson(logDto)
        assertTrue(logJson.contains("\"device_name\":\"Tablet Recepção\""))

        val relDto = SupabaseAppReleaseDto(
            id = "r-1",
            versionName = "1.0",
            versionCode = 1,
            minSupportedCode = 1,
            mandatory = false,
            apkPath = "v1.apk",
            sha256 = "",
            changelog = "Inicial",
            releaseReason = "Base",
            active = true,
            createdAt = "2026-11-12T10:00:00Z"
        )
        assertEquals(1, relDto.versionCode)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }
}
