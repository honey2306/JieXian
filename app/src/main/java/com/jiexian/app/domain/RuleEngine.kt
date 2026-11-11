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
            when (rule.type) {
                RuleType.DAILY_LIMIT -> {
                    val target = rule.targets.firstOrNull { it.packageName == packageName } ?: return@mapNotNull null
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
                    // Listed targets are the ones that get locked.
                    val target = rule.targets.firstOrNull { it.packageName == packageName } ?: return@mapNotNull null
                    val minute = now.hour * 60 + now.minute
                    if (!rule.isInWindow(minute)) return@mapNotNull null
                    val startDate = windowStartDate(rule, now.toLocalDate(), minute)
                    ActiveLock(
                        id = "${rule.id}:$packageName:window:$startDate:${rule.startMinute}",
                        sourceRuleId = rule.id,
                        ruleName = rule.name,
                        packageName = target.packageName,
                        appName = target.appName,
                        type = rule.type,
                        unlockAt = windowEndMillis(rule, startDate, now.zone),
                        detail = "禁用时段 ${rule.windowLabel()}",
                    )
                }

                RuleType.ALLOW_ONLY -> {
                    // Listed targets are the ones that stay usable; everyone else gets locked.
                    if (rule.containsPackage(packageName)) return@mapNotNull null
                    val minute = now.hour * 60 + now.minute
                    if (!rule.isInWindow(minute)) return@mapNotNull null
                    val startDate = windowStartDate(rule, now.toLocalDate(), minute)
                    val allowedNames =
                        rule.targets.take(2).joinToString("、") { it.appName } +
                            if (rule.targets.size > 2) " 等 ${rule.targets.size} 个" else ""
                    ActiveLock(
                        id = "${rule.id}:$packageName:allow:$startDate:${rule.startMinute}",
                        sourceRuleId = rule.id,
                        ruleName = rule.name,
                        packageName = packageName,
                        appName = packageName,
                        type = rule.type,
                        unlockAt = windowEndMillis(rule, startDate, now.zone),
                        detail = "允许清单 ${rule.windowLabel()} · 仅可使用 $allowedNames",
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

    private fun windowEndMillis(
        rule: AppRule,
        startDate: LocalDate,
        zone: java.time.ZoneId,
    ): Long {
        val endDate = if (rule.startMinute > rule.endMinute) startDate.plusDays(1) else startDate
        return endDate
            .atTime(LocalTime.of(rule.endMinute / 60, rule.endMinute % 60))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

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
