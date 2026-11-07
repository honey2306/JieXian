package com.jiexian.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import com.jiexian.app.domain.ScreenEvent
import com.jiexian.app.domain.ScreenTimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class ScreenTimeRepository(
    private val context: Context,
) {
    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    private val usageRepository = UsageRepository(context)

    suspend fun screenOnToday(now: ZonedDateTime = ZonedDateTime.now()): Long =
        withContext(Dispatchers.IO) {
            if (!usageRepository.hasUsageAccess()) return@withContext 0L
            val start =
                now
                    .toLocalDate()
                    .atStartOfDay(now.zone)
                    .toInstant()
                    .toEpochMilli()
            val end = now.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT >= 29) queryScreenEvents(start, end) else sampleLegacyScreenTime(start, end)
        }

    @RequiresApi(29)
    private fun queryScreenEvents(
        start: Long,
        end: Long,
    ): Long {
        val usageEvents = usageStatsManager.queryEvents(start - SCREEN_STATE_LOOKBACK_MILLIS, end)
        val event = UsageEvents.Event()
        val events = mutableListOf<ScreenEvent>()
        while (usageEvents.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> events += ScreenEvent(event.timeStamp, interactive = true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> events += ScreenEvent(event.timeStamp, interactive = false)
            }
        }
        return ScreenTimeCalculator.calculate(events, start, end)
    }

    private fun sampleLegacyScreenTime(
        start: Long,
        end: Long,
    ): Long {
        val preferences = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        val sameDay = preferences.getLong(KEY_DAY_START, -1L) == start
        var total = if (sameDay) preferences.getLong(KEY_TOTAL, 0L) else 0L
        val lastSample = preferences.getLong(KEY_LAST_SAMPLE, 0L)
        if (preferences.getBoolean(KEY_WAS_INTERACTIVE, false) && lastSample > 0L && end > lastSample) {
            val elapsed = (end - maxOf(start, lastSample)).coerceIn(0L, MAX_SAMPLE_GAP_MILLIS)
            total += elapsed
        }
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        preferences
            .edit()
            .putLong(KEY_DAY_START, start)
            .putLong(KEY_TOTAL, total)
            .putLong(KEY_LAST_SAMPLE, end)
            .putBoolean(KEY_WAS_INTERACTIVE, interactive)
            .apply()
        return total
    }

    companion object {
        private const val LEGACY_PREFERENCES = "legacy_screen_time"
        private const val KEY_DAY_START = "day_start"
        private const val KEY_TOTAL = "total"
        private const val KEY_LAST_SAMPLE = "last_sample"
        private const val KEY_WAS_INTERACTIVE = "was_interactive"
        private const val MAX_SAMPLE_GAP_MILLIS = 60_000L
        private const val SCREEN_STATE_LOOKBACK_MILLIS = 24 * 60 * 60 * 1000L
    }
}
