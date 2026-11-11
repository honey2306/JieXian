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

    private val allowOnlyRule =
        AppRule(
            id = "allow",
            name = "专注",
            targets = listOf(firstTarget),
            type = RuleType.ALLOW_ONLY,
            startMinute = 23 * 60,
            endMinute = 9 * 60,
        )

    private val groupTotalRule =
        AppRule(
            id = "group",
            name = "组合额度",
            targets = listOf(firstTarget, secondTarget),
            type = RuleType.DAILY_LIMIT,
            limitMinutes = 30,
            totalLimitMinutes = 60,
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
    fun `allow list locks every other app and keeps listed apps usable`() {
        val blocked = RuleEngine.triggeredLocks(listOf(allowOnlyRule), secondTarget.packageName, at(1, 23, 30), 0).single()
        assertEquals(secondTarget.packageName, blocked.packageName)
        assertEquals(RuleType.ALLOW_ONLY, blocked.type)
        assertEquals(at(2, 9).toInstant().toEpochMilli(), blocked.unlockAt)
        assertTrue(RuleEngine.triggeredLocks(listOf(allowOnlyRule), firstTarget.packageName, at(1, 23, 30), 0).isEmpty())
    }

    @Test
    fun `allow list is inactive outside its window`() {
        assertTrue(RuleEngine.triggeredLocks(listOf(allowOnlyRule), secondTarget.packageName, at(1, 15), 0).isEmpty())
    }

    @Test
    fun `allow list and block list can coexist for different apps`() {
        val locks = RuleEngine.triggeredLocks(listOf(overnightRule, allowOnlyRule), "third.app", at(1, 23, 30), 0)
        // The block-list rule never matches an unlisted app; only the allow list locks it.
        assertEquals(1, locks.count { it.type == RuleType.ALLOW_ONLY })
    }

    @Test
    fun `group total locks an app that used little of its own quota`() {
        val totals =
            RuleEngine.ruleUsageTotals(
                listOf(groupTotalRule),
                mapOf(
                    firstTarget.packageName to 40 * 60_000L,
                    secondTarget.packageName to 20 * 60_000L,
                ),
            )
        assertEquals(60 * 60_000L, totals["group"])
        val lock = RuleEngine.triggeredLocks(listOf(groupTotalRule), secondTarget.packageName, at(1, 20), 20 * 60_000L, totals).single()
        assertEquals(at(2, 0).toInstant().toEpochMilli(), lock.unlockAt)
        assertEquals("整组已计入 60 分钟，60 分钟总额度已用完", lock.detail)
    }

    @Test
    fun `group total does not lock before the combined usage reaches it`() {
        val totals = mapOf("group" to 59 * 60_000L)
        assertTrue(RuleEngine.triggeredLocks(listOf(groupTotalRule), secondTarget.packageName, at(1, 20), 10 * 60_000L, totals).isEmpty())
        assertTrue(
            RuleEngine.triggeredLocks(listOf(groupTotalRule), secondTarget.packageName, at(1, 20), 10 * 60_000L, emptyMap()).isEmpty(),
        )
    }

    @Test
    fun `per app quota still locks while the group has room`() {
        val totals =
            RuleEngine.ruleUsageTotals(
                listOf(groupTotalRule),
                mapOf(
                    firstTarget.packageName to 30 * 60_000L,
                    secondTarget.packageName to 0L,
                ),
            )
        val lock = RuleEngine.triggeredLocks(listOf(groupTotalRule), firstTarget.packageName, at(1, 20), 30 * 60_000L, totals).single()
        assertEquals("今日已计入 30 分钟，30 分钟额度已用完", lock.detail)
    }

    @Test
    fun `rule usage totals only sum targets of rules with a group limit`() {
        val totals = RuleEngine.ruleUsageTotals(listOf(dailyRule, groupTotalRule), mapOf(firstTarget.packageName to 5 * 60_000L))
        assertEquals(setOf("group"), totals.keys)
        assertEquals(5 * 60_000L, totals["group"])
    }

    @Test
    fun `group total must exceed the per app quota and stay within a day`() {
        assertThrows(IllegalArgumentException::class.java) { groupTotalRule.copy(totalLimitMinutes = 30) }
        assertThrows(IllegalArgumentException::class.java) { groupTotalRule.copy(totalLimitMinutes = 1441) }
        assertThrows(IllegalArgumentException::class.java) { groupTotalRule.copy(totalLimitMinutes = 4) }
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
