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

        assertEquals(6, ClientOrigins.ALL.size)
        assertTrue(ClientOrigins.ALL.contains("Instagram"))
        assertTrue(ClientOrigins.ALL.contains("TikTok"))
        assertTrue(ClientOrigins.ALL.contains("Indicação"))
        assertTrue(ClientOrigins.ALL.contains("iFood"))
        assertTrue(ClientOrigins.ALL.contains("Google"))
        assertTrue(ClientOrigins.ALL.contains("Já foi na antiga loja"))
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
}
