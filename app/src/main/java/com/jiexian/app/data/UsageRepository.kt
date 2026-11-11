package com.jiexian.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.jiexian.app.domain.AppUsageEvent
import com.jiexian.app.domain.UsageCalculator
import com.jiexian.app.domain.UsageStateEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class ActivityChangeKind {
    RESUMED,
    PAUSED,
    SCREEN_OFF,
}

/** Minimal activity state change used by the overlay fallback while accessibility is gone. */
data class ActivityChange(
    val packageName: String,
    val activityClass: String?,
    val kind: ActivityChangeKind,
)

data class ForegroundResume(
    val packageName: String,
    val atMillis: Long,
)

class UsageRepository(
    private val context: Context,
) {
    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    private val usageLock = Any()
    private var cachedDayStart = -1L
    private var cachedAt = 0L
    private var cachedUsage: Map<String, Long> = emptyMap()

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    suspend fun latestForegroundResume(windowMillis: Long = 15_000L): ForegroundResume? =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess()) return@withContext null
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - windowMillis, now + 1_000L)
            val event = UsageEvents.Event()
            var latest: ForegroundResume? = null
            while (events.getNextEvent(event)) {
                val isResume =
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isResume && event.timeStamp >= (latest?.atMillis ?: Long.MIN_VALUE)) {
                    latest = ForegroundResume(event.packageName, event.timeStamp)
                }
            }
            latest
        }

    /**
     * Foreground time per package since local midnight.
     *
     * Built from [UsageEvents] instead of summed [UsageStats] buckets: several devices return
     * overlapping buckets for one package, and summing them counted usage twice, so a quota
     * could lock before the user actually used it.
     */
    suspend fun usageToday(packageNames: Collection<String>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess() || packageNames.isEmpty()) return@withContext emptyMap()
            val zone = ZoneId.systemDefault()
            val dayStart =
                LocalDate
                    .now(zone)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val now = System.currentTimeMillis()
            usageSince(dayStart, now).filterKeys { it in packageNames }
        }

    /**
     * Raw activity transitions between [fromMillis] and now.
     *
     * The overlay fallback follows the foreground package itself, because usage totals cannot
     * tell whether an app is on screen right now.
     */
    suspend fun activityChanges(
        fromMillis: Long,
        toMillis: Long = System.currentTimeMillis(),
    ): List<ActivityChange> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess()) return@withContext emptyList()
            val events =
                runCatching { usageStatsManager.queryEvents(fromMillis, toMillis + 1_000L) }.getOrNull()
                    ?: return@withContext emptyList()
            val event = UsageEvents.Event()
            val changes = mutableListOf<ActivityChange>()
            while (events.getNextEvent(event)) {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND ->
                        changes += ActivityChange(event.packageName.orEmpty(), event.className, ActivityChangeKind.RESUMED)
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    EVENT_ACTIVITY_STOPPED,
                    -> changes += ActivityChange(event.packageName.orEmpty(), event.className, ActivityChangeKind.PAUSED)
                    EVENT_SCREEN_NON_INTERACTIVE ->
                        changes += ActivityChange("", null, ActivityChangeKind.SCREEN_OFF)
                }
            }
            changes
        }

    private fun usageSince(
        dayStart: Long,
        now: Long,
    ): Map<String, Long> =
        synchronized(usageLock) {
            if (dayStart == cachedDayStart && now - cachedAt in 0..CACHE_TTL_MILLIS) return cachedUsage
            val events = foregroundEvents(dayStart - LOOKBACK_MILLIS, now)
            val usage = UsageCalculator.foregroundMillis(events, dayStart, now)
            cachedDayStart = dayStart
            cachedAt = now
            cachedUsage = usage
            usage
        }

    private fun foregroundEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<AppUsageEvent> {
        val events = runCatching { usageStatsManager.queryEvents(startMillis, endMillis) }.getOrNull() ?: return emptyList()
        val event = UsageEvents.Event()
        return buildList {
            while (events.getNextEvent(event)) {
                val state =
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageStateEvent.FOREGROUND
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageStateEvent.BACKGROUND
                        EVENT_ACTIVITY_STOPPED -> UsageStateEvent.BACKGROUND
                        EVENT_SCREEN_NON_INTERACTIVE -> UsageStateEvent.SCREEN_OFF
                        EVENT_DEVICE_SHUTDOWN -> UsageStateEvent.SHUTDOWN
                        else -> null
                    } ?: continue
                add(
                    AppUsageEvent(
                        packageName = event.packageName.orEmpty(),
                        atMillis = event.timeStamp,
                        state = state,
                        activityClass = event.className,
                    ),
                )
            }
        }
    }

    companion object {
        /**
         * Also look back before midnight so an app that stayed in the foreground across the
         * daylight boundary is still counted, instead of missing its start event.
         */
        private const val LOOKBACK_MILLIS = 12 * 60 * 60 * 1000L
        private const val CACHE_TTL_MILLIS = 3_000L

        /** `ACTIVITY_STOPPED` on API 29+, inlined so the field is never touched on older devices. */
        private const val EVENT_ACTIVITY_STOPPED = 23

        /** `SCREEN_NON_INTERACTIVE` on API 28+; older systems report a background event instead. */
        private const val EVENT_SCREEN_NON_INTERACTIVE = 16

        /** `DEVICE_SHUTDOWN`; the app cannot observe a rebooting device any other way. */
        private const val EVENT_DEVICE_SHUTDOWN = 26
    }
}
