package com.jiexian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RuleType {
    DAILY_LIMIT,
    TIME_WINDOW,

    /** Inverse of [TIME_WINDOW]: only [AppRule.targets] stay usable, everything else is locked. */
    ALLOW_ONLY,
}

data class AppTarget(
    val packageName: String,
    val appName: String,
) {
    init {
        require(packageName.isNotBlank())
        require(appName.isNotBlank())
    }
}

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targets: List<AppTarget>,
    val type: RuleType,
    /** Daily quota per target app in minutes. Only used by DAILY_LIMIT. */
    val limitMinutes: Int = 60,
    /**
     * Optional daily quota for all [targets] together, in minutes. Once the group total is
     * reached, every app in the rule locks until next midnight. Only used by DAILY_LIMIT.
     */
    val totalLimitMinutes: Int? = null,
    /** Inclusive start, minutes after midnight. Only used by window rules. */
    val startMinute: Int = 23 * 60,
    /** Exclusive end, minutes after midnight. Overnight windows are supported. */
    val endMinute: Int = 9 * 60,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(name.isNotBlank())
        require(targets.isNotEmpty())
        require(targets.distinctBy(AppTarget::packageName).size == targets.size)
        require(limitMinutes in 5..720)
        require(totalLimitMinutes == null || totalLimitMinutes in 5..MAX_TOTAL_LIMIT_MINUTES)
        if (totalLimitMinutes != null) require(totalLimitMinutes > limitMinutes)
        require(startMinute in 0..1439)
        require(endMinute in 0..1439)
        if (usesWindow) require(startMinute != endMinute)
    }

    /** Daily quota rules are the only kind that are not bound to a clock window. */
    val usesWindow: Boolean get() = type != RuleType.DAILY_LIMIT

    fun containsPackage(packageName: String): Boolean = targets.any { it.packageName == packageName }

    fun isInWindow(minuteOfDay: Int): Boolean {
        if (!usesWindow) return false
        return if (startMinute < endMinute) {
            minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    fun windowLabel(): String = "${minuteLabel(startMinute)} – ${minuteLabel(endMinute)}"
}

fun minuteLabel(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** Upper bound for a rule-wide daily quota, in minutes: one full day. */
const val MAX_TOTAL_LIMIT_MINUTES = 1440
