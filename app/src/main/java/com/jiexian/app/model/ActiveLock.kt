package com.jiexian.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A snapshot of a restriction that has already started.
 * Editing or deleting its source rule never edits or removes this snapshot.
 */
@Entity(tableName = "active_locks")
data class ActiveLock(
    @PrimaryKey val id: String,
    val sourceRuleId: String,
    val ruleName: String,
    val packageName: String,
    val appName: String,
    val type: RuleType,
    val unlockAt: Long,
    val detail: String,
    val startedAt: Long = System.currentTimeMillis(),
)
