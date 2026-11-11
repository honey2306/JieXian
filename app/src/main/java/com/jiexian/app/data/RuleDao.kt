package com.jiexian.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM app_rules ORDER BY createdAt ASC")
    fun observeRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules ORDER BY createdAt ASC")
    suspend fun allRules(): List<AppRule>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRule(rule: AppRule)

    @Update
    suspend fun updateRule(rule: AppRule)

    @Delete
    suspend fun deleteRule(rule: AppRule)

    @Query("SELECT * FROM active_locks WHERE unlockAt > :now ORDER BY unlockAt ASC")
    fun observeActiveLocks(now: Long): Flow<List<ActiveLock>>

    @Query("SELECT * FROM active_locks WHERE unlockAt > :now")
    suspend fun locksNow(now: Long): List<ActiveLock>

    @Query("SELECT * FROM active_locks WHERE packageName = :packageName AND unlockAt > :now")
    suspend fun activeLocksForPackage(
        packageName: String,
        now: Long,
    ): List<ActiveLock>

    @Query("SELECT * FROM active_locks WHERE sourceRuleId = :ruleId AND unlockAt > :now")
    suspend fun activeLocksForRule(
        ruleId: String,
        now: Long,
    ): List<ActiveLock>

    @Query("SELECT * FROM active_locks WHERE id = :id")
    suspend fun lockById(id: String): ActiveLock?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLock(lock: ActiveLock)

    @Query("DELETE FROM active_locks WHERE unlockAt <= :now")
    suspend fun deleteExpiredLocks(now: Long)

    @Transaction
    suspend fun createLockIfAbsent(
        lock: ActiveLock,
        now: Long,
    ) {
        deleteExpiredLocks(now)
        if (lockById(lock.id) == null) insertLock(lock)
    }
}
