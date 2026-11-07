package com.jiexian.app.update

import android.content.Context
import com.jiexian.app.BuildConfig

class UpdateChecker(
    private val context: Context,
) {
    private val repository = UpdateRepository(context)
    private val notifier = UpdateNotifier(context)
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun cachedUpdate(): ReleaseInfo? =
        preferences.all.let { values ->
            val code = values["version_code"] as? Int ?: return@let null
            if (code <= BuildConfig.VERSION_CODE) return@let null
            ReleaseInfo(
                versionCode = code,
                versionName = values["version_name"] as? String ?: return@let null,
                notes = values["notes"] as? String ?: "",
                apkUrl = values["apk_url"] as? String ?: return@let null,
                sha256 = values["sha256"] as? String ?: return@let null,
                sizeBytes = values["size_bytes"] as? Long ?: return@let null,
            )
        }

    suspend fun check(force: Boolean = false): ReleaseInfo? {
        if (!force && System.currentTimeMillis() - preferences.getLong("checked_at", 0L) < CHECK_INTERVAL) {
            return cachedUpdate()
        }
        preferences.edit().putLong("checked_at", System.currentTimeMillis()).apply()
        val release = repository.fetchLatest()
        preferences
            .edit()
            .putInt("version_code", release.versionCode)
            .putString("version_name", release.versionName)
            .putString("notes", release.notes)
            .putString("apk_url", release.apkUrl)
            .putString("sha256", release.sha256)
            .putLong("size_bytes", release.sizeBytes)
            .apply()
        val update = release.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        if (update != null && preferences.getInt("notified_version", 0) != update.versionCode) {
            notifier.show(update)
            preferences.edit().putInt("notified_version", update.versionCode).apply()
        }
        return update
    }

    companion object {
        private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L
    }
}
