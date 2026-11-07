package com.jiexian.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
) {
    suspend fun launchableApps(): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            activities
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    if (packageName == context.packageName) return@mapNotNull null
                    InstalledApp(
                        packageName = packageName,
                        name = resolveInfo.loadLabel(packageManager).toString(),
                    )
                }.distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }
        }
}
