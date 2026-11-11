package com.jiexian.app.service

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Small on-device timeline of guardian state changes, kept only in this app's preferences.
 *
 * Vendors stop accessibility services in ways the app cannot observe while dead, so the only
 * way to tell "the system revoked the grant" apart from "the process was killed but the grant
 * survived" is to compare what was recorded before and after. Nothing here leaves the device.
 */
object GuardianLog {
    private const val PREFERENCES_NAME = "guardian_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 40
    private const val MAX_CHARS = 8_000

    private val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun record(
        context: Context,
        event: String,
        at: Long = System.currentTimeMillis(),
    ) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val stamp = formatter.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
            val previous = prefs.getString(KEY_ENTRIES, "").orEmpty()
            val entries =
                (previous.lines().filter { it.isNotBlank() } + "$stamp  $event")
                    .takeLast(MAX_ENTRIES)
            val text = entries.joinToString("\n").takeLast(MAX_CHARS)
            prefs.edit().putString(KEY_ENTRIES, text).apply()
        }
    }

    fun entries(context: Context): List<String> =
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "")
                .orEmpty()
                .lines()
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
}
