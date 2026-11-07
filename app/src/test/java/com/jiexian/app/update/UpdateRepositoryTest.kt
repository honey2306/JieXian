package com.jiexian.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateRepositoryTest {
    @Test
    fun `parses signed release manifest`() {
        val release = UpdateRepository.parseManifest(manifest())
        assertEquals(42, release.versionCode)
        assertEquals("1.2.0", release.versionName)
        assertEquals("https://github.com/honey2306/JieXian/releases/download/v1.2.0/JieXian-1.2.0.apk", release.apkUrl)
        assertEquals(12345L, release.sizeBytes)
    }

    @Test
    fun `rejects insecure or malformed manifests`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateRepository.parseManifest(manifest().replace("https://", "http://"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateRepository.parseManifest(manifest().replace("a".repeat(64), "invalid"))
        }
        assertThrows(Exception::class.java) {
            UpdateRepository.parseManifest(manifest().replace("12345", "0"))
        }
    }

    private fun manifest(): String =
        """
        {
          "versionCode": 42,
          "versionName": "1.2.0",
          "notes": "测试更新",
          "apkUrl": "https://github.com/honey2306/JieXian/releases/download/v1.2.0/JieXian-1.2.0.apk",
          "sha256": "${"a".repeat(64)}",
          "sizeBytes": 12345
        }
        """.trimIndent()
}
