package com.jiexian.app.enforce

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import com.jiexian.app.service.GuardianLog
import com.jiexian.app.service.GuardianRestartReceiver
import com.jiexian.app.util.PermissionUtils
import rikka.shizuku.Shizuku
import java.util.Collections

/**
 * System level enforcement through Shizuku (the ADB shell identity, no root).
 *
 * An accessibility or overlay guard dies with the app process. Asking the platform to suspend the
 * locked package is a system state instead: the app cannot be launched at all, and killing the
 * guard, cleaning tasks or rebooting does not undo it. Shizuku must be running and must have
 * granted this app the shell permission; otherwise everything falls back to the accessibility
 * and overlay paths.
 */
object ShizukuEnforcement {
    enum class State {
        /** Shizuku is not installed or its service is not running. */
        UNAVAILABLE,

        /** Shizuku runs, but this app has no shell permission yet. */
        UNAUTHORIZED,

        /** Shell commands are available. */
        READY,
    }

    private const val PREFERENCES_NAME = "system_enforcement"
    private const val KEY_SUSPENDED = "suspended_packages"
    private const val UNLOCK_REQUEST_CODE = 4201

    fun state(): State {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) return State.UNAVAILABLE
        val granted =
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        return if (granted) State.READY else State.UNAUTHORIZED
    }

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /**
     * Packages the platform should refuse to launch for the given locks.
     *
     * Allow-only rules are skipped: their "locked" set is everything that is not allowed, which is
     * decided per foreground event, not from a stored target list.
     */
    fun suspendedPackages(
        locks: List<ActiveLock>,
        ownPackage: String,
    ): Set<String> =
        locks
            .asSequence()
            .filter { it.type != RuleType.ALLOW_ONLY }
            .map { it.packageName }
            .filter { it.isNotBlank() && it != ownPackage }
            .toSet()

    /** Brings the platform suspension state in line with the current locks. */
    suspend fun sync(
        context: Context,
        repository: RuleRepository,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (state() != State.READY) return false
        val locks = repository.locksNow(now)
        val desired = suspendedPackages(locks, context.packageName)
        applySuspension(context, desired)
        ensureAccessibility(context, hasRules = repository.allRules().isNotEmpty())
        scheduleUnlockCheck(context, locks.minOfOrNull(ActiveLock::unlockAt))
        return true
    }

    private suspend fun applySuspension(
        context: Context,
        desired: Set<String>,
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val current: Set<String> =
            preferences.getStringSet(KEY_SUSPENDED, emptySet())?.toSet() ?: emptySet()
        val toSuspend = desired - current
        val toUnsuspend = current - desired
        val suspended = mutableSetOf<String>()
        val released = mutableSetOf<String>()
        toSuspend.forEach { packageName ->
            if (ShizukuShell.run(context, "pm suspend $packageName") != null) {
                suspended += packageName
            }
        }
        toUnsuspend.forEach { packageName ->
            if (ShizukuShell.run(context, "pm unsuspend $packageName") != null) {
                released += packageName
            }
        }
        // Only record what actually ran, so a failed command (or a missing shell service) is
        // retried on the next pass instead of being remembered as done.
        val applied = (current - released) + suspended
        preferences
            .edit()
            .putStringSet(KEY_SUSPENDED, Collections.unmodifiableSet(applied))
            .apply()
        if (suspended.isNotEmpty() || released.isNotEmpty()) {
            GuardianLog.record(
                context,
                "系统封锁：暂停 ${suspended.size} 个，解除 ${released.size} 个",
            )
        } else if (toSuspend.isNotEmpty() || toUnsuspend.isNotEmpty()) {
            GuardianLog.record(context, "系统封锁：命令未能执行，稍后重试")
        }
    }

    /**
     * Writes the accessibility grant back after a vendor cleaner or a reboot dropped it.
     *
     * Only while the user still has rules: the guard should not switch itself back on when the
     * app is no longer used for anything.
     */
    private suspend fun ensureAccessibility(
        context: Context,
        hasRules: Boolean,
    ) {
        if (!hasRules || PermissionUtils.isAccessibilityEnabledInSettings(context)) return
        val component = "${context.packageName}/${com.jiexian.app.service.LimitAccessibilityService::class.java.name}"
        val existing =
            ShizukuShell
                .run(context, "settings get secure enabled_accessibility_services")
                ?.trim()
                .orEmpty()
        val merged =
            when {
                existing.isEmpty() || existing == "null" -> component
                existing.split(':').any { it.equals(component, ignoreCase = true) } -> return
                else -> "$existing:$component"
            }
        ShizukuShell.run(context, "settings put secure enabled_accessibility_services $merged")
        ShizukuShell.run(context, "settings put secure accessibility_enabled 1")
        GuardianLog.record(context, "系统封锁：写回无障碍授权")
    }

    /** Wakes the guard at the earliest unlock time so suspended apps are released on time. */
    private fun scheduleUnlockCheck(
        context: Context,
        earliestUnlockAt: Long?,
    ) {
        if (earliestUnlockAt == null || earliestUnlockAt <= System.currentTimeMillis()) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                earliestUnlockAt,
                unlockCheckIntent(context),
            )
        }
    }

    private fun unlockCheckIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            UNLOCK_REQUEST_CODE,
            Intent(context, GuardianRestartReceiver::class.java)
                .setAction(GuardianRestartReceiver.ACTION_UNLOCK_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

}
