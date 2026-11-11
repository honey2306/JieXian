package com.jiexian.app.domain

enum class UsageStateEvent {
    FOREGROUND,
    BACKGROUND,
    SCREEN_OFF,
    SHUTDOWN,
}

data class AppUsageEvent(
    val packageName: String,
    val atMillis: Long,
    val state: UsageStateEvent,
    /**
     * Class of the activity that changed state.
     *
     * One package can have several resumed activities (a player plus its floating window,
     * an activity plus a dialog activity of the same app). Comparing activity classes keeps
     * the foreground interval open until the last of them pauses.
     */
    val activityClass: String? = null,
)

/**
 * Calculates per-app foreground time from Android's activity state events.
 *
 * This replaces summing aggregated daily usage buckets, which can double count on devices
 * that return several overlapping buckets for one package and made a 30 minute quota fire
 * early. Intervals are clipped to the requested range; screen-off and shutdown close every
 * open interval so background playback is not counted as foreground usage.
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
        val resumed = mutableMapOf<String, MutableSet<String>>()

        fun close(
            packageName: String,
            atMillis: Long,
        ) {
            val openedAt = openAt.remove(packageName) ?: return
            resumed.remove(packageName)
            val from = maxOf(openedAt, startMillis)
            val to = minOf(atMillis, endMillis)
            if (to > from) totals[packageName] = (totals[packageName] ?: 0L) + (to - from)
        }

        fun closeAll(atMillis: Long) {
            openAt.keys.toList().forEach { close(it, atMillis) }
        }

        events.forEach { event ->
            when (event.state) {
                UsageStateEvent.FOREGROUND -> {
                    if (event.packageName.isEmpty()) return@forEach
                    if (event.packageName !in openAt) openAt[event.packageName] = event.atMillis
                    resumed
                        .getOrPut(event.packageName) { mutableSetOf() }
                        .add(event.activityClass.orEmpty())
                }

                UsageStateEvent.BACKGROUND -> {
                    if (event.packageName.isEmpty() || event.packageName !in openAt) return@forEach
                    val activities = resumed.getOrPut(event.packageName) { mutableSetOf() }
                    // Without a class there is no way to tell which activity paused, so treat
                    // the package as backgrounded.
                    if (event.activityClass == null) activities.clear() else activities.remove(event.activityClass)
                    if (activities.isEmpty()) close(event.packageName, event.atMillis)
                }

                UsageStateEvent.SCREEN_OFF,
                UsageStateEvent.SHUTDOWN,
                -> {
                    closeAll(event.atMillis)
                }
            }
        }
        closeAll(endMillis)
        return totals
    }
}
