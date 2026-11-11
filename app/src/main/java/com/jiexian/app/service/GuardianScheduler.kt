package com.jiexian.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jiexian.app.util.PermissionUtils

/**
 * Best-effort life support for the guardian: a repeating watchdog alarm that restarts the
 * foreground service after reboots, updates and vendor task cleaners. Nothing here can
 * survive a force stop or an uninstall, which always cancel alarms for good.
 */
object GuardianScheduler {
    const val ACTION_WATCHDOG = "com.jiexian.app.action.GUARDIAN_WATCHDOG"
    private const val WATCHDOG_INTERVAL_MILLIS = 15 * 60_000L
    private const val WATCHDOG_REQUEST_CODE = 4100

    fun start(context: Context) {
        if (!PermissionUtils.isAccessibilityEnabledInSettings(context)) {
            stop(context)
            return
        }
        if (!GuardService.isRunning()) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java)) }
        }
        ensureWatchdog(context)
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, GuardService::class.java)) }
        cancelWatchdog(context)
    }

    fun ensureWatchdog(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + WATCHDOG_INTERVAL_MILLIS,
                WATCHDOG_INTERVAL_MILLIS,
                watchdogPendingIntent(context),
            )
        }
    }

    fun cancelWatchdog(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(watchdogPendingIntent(context)) }
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, GuardianRestartReceiver::class.java).setAction(ACTION_WATCHDOG),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
