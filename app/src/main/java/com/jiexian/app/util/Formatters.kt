package com.jiexian.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatMinutes(minutes: Int): String =
    when {
        minutes < 60 -> "$minutes 分钟"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分"
    }

fun formatUsage(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    return if (totalMinutes < 60) {
        "$totalMinutes 分钟"
    } else {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (minutes == 0) "$hours 小时" else "$hours 小时 $minutes 分"
    }
}

fun unlockTimeLabel(unlockAt: Long): String {
    val time = Instant.ofEpochMilli(unlockAt).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
}
