package com.jiexian.app.domain

import com.jiexian.app.model.AppRule
import com.jiexian.app.model.AppTarget
import com.jiexian.app.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderEngineTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val first = AppTarget("first.app", "First")
    private val second = AppTarget("second.app", "Second")
    private val daily =
        AppRule(
            id = "daily",
            name = "每日留白",
            targets = listOf(first, second),
            type = RuleType.DAILY_LIMIT,
            limitMinutes = 30,
        )
    private val overnight =
        AppRule(
            id = "night",
            name = "睡前免打扰",
            targets = listOf(first, second),
            type = RuleType.TIME_WINDOW,
            startMinute = 23 * 60,
            endMinute = 9 * 60,
        )

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): ZonedDateTime = ZonedDateTime.of(2026, 1, day, hour, minute, second, 0, zone)

    @Test
    fun `each app gets its own five minute quota reminder`() {
        val due = due(daily, at(1, 20), mapOf(first.packageName to minutes(25), second.packageName to minutes(26)))
        assertEquals(2, due.size)
        assertEquals(2, due.map { it.key }.distinct().size)
        assertTrue(due.all { it.kind == ReminderKind.QUOTA })
    }

    @Test
    fun `quota reminder respects threshold exhaustion and deduplication`() {
        assertTrue(due(daily, at(1, 20), mapOf(first.packageName to minutes(25) - 1, second.packageName to minutes(30))).isEmpty())
        val used = mapOf(first.packageName to minutes(25), second.packageName to minutes(24))
        val sent = due(daily, at(1, 20), used).map { it.key }.toSet()
        assertTrue(due(daily, at(1, 20, 1), used, sent).isEmpty())
    }

    @Test
    fun `overlapping daily rules send only the strictest app reminder`() {
        val relaxed = daily.copy(id = "relaxed", limitMinutes = 60)
        val due =
            ReminderEngine.dueReminders(
                rules = listOf(relaxed, daily),
                usageTodayMillis = mapOf(first.packageName to minutes(25)),
                screenOnTodayMillis = 0L,
                now = at(1, 20),
                alreadySentKeys = emptySet(),
            )
        assertEquals(1, due.size)
        assertEquals("quota:2026-01-01:first.app", due.single().key)
    }

    @Test
    fun `window reminder fires at five minutes but not earlier`() {
        assertTrue(due(overnight, at(1, 22, 54, 59)).isEmpty())
        val reminder = due(overnight, at(1, 22, 55)).single()
        assertEquals(ReminderKind.WINDOW, reminder.kind)
        assertTrue(reminder.title.contains("睡前免打扰"))
        assertTrue(reminder.key.contains("2026-01-01"))
    }

    @Test
    fun `window reminder does not fire during active overnight window`() {
        assertTrue(due(overnight, at(1, 23, 30)).isEmpty())
        assertTrue(due(overnight, at(2, 8, 59)).isEmpty())
    }

    @Test
    fun `sent window cycle is not repeated`() {
        val reminder = due(overnight, at(1, 22, 55)).single()
        assertTrue(due(overnight, at(1, 22, 56), alreadySent = setOf(reminder.key)).isEmpty())
    }

    @Test
    fun `screen reminder fires at each newly completed hour only once`() {
        assertTrue(due(daily, at(1, 20), screenMillis = minutes(59)).none { it.kind == ReminderKind.SCREEN_TIME })
        val firstHour = due(daily, at(1, 20), screenMillis = minutes(60)).single { it.kind == ReminderKind.SCREEN_TIME }
        assertTrue(firstHour.title.contains("1 小时"))
        val secondHour =
            due(daily, at(1, 21), screenMillis = minutes(120), alreadySent = setOf(firstHour.key)).single {
                it.kind ==
                    ReminderKind.SCREEN_TIME
            }
        assertTrue(secondHour.title.contains("2 小时"))
        assertTrue(due(daily, at(1, 21, 1), screenMillis = minutes(120), alreadySent = setOf(firstHour.key, secondHour.key)).isEmpty())
    }

    private fun due(
        rule: AppRule,
        now: ZonedDateTime,
        usage: Map<String, Long> = emptyMap(),
        alreadySent: Set<String> = emptySet(),
        screenMillis: Long = 0L,
    ): List<DueReminder> =
        ReminderEngine.dueReminders(
            rules = listOf(rule),
            usageTodayMillis = usage,
            screenOnTodayMillis = screenMillis,
            now = now,
            alreadySentKeys = alreadySent,
        )

    private fun minutes(value: Int): Long = value * 60_000L
}
