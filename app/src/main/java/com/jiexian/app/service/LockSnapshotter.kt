package com.jiexian.app.service

import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.domain.RuleEngine
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.RuleType
import java.time.ZonedDateTime

class LockSnapshotter(
    private val repository: RuleRepository,
    private val usageRepository: UsageRepository,
) {
    suspend fun snapshotAll(now: ZonedDateTime = ZonedDateTime.now()) {
        repository.deleteExpiredLocks(now.toInstant().toEpochMilli())
        val groups =
            repository
                .allRules()
                .flatMap { rule -> rule.targets.map { target -> target.packageName to rule } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        snapshotRuleGroups(groups, now)
    }

    suspend fun snapshotPackage(
        packageName: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        repository.deleteExpiredLocks(now.toInstant().toEpochMilli())
        snapshotRuleGroups(mapOf(packageName to repository.rulesForPackage(packageName)), now)
    }

    private suspend fun snapshotRuleGroups(
        ruleGroups: Map<String, List<AppRule>>,
        now: ZonedDateTime,
    ) {
        val minute = now.hour * 60 + now.minute
        val candidates =
            ruleGroups.filterValues { rules ->
                rules.any { it.type == RuleType.DAILY_LIMIT || it.isInWindow(minute) }
            }
        val dailyPackages =
            candidates
                .filterValues { rules ->
                    rules.any { it.type == RuleType.DAILY_LIMIT }
                }.keys
        val usage = usageRepository.usageToday(dailyPackages)
        val nowMillis = now.toInstant().toEpochMilli()

        candidates.forEach { (packageName, rules) ->
            val existingLocks = repository.activeLocksForPackage(packageName, nowMillis)
            val triggered =
                RuleEngine.triggeredLocks(
                    rules = rules,
                    packageName = packageName,
                    now = now,
                    usageTodayMillis = usage[packageName] ?: 0L,
                )
            triggered
                // An edit affects future cycles, never creates a second lock for the current cycle.
                .filter { lock -> existingLocks.none { it.sourceRuleId == lock.sourceRuleId } }
                .forEach { repository.createLockIfAbsent(it, nowMillis) }
        }
    }
}
