package com.jiexian.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGuardTest {
    private val own = "com.jiexian.app"

    private fun shouldBlock(
        accessibilityRunning: Boolean = false,
        overlayAllowed: Boolean = true,
        foreground: String? = "com.example.locked",
        hasLock: Boolean = true,
    ) = OverlayGuard.shouldBlock(
        accessibilityRunning = accessibilityRunning,
        overlayAllowed = overlayAllowed,
        foregroundPackage = foreground,
        ownPackage = own,
        hasActiveLock = hasLock,
    )

    @Test
    fun `blocks a locked app when accessibility is gone`() {
        assertTrue(shouldBlock())
    }

    @Test
    fun `leaves blocking to accessibility while it is connected`() {
        assertFalse(shouldBlock(accessibilityRunning = true))
    }

    @Test
    fun `does nothing without the overlay permission`() {
        assertFalse(shouldBlock(overlayAllowed = false))
    }

    @Test
    fun `does nothing when the foreground app is not locked`() {
        assertFalse(shouldBlock(hasLock = false))
    }

    @Test
    fun `never covers the app itself`() {
        assertFalse(shouldBlock(foreground = own))
    }

    @Test
    fun `does nothing when the foreground is unknown`() {
        assertFalse(shouldBlock(foreground = null))
        assertFalse(shouldBlock(foreground = ""))
    }
}
