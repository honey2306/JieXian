package com.jiexian.app.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jiexian.app.domain.AccessibilitySettings
import com.jiexian.app.service.LimitAccessibilityService
import com.jiexian.app.service.ProtectionState

object PermissionUtils {
    fun isAccessibilityEnabled(context: Context): Boolean {
        if (ProtectionState.serviceRunning.value) return true
        return isAccessibilityEnabledInSettings(context)
    }

    /** Whether the system still lists the guardian service as enabled, regardless of liveness. */
    fun isAccessibilityEnabledInSettings(context: Context): Boolean {
        val enabled =
            runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            }.getOrNull()
        return AccessibilitySettings.isServiceListed(
            value = enabled,
            packageName = context.packageName,
            serviceClass = LimitAccessibilityService::class.java.name,
        )
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val runtimeGranted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Whether the guard may draw its fallback overlay above other apps. */
    fun canDrawOverlays(context: Context): Boolean = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Asks the system to stop battery-optimizing this app. Falls back to the global list. */
    fun batteryOptimizationIntents(context: Context): List<Intent> =
        listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * Opens the vendor auto-start manager when the OEM ships one. Returns false when none of
     * the known activities exist, so the caller can fall back to the app details screen.
     */
    fun openAutoStartSettings(context: Context): Boolean =
        AUTO_START_COMPONENTS.any { component ->
            runCatching {
                context.startActivity(
                    Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private val AUTO_START_COMPONENTS =
        listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
        )
}
