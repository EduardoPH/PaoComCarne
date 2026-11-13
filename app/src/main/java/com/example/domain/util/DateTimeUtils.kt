package com.example.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {
    private val isoFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
    }

    fun toIsoUtc(timestamp: Long): String {
        return (isoFormatThreadLocal.get()
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }).format(Date(timestamp))
    }

    fun fromIsoUtc(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        // Try Instant / OffsetDateTime if on API 26+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                return java.time.OffsetDateTime.parse(isoString).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    return java.time.Instant.parse(isoString).toEpochMilli()
                } catch (_: Exception) {}
            }
        }
        // Fallback with SimpleDateFormat
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(isoString)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    fun formatFileDate(timestamp: Long): String {
        val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fileDateFormat.format(Date(timestamp))
    }
}
