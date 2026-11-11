package com.jiexian.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalculatorTest {
    private val start = 1_000_000L
    private val end = start + 60 * 60_000L

    private fun event(
        packageName: String,
        offsetMinutes: Int,
        state: UsageStateEvent,
        activityClass: String? = null,
    ) = AppUsageEvent(packageName, start + offsetMinutes * 60_000L, state, activityClass)

    @Test
    fun `sums foreground intervals per package`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND),
                        event("b", 0, UsageStateEvent.FOREGROUND),
                        event("a", 10, UsageStateEvent.BACKGROUND),
                        event("b", 25, UsageStateEvent.BACKGROUND),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(10 * 60_000L, usage["a"])
        assertEquals(25 * 60_000L, usage["b"])
    }

    @Test
    fun `interval started before the day is clipped to the day start`() {
        val beforeStart = start - 30 * 60_000L
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        AppUsageEvent("a", beforeStart, UsageStateEvent.FOREGROUND),
                        AppUsageEvent("a", start + 5 * 60_000L, UsageStateEvent.BACKGROUND),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(5 * 60_000L, usage["a"])
    }

    @Test
    fun `still open interval is counted until the query end`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events = listOf(event("a", 40, UsageStateEvent.FOREGROUND)),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(20 * 60_000L, usage["a"])
    }

    @Test
    fun `screen off closes every open interval`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND),
                        event("b", 10, UsageStateEvent.FOREGROUND),
                        event("", 20, UsageStateEvent.SCREEN_OFF),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(20 * 60_000L, usage["a"])
        assertEquals(10 * 60_000L, usage["b"])
    }

    @Test
    fun `duplicate resume and stray background events do not corrupt totals`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND),
                        event("a", 5, UsageStateEvent.FOREGROUND),
                        event("b", 7, UsageStateEvent.BACKGROUND),
                        event("a", 20, UsageStateEvent.BACKGROUND),
                        event("a", 25, UsageStateEvent.BACKGROUND),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(20 * 60_000L, usage["a"])
        assertTrue("b" !in usage)
    }

    @Test
    fun `intervals entirely outside the range are ignored`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        AppUsageEvent("a", start - 20 * 60_000L, UsageStateEvent.FOREGROUND),
                        AppUsageEvent("a", start - 10 * 60_000L, UsageStateEvent.BACKGROUND),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertTrue(usage.isEmpty())
    }

    @Test
    fun `two resumed activities keep the interval open until the last one pauses`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND, "Main"),
                        event("a", 10, UsageStateEvent.FOREGROUND, "Player"),
                        event("a", 20, UsageStateEvent.BACKGROUND, "Main"),
                        event("a", 30, UsageStateEvent.BACKGROUND, "Player"),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(30 * 60_000L, usage["a"])
    }

    @Test
    fun `repeated resume of one activity does not outlive its pause`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND, "Main"),
                        event("a", 5, UsageStateEvent.FOREGROUND, "Main"),
                        event("a", 20, UsageStateEvent.BACKGROUND, "Main"),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(20 * 60_000L, usage["a"])
    }

    @Test
    fun `pause without a class name closes the package`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND, "Main"),
                        event("a", 15, UsageStateEvent.BACKGROUND),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(15 * 60_000L, usage["a"])
    }

    @Test
    fun `shutdown closes every open interval`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        event("a", 0, UsageStateEvent.FOREGROUND, "Main"),
                        event("", 12, UsageStateEvent.SHUTDOWN),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(12 * 60_000L, usage["a"])
    }

    @Test
    fun `activity opened before the range start is counted from the range start`() {
        val usage =
            UsageCalculator.foregroundMillis(
                events =
                    listOf(
                        AppUsageEvent("a", start - 40 * 60_000L, UsageStateEvent.FOREGROUND, "Main"),
                        AppUsageEvent("a", start + 12 * 60_000L, UsageStateEvent.BACKGROUND, "Main"),
                    ),
                startMillis = start,
                endMillis = end,
            )
        assertEquals(12 * 60_000L, usage["a"])
    }

    @Test
    fun `empty or inverted range returns nothing`() {
        assertTrue(UsageCalculator.foregroundMillis(listOf(event("a", 1, UsageStateEvent.FOREGROUND)), start, start).isEmpty())
    }
}
