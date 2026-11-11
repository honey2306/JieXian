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
        ruleTotalUsageMillis: Map<String, Long> = emptyMap(),
    ): List<ActiveLock> =
        rules.mapNotNull { rule ->
            when (rule.type) {
                RuleType.DAILY_LIMIT -> {
                    val target = rule.targets.firstOrNull { it.packageName == packageName } ?: return@mapNotNull null
                    val totalLimitMinutes = rule.totalLimitMinutes
                    val totalUsedMillis = ruleTotalUsageMillis[rule.id] ?: 0L
                    val perAppExhausted = usageTodayMillis >= rule.limitMinutes * 60_000L
                    val totalExhausted = totalLimitMinutes != null && totalUsedMillis >= totalLimitMinutes * 60_000L
                    if (!perAppExhausted && !totalExhausted) return@mapNotNull null
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
                        detail =
                            if (totalExhausted) {
                                "整组已计入 ${totalUsedMillis / 60_000L} 分钟，$totalLimitMinutes 分钟总额度已用完"
                            } else {
                                "今日已计入 ${usageTodayMillis / 60_000L} 分钟，${rule.limitMinutes} 分钟额度已用完"
                            },
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

    /** Combined usage per rule, for rules that have a group total limit. */
    fun ruleUsageTotals(
        rules: List<AppRule>,
        usageTodayMillis: Map<String, Long>,
    ): Map<String, Long> =
        rules
            .filter { it.totalLimitMinutes != null }
            .associate { rule -> rule.id to rule.targets.sumOf { usageTodayMillis[it.packageName] ?: 0L } }

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
