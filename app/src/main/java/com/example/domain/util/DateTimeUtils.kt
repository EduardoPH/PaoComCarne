package com.example.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR"))
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    fun toIsoUtc(timestamp: Long): String {
        return isoFormat.format(Date(timestamp))
    }

    fun fromIsoUtc(isoString: String): Long {
        return try {
            isoFormat.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatFileDate(timestamp: Long): String {
        val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fileDateFormat.format(Date(timestamp))
    }
}
