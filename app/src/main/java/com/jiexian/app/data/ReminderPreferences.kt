package com.jiexian.app.data

import android.content.Context
import java.time.LocalDate

class ReminderPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun sentKeys(): Set<String> = preferences.getStringSet(KEY_SENT, emptySet()).orEmpty().toSet()

    fun prune(
        today: LocalDate,
        tomorrow: LocalDate,
    ) {
        val retainedDates = setOf(today.toString(), tomorrow.toString())
        val retained =
            sentKeys().filterTo(mutableSetOf()) { key ->
                val parts = key.split(':')
                parts.size >= 3 && parts[1] in retainedDates
            }
        if (retained != sentKeys()) preferences.edit().putStringSet(KEY_SENT, retained).apply()
    }

    @Synchronized
    fun markSent(key: String) {
        preferences.edit().putStringSet(KEY_SENT, sentKeys() + key).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "reminders"
        private const val KEY_SENT = "sent_keys"
    }
}
