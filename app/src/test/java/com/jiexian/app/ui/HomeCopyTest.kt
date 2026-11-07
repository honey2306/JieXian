package com.jiexian.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCopyTest {
    @Test
    fun `home copy follows time of day`() {
        assertTrue(homeCopyFor(8, hasRules = true, activeRuleCount = 0).title.contains("天亮"))
        assertTrue(homeCopyFor(14, hasRules = true, activeRuleCount = 0).title.contains("慢下来"))
        assertTrue(homeCopyFor(20, hasRules = true, activeRuleCount = 0).title.contains("夜幕"))
        assertTrue(homeCopyFor(23, hasRules = true, activeRuleCount = 0).title.contains("夜深"))
    }

    @Test
    fun `active and empty states take priority over time copy`() {
        val active = homeCopyFor(8, hasRules = true, activeRuleCount = 2)
        val empty = homeCopyFor(8, hasRules = false, activeRuleCount = 0)
        assertTrue(active.title.contains("暂时安静"))
        assertTrue(active.subtitle.contains("2 条界限"))
        assertTrue(empty.title.contains("留一道界限"))
    }
}
