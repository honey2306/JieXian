package com.jiexian.app.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val UPDATE_MANIFEST_URL = "https://github.com/honey2306/JieXian/releases/latest/download/update.json"

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

class UpdateRepository(
    private val context: Context,
) {
    suspend fun fetchLatest(): ReleaseInfo =
        withContext(Dispatchers.IO) {
            parseManifest(open(UPDATE_MANIFEST_URL).inputStream.bufferedReader().use { it.readText() })
        }

    suspend fun downloadAndVerify(release: ReleaseInfo): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "jiexian-${release.versionCode}.apk")
            val partial = File(directory, "${target.name}.part")
            partial.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                open(release.apkUrl).inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > release.sizeBytes) throw IllegalStateException("APK 文件大小超出清单记录")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (partial.length() != release.sizeBytes || !actualHash.equals(release.sha256, ignoreCase = true)) {
                    throw IllegalStateException("APK 校验失败，已停止安装")
                }
                partial.renameTo(target)
                target
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
        }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "仅允许 HTTPS 更新地址" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "JieXian-Android-Update")
            if (responseCode !in 200..299) throw IllegalStateException("更新服务器返回 $responseCode")
        }
    }

    companion object {
        fun parseManifest(value: String): ReleaseInfo {
            val json = JSONObject(value)
            val sha = json.getString("sha256")
            val url = json.getString("apkUrl")
            require(url.startsWith("https://"))
            require(sha.matches(Regex("[0-9a-fA-F]{64}")))
            return ReleaseInfo(
                versionCode = json.getInt("versionCode").also { require(it > 0) },
                versionName = json.getString("versionName").also { require(it.isNotBlank()) },
                notes = json.optString("notes", "稳定性与体验改进"),
                apkUrl = url,
                sha256 = sha,
                sizeBytes = json.getLong("sizeBytes").also { require(it in 1..MAX_APK_BYTES) },
            )
        }

        private const val MAX_APK_BYTES = 512L * 1024L * 1024L
    }
}
