package com.jiexian.app.domain

data class ScreenEvent(
    val atMillis: Long,
    val interactive: Boolean,
)

object ScreenTimeCalculator {
    fun calculate(
        events: List<ScreenEvent>,
        startMillis: Long,
        endMillis: Long,
    ): Long {
        if (endMillis <= startMillis) return 0L
        var interactive = false
        var interactiveSince: Long? = null
        var total = 0L

        events.sortedBy(ScreenEvent::atMillis).forEach { event ->
            if (event.atMillis < startMillis) {
                interactive = event.interactive
                interactiveSince = null
                return@forEach
            }
            if (event.atMillis >= endMillis) return@forEach
            when {
                event.interactive && !interactive -> {
                    interactiveSince = event.atMillis
                }

                !event.interactive && interactive -> {
                    total += event.atMillis - (interactiveSince ?: startMillis)
                    interactiveSince = null
                }
            }
            interactive = event.interactive
        }
        if (interactive) total += endMillis - (interactiveSince ?: startMillis)
        return total.coerceIn(0L, endMillis - startMillis)
    }
}
