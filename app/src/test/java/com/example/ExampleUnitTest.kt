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
}
