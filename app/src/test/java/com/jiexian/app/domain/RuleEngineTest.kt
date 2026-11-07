package com.jiexian.app.domain

import com.jiexian.app.model.AppRule
import com.jiexian.app.model.AppTarget
import com.jiexian.app.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val firstTarget = AppTarget("example.app", "Example")
    private val secondTarget = AppTarget("second.app", "Second")
    private val dailyRule =
        AppRule(
            id = "daily",
            name = "日间留白",
            targets = listOf(firstTarget, secondTarget),
            type = RuleType.DAILY_LIMIT,
            limitMinutes = 30,
        )
    private val overnightRule =
        AppRule(
            id = "night",
            name = "睡前免打扰",
            targets = listOf(firstTarget, secondTarget),
            type = RuleType.TIME_WINDOW,
            startMinute = 23 * 60,
            endMinute = 9 * 60,
        )

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): ZonedDateTime = ZonedDateTime.of(2026, 1, day, hour, minute, 0, 0, zone)

    @Test
    fun `daily limit triggers exactly at quota and unlocks next midnight`() {
        val locks = RuleEngine.triggeredLocks(listOf(dailyRule), firstTarget.packageName, at(1, 20), 30 * 60_000L)
        assertEquals(1, locks.size)
        assertEquals(at(2, 0).toInstant().toEpochMilli(), locks.single().unlockAt)
    }

    @Test
    fun `daily limit does not trigger one millisecond before quota`() {
        assertTrue(RuleEngine.triggeredLocks(listOf(dailyRule), firstTarget.packageName, at(1, 20), 30 * 60_000L - 1).isEmpty())
    }

    @Test
    fun `multi app rule creates independent locks for each target`() {
        val first = RuleEngine.triggeredLocks(listOf(dailyRule), firstTarget.packageName, at(1, 20), 30 * 60_000L).single()
        val second = RuleEngine.triggeredLocks(listOf(dailyRule), secondTarget.packageName, at(1, 20), 30 * 60_000L).single()
        assertEquals(firstTarget.packageName, first.packageName)
        assertEquals(secondTarget.packageName, second.packageName)
        assertEquals("日间留白", first.ruleName)
        assertEquals("日间留白", second.ruleName)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `quota on one target does not lock another target`() {
        assertTrue(RuleEngine.triggeredLocks(listOf(dailyRule), secondTarget.packageName, at(1, 20), 29 * 60_000L).isEmpty())
    }

    @Test
    fun `overnight window triggers after midnight and keeps original end`() {
        val locks = RuleEngine.triggeredLocks(listOf(overnightRule), firstTarget.packageName, at(2, 8, 59), 0)
        assertEquals(1, locks.size)
        assertEquals(at(2, 9).toInstant().toEpochMilli(), locks.single().unlockAt)
        assertEquals("睡前免打扰", locks.single().ruleName)
    }

    @Test
    fun `overnight window is inactive during allowed afternoon`() {
        assertTrue(RuleEngine.triggeredLocks(listOf(overnightRule), firstTarget.packageName, at(1, 15), 0).isEmpty())
    }

    @Test
    fun `window end is exclusive`() {
        assertTrue(RuleEngine.triggeredLocks(listOf(overnightRule), firstTarget.packageName, at(2, 9), 0).isEmpty())
    }

    @Test
    fun `remaining quota uses strictest daily rule`() {
        val stricter = dailyRule.copy(id = "strict", limitMinutes = 10)
        val remaining = RuleEngine.remainingQuotaMillis(listOf(dailyRule, stricter), firstTarget.packageName, 6 * 60_000L)
        assertEquals(4 * 60_000L, remaining)
    }

    @Test
    fun `unrelated package never triggers`() {
        assertFalse(RuleEngine.triggeredLocks(listOf(dailyRule, overnightRule), "other.app", at(1, 23), Long.MAX_VALUE).isNotEmpty())
    }

    @Test
    fun `rule requires a name at least one target and unique packages`() {
        assertThrows(IllegalArgumentException::class.java) { dailyRule.copy(name = " ") }
        assertThrows(IllegalArgumentException::class.java) { dailyRule.copy(targets = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            dailyRule.copy(targets = listOf(firstTarget, firstTarget.copy(appName = "Duplicate")))
        }
    }
}
