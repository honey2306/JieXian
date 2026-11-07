package com.jiexian.app.domain

import com.jiexian.app.model.AppRule
import com.jiexian.app.model.RuleType
import com.jiexian.app.model.minuteLabel
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

private const val FIVE_MINUTES_MILLIS = 5 * 60_000L
private const val ONE_HOUR_MILLIS = 60 * 60_000L

enum class ReminderKind {
    QUOTA,
    WINDOW,
    SCREEN_TIME,
}

data class DueReminder(
    val key: String,
    val kind: ReminderKind,
    val title: String,
    val text: String,
)

object ReminderEngine {
    fun dueReminders(
        rules: List<AppRule>,
        usageTodayMillis: Map<String, Long>,
        screenOnTodayMillis: Long,
        now: ZonedDateTime,
        alreadySentKeys: Set<String>,
    ): List<DueReminder> =
        buildList {
            val date = now.toLocalDate()
            rules
                .filter { it.type == RuleType.DAILY_LIMIT }
                .flatMap { rule -> rule.targets.map { target -> target to rule } }
                .groupBy { (target, _) -> target.packageName }
                .forEach { (packageName, targets) ->
                    val used = usageTodayMillis[packageName] ?: 0L
                    val remaining = targets.minOf { (_, rule) -> rule.limitMinutes * 60_000L - used }
                    val key = "quota:$date:$packageName"
                    if (remaining in 1..FIVE_MINUTES_MILLIS && key !in alreadySentKeys) {
                        add(
                            DueReminder(
                                key = key,
                                kind = ReminderKind.QUOTA,
                                title = "${targets.first().first.appName} 今日额度即将用完",
                                text = "剩余约 5 分钟，额度用完后将锁定至次日 00:00。",
                            ),
                        )
                    }
                }

            rules.filter { it.type == RuleType.TIME_WINDOW }.forEach { rule ->
                val todayStart =
                    now
                        .toLocalDate()
                        .atTime(LocalTime.of(rule.startMinute / 60, rule.startMinute % 60))
                        .atZone(now.zone)
                val nextStart = if (now.isBefore(todayStart)) todayStart else todayStart.plusDays(1)
                val untilStart = Duration.between(now, nextStart).toMillis()
                val key = "window:${nextStart.toLocalDate()}:${rule.id}:${rule.startMinute}"
                if (untilStart in 1..FIVE_MINUTES_MILLIS && key !in alreadySentKeys) {
                    val appNames =
                        rule.targets.take(2).joinToString("、") { it.appName } +
                            if (rule.targets.size > 2) " 等 ${rule.targets.size} 个应用" else ""
                    add(
                        DueReminder(
                            key = key,
                            kind = ReminderKind.WINDOW,
                            title = "「${rule.name}」将在 5 分钟后开始",
                            text = "$appNames · ${minuteLabel(rule.startMinute)} 起进入禁用时段。",
                        ),
                    )
                }
            }

            val completedHours = (screenOnTodayMillis / ONE_HOUR_MILLIS).toInt()
            if (completedHours >= 1) {
                val key = "screen:$date:$completedHours"
                if (key !in alreadySentKeys) {
                    add(
                        DueReminder(
                            key = key,
                            kind = ReminderKind.SCREEN_TIME,
                            title = "今日已亮屏 $completedHours 小时",
                            text = "放下手机，让眼睛和注意力休息一会儿。",
                        ),
                    )
                }
            }
        }
}
