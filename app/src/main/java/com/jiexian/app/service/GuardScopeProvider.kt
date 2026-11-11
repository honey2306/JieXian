package com.jiexian.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import com.jiexian.app.domain.GuardScope

/**
 * Builds the set of packages an allow-only rule may send back to the home screen.
 * Only launchable apps are eligible; launchers, system UI, input methods and permission
 * dialogs are always left alone so the device stays usable.
 */
class GuardScopeProvider(
    context: Context,
) {
    private val context = context.applicationContext
    private var cachedAt = 0L
    private var cached = GuardScope(emptySet(), emptySet())

    fun scope(): GuardScope {
        val now = System.currentTimeMillis()
        if (now - cachedAt > REFRESH_INTERVAL_MILLIS) refresh()
        return cached
    }

    private fun refresh() {
        val packageManager = context.packageManager
        cached =
            GuardScope(
                launchablePackages = launchablePackages(packageManager),
                protectedPackages = protectedPackages(packageManager),
            )
        cachedAt = System.currentTimeMillis()
    }

    private fun launchablePackages(packageManager: PackageManager): Set<String> =
        packageManager
            .queryIntentActivities(launcherIntent(), PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

    private fun protectedPackages(packageManager: PackageManager): Set<String> =
        buildSet {
            add(context.packageName)
            addAll(ALWAYS_PROTECTED)
            runCatching {
                packageManager
                    .queryIntentActivities(homeIntent(), PackageManager.MATCH_ALL)
                    .mapNotNull { it.activityInfo?.packageName }
                    .forEach(::add)
            }
            runCatching {
                enabledInputMethodPackages().forEach(::add)
            }
        }

    private fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun enabledInputMethodPackages(): List<String> {
        val inputMethodManager = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return inputMethodManager.enabledInputMethodList.map { it.packageName }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 5 * 60_000L
        private val ALWAYS_PROTECTED =
            setOf(
                "android",
                "com.android.systemui",
                "com.android.settings",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                // Vendor settings / security centers stay reachable so permissions and
                // force-stop can always be managed from the system UI.
                "com.miui.securitycenter",
                "com.coloros.safecenter",
                "com.oplus.safecenter",
                "com.vivo.permissionmanager",
                "com.huawei.systemmanager",
            )
    }
}
