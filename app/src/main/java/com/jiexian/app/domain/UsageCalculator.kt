package com.jiexian.app.domain

enum class UsageStateEvent {
    FOREGROUND,
    BACKGROUND,
    SCREEN_OFF,
}

data class AppUsageEvent(
    val packageName: String,
    val atMillis: Long,
    val state: UsageStateEvent,
)

/**
 * Calculates per-app foreground time from Android's activity state events.
 *
 * This replaces summing aggregated daily usage buckets, which can double count on devices
 * that return several overlapping buckets for one package and made a 30 minute quota fire
 * early. Intervals are clipped to the requested range; a screen-off event closes every open
 * interval so background playback is not counted as foreground usage.
 */
object UsageCalculator {
    fun foregroundMillis(
        events: List<AppUsageEvent>,
        startMillis: Long,
        endMillis: Long,
    ): Map<String, Long> {
        if (endMillis <= startMillis) return emptyMap()
        val totals = mutableMapOf<String, Long>()
        val openAt = mutableMapOf<String, Long>()

        fun close(
            packageName: String,
            atMillis: Long,
        ) {
            val openedAt = openAt.remove(packageName) ?: return
            val from = maxOf(openedAt, startMillis)
            val to = minOf(atMillis, endMillis)
            if (to > from) totals[packageName] = (totals[packageName] ?: 0L) + (to - from)
        }

        events.forEach { event ->
            when (event.state) {
                UsageStateEvent.FOREGROUND -> {
                    if (event.packageName.isNotEmpty() && event.packageName !in openAt) {
                        openAt[event.packageName] = event.atMillis
                    }
                }

                UsageStateEvent.BACKGROUND -> {
                    if (event.packageName.isNotEmpty()) close(event.packageName, event.atMillis)
                }

                UsageStateEvent.SCREEN_OFF -> {
                    openAt.keys.toList().forEach { close(it, event.atMillis) }
                }
            }
        }
        openAt.keys.toList().forEach { close(it, endMillis) }
        return totals
    }
}
