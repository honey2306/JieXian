package com.jiexian.app.data

import android.content.Context
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.RuleType
import kotlinx.coroutines.flow.Flow

class RuleRepository private constructor(
    context: Context,
) {
    private val dao = JieXianDatabase.get(context).ruleDao()

    val rules: Flow<List<AppRule>> = dao.observeRules()

    fun activeLocks(now: Long = System.currentTimeMillis()): Flow<List<ActiveLock>> = dao.observeActiveLocks(now)

    suspend fun addRule(rule: AppRule) = dao.insertRule(rule)

    suspend fun updateRule(rule: AppRule) = dao.updateRule(rule)

    suspend fun deleteRule(rule: AppRule) = dao.deleteRule(rule)

    suspend fun allRules(): List<AppRule> = dao.allRules()

    suspend fun rulesForPackage(packageName: String): List<AppRule> = dao.allRules().filter { it.containsPackage(packageName) }

    suspend fun allowOnlyRules(): List<AppRule> = dao.allRules().filter { it.type == RuleType.ALLOW_ONLY }

    suspend fun activeLocksForPackage(
        packageName: String,
        now: Long,
    ): List<ActiveLock> = dao.activeLocksForPackage(packageName, now)

    suspend fun activeLocksForRule(
        ruleId: String,
        now: Long,
    ): List<ActiveLock> = dao.activeLocksForRule(ruleId, now)

    suspend fun createLockIfAbsent(
        lock: ActiveLock,
        now: Long,
    ) = dao.createLockIfAbsent(lock, now)

    suspend fun deleteExpiredLocks(now: Long) = dao.deleteExpiredLocks(now)

    companion object {
        @Volatile private var instance: RuleRepository? = null

        fun get(context: Context): RuleRepository =
            instance ?: synchronized(this) {
                instance ?: RuleRepository(context.applicationContext).also { instance = it }
            }
    }
}
