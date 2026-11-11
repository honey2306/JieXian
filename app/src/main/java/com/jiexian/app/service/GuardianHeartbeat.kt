package com.jiexian.app.service

import android.content.Context

/**
 * Liveness signal for the accessibility service.
 *
 * The system settings list alone is not proof that the guardian is running: after a force
 * stop, a data clear or an aggressive vendor task cleaner the service can stay listed as
 * enabled while nothing is actually watching the foreground app. The accessibility service
 * refreshes this heartbeat while it runs, and the UI/notification compare it against the
 * settings state.
 */
object GuardianHeartbeat {
    private const val PREFERENCES_NAME = "guardian"
    private const val KEY_LAST_SIGNAL = "accessibility_last_signal"
    private const val MIN_WRITE_INTERVAL_MILLIS = 15_000L

    /** A running handler may be delayed while the device dozes, so allow a generous gap. */
    const val STALE_AFTER_MILLIS = 10 * 60_000L

    private var lastWriteAt = 0L

    fun signal(
        context: Context,
        at: Long = System.currentTimeMillis(),
    ) {
        if (at - lastWriteAt < MIN_WRITE_INTERVAL_MILLIS) return
        lastWriteAt = at
        preferences(context).edit().putLong(KEY_LAST_SIGNAL, at).apply()
    }

    fun lastSignalAt(context: Context): Long = preferences(context).getLong(KEY_LAST_SIGNAL, 0L)

    fun isAlive(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (ProtectionState.serviceRunning.value) return true
        val last = lastSignalAt(context)
        return last > 0L && now - last <= STALE_AFTER_MILLIS
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
