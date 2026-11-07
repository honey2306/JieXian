package com.jiexian.app.domain

import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.RuleType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object RuleEngine {
    fun triggeredLocks(
        rules: List<AppRule>,
        packageName: String,
        now: ZonedDateTime,
        usageTodayMillis: Long,
    ): List<ActiveLock> =
        rules.mapNotNull { rule ->
            val target = rule.targets.firstOrNull { it.packageName == packageName } ?: return@mapNotNull null
            when (rule.type) {
                RuleType.DAILY_LIMIT -> {
                    if (usageTodayMillis < rule.limitMinutes * 60_000L) return@mapNotNull null
                    val tomorrow = now.toLocalDate().plusDays(1)
                    val unlockAt = tomorrow.atStartOfDay(now.zone).toInstant().toEpochMilli()
                    ActiveLock(
                        id = "${rule.id}:${target.packageName}:daily:${now.toLocalDate()}",
                        sourceRuleId = rule.id,
                        ruleName = rule.name,
                        packageName = target.packageName,
                        appName = target.appName,
                        type = rule.type,
                        unlockAt = unlockAt,
                        detail = "今日 ${rule.limitMinutes} 分钟额度已用完",
                    )
                }

                RuleType.TIME_WINDOW -> {
                    val minute = now.hour * 60 + now.minute
                    if (!rule.isInWindow(minute)) return@mapNotNull null
                    val startDate = windowStartDate(rule, now.toLocalDate(), minute)
                    val endDate = if (rule.startMinute > rule.endMinute) startDate.plusDays(1) else startDate
                    val unlockAt =
                        endDate
                            .atTime(LocalTime.of(rule.endMinute / 60, rule.endMinute % 60))
                            .atZone(now.zone)
                            .toInstant()
                            .toEpochMilli()
                    ActiveLock(
                        id = "${rule.id}:${target.packageName}:window:$startDate:${rule.startMinute}",
                        sourceRuleId = rule.id,
                        ruleName = rule.name,
                        packageName = target.packageName,
                        appName = target.appName,
                        type = rule.type,
                        unlockAt = unlockAt,
                        detail = "禁用时段 ${rule.windowLabel()}",
                    )
                }
            }
        }

    fun remainingQuotaMillis(
        rules: List<AppRule>,
        packageName: String,
        usageTodayMillis: Long,
    ): Long? =
        rules
            .filter { it.containsPackage(packageName) && it.type == RuleType.DAILY_LIMIT }
            .minOfOrNull { rule -> (rule.limitMinutes * 60_000L - usageTodayMillis).coerceAtLeast(0L) }

    private fun windowStartDate(
        rule: AppRule,
        today: LocalDate,
        minute: Int,
    ): LocalDate =
        if (rule.startMinute > rule.endMinute && minute < rule.endMinute) {
            today.minusDays(1)
        } else {
            today
        }
}
