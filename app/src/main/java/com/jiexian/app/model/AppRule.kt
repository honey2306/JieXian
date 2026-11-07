package com.jiexian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RuleType {
    DAILY_LIMIT,
    TIME_WINDOW,
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
    /** Inclusive start, minutes after midnight. Only used by TIME_WINDOW. */
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
        require(startMinute in 0..1439)
        require(endMinute in 0..1439)
        if (type == RuleType.TIME_WINDOW) require(startMinute != endMinute)
    }

    fun containsPackage(packageName: String): Boolean = targets.any { it.packageName == packageName }

    fun isInWindow(minuteOfDay: Int): Boolean {
        if (type != RuleType.TIME_WINDOW) return false
        return if (startMinute < endMinute) {
            minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    fun windowLabel(): String = "${minuteLabel(startMinute)} – ${minuteLabel(endMinute)}"
}

fun minuteLabel(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
