package com.jiexian.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

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

    suspend fun usageToday(packageNames: Collection<String>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess() || packageNames.isEmpty()) return@withContext emptyMap()
            val zone = ZoneId.systemDefault()
            val start =
                LocalDate
                    .now(zone)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val now = System.currentTimeMillis()
            val result = packageNames.associateWith { 0L }.toMutableMap()
            usageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty()
                .filter { it.packageName in result }
                .forEach { stats ->
                    // Some Android versions return more than one bucket for the same package.
                    result[stats.packageName] = (result[stats.packageName] ?: 0L) +
                        stats.totalTimeInForeground.coerceAtLeast(0L)
                }
            result
        }
}
