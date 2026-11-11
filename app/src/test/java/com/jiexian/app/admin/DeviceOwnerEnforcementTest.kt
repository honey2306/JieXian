package com.jiexian.app.admin

import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerEnforcementTest {
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
    fun `suspends packages blocked by quota and window rules`() {
        val suspended =
            DeviceOwnerEnforcement.suspendedPackages(
                listOf(
                    lock("com.example.first", RuleType.DAILY_LIMIT),
                    lock("com.example.second", RuleType.TIME_WINDOW),
                ),
            )
        assertEquals(setOf("com.example.first", "com.example.second"), suspended)
    }

    @Test
    fun `does not suspend the app itself`() {
        val suspended =
            DeviceOwnerEnforcement.suspendedPackages(
                listOf(lock("com.jiexian.app", RuleType.DAILY_LIMIT), lock("com.example.first", RuleType.DAILY_LIMIT)),
                ownPackage = "com.jiexian.app",
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `leaves allow-only locks to the accessibility path`() {
        val suspended =
            DeviceOwnerEnforcement.suspendedPackages(
                listOf(lock("com.example.first", RuleType.DAILY_LIMIT), lock("com.example.third", RuleType.ALLOW_ONLY)),
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `deduplicates packages with several active locks`() {
        val suspended =
            DeviceOwnerEnforcement.suspendedPackages(
                listOf(
                    lock("com.example.first", RuleType.DAILY_LIMIT, id = "a"),
                    lock("com.example.first", RuleType.TIME_WINDOW, id = "b"),
                ),
            )
        assertEquals(setOf("com.example.first"), suspended)
    }

    @Test
    fun `empty lock list suspends nothing`() {
        assertTrue(DeviceOwnerEnforcement.suspendedPackages(emptyList()).isEmpty())
    }
}
