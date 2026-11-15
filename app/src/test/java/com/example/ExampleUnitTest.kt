package com.example

import com.example.domain.model.AttendanceType
import com.example.domain.model.ClientClassification
import com.example.domain.model.ClientOrigins
import com.example.domain.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPasswordHashingAndVerification() {
        val salt = SecurityUtils.generateSalt()
        assertTrue(salt.isNotBlank())
        val hash = SecurityUtils.hashPassword("pao1234", salt)
        assertTrue(hash.isNotBlank())

        assertTrue(SecurityUtils.verifyPassword("pao1234", salt, hash))
        assertFalse(SecurityUtils.verifyPassword("wrongpass", salt, hash))

        val adminSalt = "c6bca501f28b49e6"
        val expectedAdminHash = "e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9"
        val computedAdminHash = SecurityUtils.hashPassword("admin", adminSalt)
        assertEquals(expectedAdminHash, computedAdminHash)
        assertTrue(SecurityUtils.verifyPassword("admin", adminSalt, expectedAdminHash))
        assertFalse(SecurityUtils.verifyPassword("wrongadmin", adminSalt, expectedAdminHash))
    }

    @Test
    fun testAttendanceRules() {
        assertEquals(1, AttendanceType.SOLO.fixedMembers)
        assertEquals(2, AttendanceType.DUPLA.fixedMembers)
        assertEquals(null, AttendanceType.GRUPO.fixedMembers)

        assertEquals(7, ClientOrigins.ALL.size)
        assertTrue(ClientOrigins.ALL.contains("Instagram"))
        assertTrue(ClientOrigins.ALL.contains("TikTok"))
        assertTrue(ClientOrigins.ALL.contains("Indicação"))
        assertTrue(ClientOrigins.ALL.contains("iFood"))
        assertTrue(ClientOrigins.ALL.contains("Google"))
        assertTrue(ClientOrigins.ALL.contains("Já foi na antiga loja"))
        assertTrue(ClientOrigins.ALL.contains("Gringo"))
    }

    @Test
    fun testMixedMembersSerialization() {
        val members = listOf(
            com.example.data.remote.SupabaseMemberDto(
                id = "m1",
                attendanceId = "att1",
                memberNumber = 1,
                classification = "NOVO",
                origin = "Indicação",
                createdAt = "2026-11-10T12:00:00Z"
            ),
            com.example.data.remote.SupabaseMemberDto(
                id = "m2",
                attendanceId = "att1",
                memberNumber = 2,
                classification = "RECORRENTE",
                origin = null,
                createdAt = "2026-11-10T12:00:00Z"
            )
        )
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.remote.SupabaseMemberDto::class.java)
        val json = com.example.data.remote.SupabaseClient.moshi.adapter<List<com.example.data.remote.SupabaseMemberDto>>(type).serializeNulls().toJson(members)

        // Assert that both objects in the array contain the "origin" key so PostgREST bulk insert succeeds
        assertTrue(json.contains("\"origin\":\"Indicação\""))
        assertTrue(json.contains("\"origin\":null"))
    }

    @Test
    fun testGenerateXlsxWith3Sheets() {
        val tempFile = java.io.File.createTempFile("test_report", ".xlsx")
        val attendances = listOf(
            com.example.domain.util.XlsxGenerator.AttendanceRow(
                id = "att1",
                date = "10/11/2026 12:30",
                shift = "Manhã",
                hoster = "Admin",
                type = "SOLO",
                isPreferential = "Não",
                totalMembers = 1,
                newMembers = 1,
                returningMembers = 0
            )
        )
        val clients = listOf(
            com.example.domain.util.XlsxGenerator.ClientRow(
                attendanceId = "att1",
                hoster = "Admin",
                memberNumber = 1,
                classification = "Novo",
                origin = "Instagram",
                isPreferential = "Não"
            )
        )
        val daily = listOf(
            com.example.domain.util.XlsxGenerator.DailySummary(
                dateLabel = "10/11/2026",
                totalClients = 1,
                morningClients = 1,
                nightClients = 0,
                newClients = 1,
                returningClients = 0,
                preferentialCount = 0,
                originsBreakdown = mapOf("Instagram" to 1)
            )
        )
        val consolidated = com.example.domain.util.XlsxGenerator.ReportConsolidatedSummary(
            dailySummaries = daily,
            grandTotalClients = 1,
            grandMorningClients = 1,
            grandNightClients = 0,
            grandNewClients = 1,
            grandReturningClients = 0,
            grandPreferentialCount = 0,
            grandOriginsBreakdown = mapOf("Instagram" to 1)
        )

        com.example.domain.util.XlsxGenerator.generateWorkbook(tempFile, attendances, clients, consolidated)

        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 0)

        // Validate that sheet3.xml exists inside the generated zip
        val zipFile = java.util.zip.ZipFile(tempFile)
        val sheet1 = zipFile.getEntry("xl/worksheets/sheet1.xml")
        val sheet2 = zipFile.getEntry("xl/worksheets/sheet2.xml")
        val sheet3 = zipFile.getEntry("xl/worksheets/sheet3.xml")
        assertTrue(sheet1 != null)
        assertTrue(sheet2 != null)
        assertTrue(sheet3 != null)
        zipFile.close()
        tempFile.delete()
    }
}
