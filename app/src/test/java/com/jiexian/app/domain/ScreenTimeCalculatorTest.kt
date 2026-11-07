package com.jiexian.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeCalculatorTest {
    private val midnight = 1_000_000L

    @Test
    fun `sums separate interactive intervals and excludes gaps`() {
        val events =
            listOf(
                ScreenEvent(midnight + minutes(10), true),
                ScreenEvent(midnight + minutes(25), false),
                ScreenEvent(midnight + minutes(45), true),
            )
        assertEquals(minutes(30), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(60)))
    }

    @Test
    fun `non interactive music listening time is not counted`() {
        val events =
            listOf(
                ScreenEvent(midnight, true),
                ScreenEvent(midnight + minutes(5), false),
            )
        assertEquals(minutes(5), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(60)))
    }

    @Test
    fun `screen already on at midnight counts only current day`() {
        val events =
            listOf(
                ScreenEvent(midnight - minutes(10), true),
                ScreenEvent(midnight + minutes(10), false),
            )
        assertEquals(minutes(10), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(60)))
    }

    @Test
    fun `screen off before midnight does not leak time into current day`() {
        val events =
            listOf(
                ScreenEvent(midnight - minutes(10), false),
                ScreenEvent(midnight + minutes(30), true),
            )
        assertEquals(minutes(30), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(60)))
    }

    @Test
    fun `currently interactive interval is closed at now`() {
        val events = listOf(ScreenEvent(midnight, true))
        assertEquals(minutes(42), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(42)))
    }

    @Test
    fun `unsorted events and missing events are handled safely`() {
        val events =
            listOf(
                ScreenEvent(midnight + minutes(20), false),
                ScreenEvent(midnight + minutes(10), true),
            )
        assertEquals(minutes(10), ScreenTimeCalculator.calculate(events, midnight, midnight + minutes(30)))
        assertEquals(0L, ScreenTimeCalculator.calculate(emptyList(), midnight, midnight + minutes(30)))
        assertEquals(0L, ScreenTimeCalculator.calculate(events, midnight, midnight))
    }

    private fun minutes(value: Int): Long = value * 60_000L
}
