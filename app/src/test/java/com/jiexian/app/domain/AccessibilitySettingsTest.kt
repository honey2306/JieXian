package com.jiexian.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsTest {
    private val packageName = "com.jiexian.app"
    private val serviceClass = "com.jiexian.app.service.LimitAccessibilityService"

    private fun listed(value: String?) = AccessibilitySettings.isServiceListed(value, packageName, serviceClass)

    @Test
    fun `matches the canonical flattened component`() {
        assertTrue(listed("com.jiexian.app/com.jiexian.app.service.LimitAccessibilityService"))
    }

    @Test
    fun `matches a shorthand class name written by some vendors`() {
        assertTrue(listed("com.jiexian.app/.service.LimitAccessibilityService"))
    }

    @Test
    fun `does not treat a dotted class of another package as relative`() {
        // Android only writes fully qualified classes or a leading dot, so "service.X"
        // means a class literally named "service.X", not "pkg.service.X".
        assertFalse(listed("com.jiexian.app/service.LimitAccessibilityService"))
    }

    @Test
    fun `finds the guardian among other enabled services`() {
        val value =
            "com.android.talkback/com.android.talkback.TalkBackService:" +
                "com.jiexian.app/com.jiexian.app.service.LimitAccessibilityService"
        assertTrue(listed(value))
    }

    @Test
    fun `ignores case and surrounding whitespace`() {
        assertTrue(listed(" com.JieXian.App / COM.JIEXIAN.APP.SERVICE.LIMITACCESSIBILITYSERVICE :x/y "))
    }

    @Test
    fun `does not match another service of the same app`() {
        assertFalse(listed("com.jiexian.app/com.jiexian.app.service.OtherService"))
    }

    @Test
    fun `does not match another package with the same class name`() {
        assertFalse(listed("com.example.app/com.jiexian.app.service.LimitAccessibilityService"))
    }

    @Test
    fun `returns false for an empty or missing setting`() {
        assertFalse(listed(null))
        assertFalse(listed(""))
        assertFalse(listed("  "))
        assertFalse(listed(":"))
    }

    @Test
    fun `skips malformed entries instead of failing`() {
        assertFalse(listed("com.jiexian.app"))
        assertTrue(listed("garbage:com.jiexian.app/com.jiexian.app.service.LimitAccessibilityService"))
    }
}
