package com.jiexian.app.service

import android.content.Context
import com.jiexian.app.data.ActivityChange
import com.jiexian.app.data.ActivityChangeKind
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.util.PermissionUtils

/**
 * Fallback enforcement for the case the accessibility service cannot handle.
 *
 * Windows can be killed, vendors can drop the accessibility grant, and the guard then has no
 * way to send the user home. It can still cover the locked app with a full screen overlay and
 * swallow every touch, so the app is not usable until the lock ends. Foreground tracking uses
 * activity events incrementally, because usage totals cannot tell what is on screen now.
 */
class OverlayGuard(
    private val context: Context,
    private val repository: RuleRepository,
    private val usageRepository: UsageRepository,
    private val snapshotter: LockSnapshotter,
    private val overlay: AppBlockerOverlay = AppBlockerOverlay(context),
) {
    private val running = mutableMapOf<String, MutableSet<String>>()
    private var foreground: String? = null
    private var lastPollAt = 0L
    private var lastSnapshotAt = 0L

    /**
     * Polls once. The accessibility service and the overlay never block the same package at
     * the same time: when accessibility is connected its block screen is the better answer.
     */
    suspend fun tick(
        accessibilityRunning: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        if (accessibilityRunning || !PermissionUtils.canDrawOverlays(context)) {
            hide()
            return
        }
        if (lastPollAt == 0L || now - lastSnapshotAt > SNAPSHOT_INTERVAL_MILLIS) {
            lastSnapshotAt = now
            runCatching { snapshotter.snapshotAll() }
        }
        val from = if (lastPollAt == 0L) now - INITIAL_LOOKBACK_MILLIS else lastPollAt
        lastPollAt = now
        runCatching { usageRepository.activityChanges(from, now) }
            .getOrDefault(emptyList())
            .forEach(::applyChange)

        val packageName = foreground
        if (packageName == null || packageName == context.packageName) {
            hide()
            return
        }
        val lock =
            repository
                .activeLocksForPackage(packageName, now)
                .maxByOrNull(ActiveLock::unlockAt)
        if (lock == null) hide() else overlay.show(lock, packageName)
    }

    /** Keeps one overlay when the same app is still on screen; drops it when switching apps. */
    private fun applyChange(change: ActivityChange) {
        when (change.kind) {
            ActivityChangeKind.RESUMED -> {
                if (change.packageName.isEmpty()) return
                running
                    .getOrPut(change.packageName) { mutableSetOf() }
                    .add(change.activityClass.orEmpty())
                foreground = change.packageName
            }

            ActivityChangeKind.PAUSED -> {
                val activities = running[change.packageName] ?: return
                val key = change.activityClass
                if (key == null) activities.clear() else activities.remove(key)
                if (activities.isEmpty()) {
                    running.remove(change.packageName)
                    if (foreground == change.packageName) {
                        foreground = running.keys.lastOrNull()
                    }
                }
            }

            ActivityChangeKind.SCREEN_OFF -> {
                running.clear()
                foreground = null
            }
        }
    }

    fun hide() = overlay.hide()

    companion object {
        private const val INITIAL_LOOKBACK_MILLIS = 30 * 60_000L
        private const val SNAPSHOT_INTERVAL_MILLIS = 5_000L

        /** Pure decision used by tests: overlay only when accessibility is not doing the job. */
        fun shouldBlock(
            accessibilityRunning: Boolean,
            overlayAllowed: Boolean,
            foregroundPackage: String?,
            ownPackage: String,
            hasActiveLock: Boolean,
        ): Boolean =
            overlayAllowed &&
                !accessibilityRunning &&
                hasActiveLock &&
                !foregroundPackage.isNullOrEmpty() &&
                foregroundPackage != ownPackage
    }
}
