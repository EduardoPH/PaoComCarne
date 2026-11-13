package com.example.data.local

import android.content.Context
import android.os.Build
import java.util.UUID

class DeviceIdentityManager(context: Context) {
    private val prefs = context.getSharedPreferences("pao_device_prefs", Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getFriendlyName(): String {
        val saved = prefs.getString(KEY_FRIENDLY_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val defaultName = buildDefaultDeviceName()
        prefs.edit().putString(KEY_FRIENDLY_NAME, defaultName).apply()
        return defaultName
    }

    fun setFriendlyName(name: String) {
        val clean = name.trim().ifBlank { buildDefaultDeviceName() }
        prefs.edit().putString(KEY_FRIENDLY_NAME, clean).apply()
    }

    fun getDeviceDisplayName(): String {
        val friendly = getFriendlyName()
        val shortId = getDeviceId().take(6).uppercase()
        return "$friendly ($shortId)"
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_uuid"
        private const val KEY_FRIENDLY_NAME = "device_friendly_name"

        fun buildDefaultDeviceName(
            manufacturer: String = Build.MANUFACTURER.orEmpty(),
            model: String = Build.MODEL.orEmpty()
        ): String {
            val cleanMan = manufacturer.trim().replaceFirstChar { it.uppercase() }
            val cleanModel = model.trim()
            return when {
                cleanModel.isBlank() && cleanMan.isBlank() -> "Dispositivo Android"
                cleanModel.isBlank() -> cleanMan
                cleanMan.isBlank() -> cleanModel
                cleanModel.startsWith(cleanMan, ignoreCase = true) -> cleanModel
                else -> "$cleanMan $cleanModel"
            }
        }
    }
}
