package com.jiexian.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardScopeTest {
    private val scope =
        GuardScope(
            launchablePackages = setOf("com.example.browser", "com.example.game", "com.example.launcher"),
            protectedPackages = setOf("com.example.launcher", "com.android.systemui"),
        )

    @Test
    fun `launchable apps outside the protected set can be restricted`() {
        assertTrue(scope.canRestrict("com.example.browser"))
    }

    @Test
    fun `launcher and system packages are never restricted`() {
        assertFalse(scope.canRestrict("com.example.launcher"))
        assertFalse(scope.canRestrict("com.android.systemui"))
    }

    @Test
    fun `non launchable and blank packages are never restricted`() {
        assertFalse(scope.canRestrict("com.example.service"))
        assertFalse(scope.canRestrict(""))
    }
}
