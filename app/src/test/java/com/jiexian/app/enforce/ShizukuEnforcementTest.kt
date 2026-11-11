package com.jiexian.app.enforce

import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuEnforcementTest {
    private fun lock(
        packageName: String,
        type: RuleType,
        id: String = "$packageName:$type",
    ) = ActiveLock(
        id = id,
        sourceRuleId = "rule",
        ruleName = "rule",
        packageName = packageName,
        appName = packageName,
        type = type,
        unlockAt = 9_999_999_999_999L,
        detail = "",
    )

    @Test
    fun `suspends quota and window targets`() {
        val suspended =
            ShizukuEnforcement.suspendedPackages(
                listOf(
                    lock("com.example.first", RuleType.DAILY_LIMIT),
                    lock("com.example.second", RuleType.TIME_WINDOW),
                ),
                ownPackage = "com.jiexian.app",
            )
        assertEquals(setOf("com.example.first", "com.example.second"), suspended)
    }

    @Test
    fun `never suspends the app itself`() {
        val suspended =
            ShizukuEnforcement.suspendedPackages(
                listOf(lock("com.jiexian.app", RuleType.DAILY_LIMIT), lock("com.example.first", RuleType.DAILY_LIMIT)),
                ownPackage = "com.jiexian.app",
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `leaves allow-only locks to the foreground guard`() {
        val suspended =
            ShizukuEnforcement.suspendedPackages(
                listOf(lock("com.example.first", RuleType.DAILY_LIMIT), lock("com.example.third", RuleType.ALLOW_ONLY)),
                ownPackage = "com.jiexian.app",
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `deduplicates a package with several locks`() {
        val suspended =
            ShizukuEnforcement.suspendedPackages(
                listOf(
                    lock("com.example.first", RuleType.DAILY_LIMIT, id = "a"),
                    lock("com.example.first", RuleType.TIME_WINDOW, id = "b"),
                ),
                ownPackage = "com.jiexian.app",
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `empty locks suspend nothing`() {
        assertTrue(ShizukuEnforcement.suspendedPackages(emptyList(), ownPackage = "com.jiexian.app").isEmpty())
    }
}
