package com.jiexian.app.service

import android.content.Context
import com.jiexian.app.data.ReminderPreferences
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.ScreenTimeRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.domain.DueReminder
import com.jiexian.app.domain.ReminderEngine
import com.jiexian.app.model.RuleType
import com.jiexian.app.notifications.ReminderNotifier
import java.time.ZonedDateTime

fun interface ReminderSender {
    fun send(reminder: DueReminder): Boolean
}

class ReminderScheduler(
    private val rules: RuleRepository,
    private val usage: UsageRepository,
    private val screenTime: ScreenTimeRepository,
    private val preferences: ReminderPreferences,
    private val sender: ReminderSender,
) {
    constructor(context: Context, rules: RuleRepository) : this(
        rules = rules,
        usage = UsageRepository(context),
        screenTime = ScreenTimeRepository(context),
        preferences = ReminderPreferences(context),
        sender = ReminderNotifier(context),
    )

    suspend fun check(now: ZonedDateTime = ZonedDateTime.now()) {
        preferences.prune(now.toLocalDate(), now.toLocalDate().plusDays(1))
        val allRules = rules.allRules()
        val packages =
            allRules
                .filter { it.type == RuleType.DAILY_LIMIT }
                .flatMap { rule -> rule.targets.map { target -> target.packageName } }
                .distinct()
        val due =
            ReminderEngine.dueReminders(
                rules = allRules,
                usageTodayMillis = usage.usageToday(packages),
                screenOnTodayMillis = screenTime.screenOnToday(now),
                now = now,
                alreadySentKeys = preferences.sentKeys(),
            )
        due.forEach { reminder ->
            if (sender.send(reminder)) preferences.markSent(reminder.key)
        }
    }
}
