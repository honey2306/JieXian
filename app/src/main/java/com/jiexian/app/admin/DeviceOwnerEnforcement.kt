package com.jiexian.app.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import java.util.Collections

/**
 * System-enforced locking, available when the app is the device owner.
 *
 * A pure accessibility guard can always be bypassed by stopping the app; suspending the
 * locked package asks the platform to refuse launching it, so the limit keeps holding even
 * when the guardian process or its accessibility grant is gone.
 */
object DeviceOwnerEnforcement {
    private const val PREFERENCES_NAME = "device_owner"
    private const val KEY_SUSPENDED = "suspended_packages"

    fun isDeviceOwner(context: Context): Boolean =
        runCatching {
            context
                .getSystemService(DevicePolicyManager::class.java)
                ?.isDeviceOwnerApp(context.packageName) == true
        }.getOrDefault(false)

    suspend fun sync(
        context: Context,
        repository: RuleRepository,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!isDeviceOwner(context)) return
        val locks = repository.locksNow(now)
        apply(context, suspendedPackages(locks))
    }

    /**
     * Packages the platform should refuse to launch.
     *
     * Allow-only rules are left out on purpose: their lock set is "everything but the allowed
     * apps", which is decided per foreground event, not from a stored target list.
     */
    fun suspendedPackages(
        locks: List<ActiveLock>,
        ownPackage: String? = null,
    ): Set<String> =
        locks
            .asSequence()
            .filter { it.type != RuleType.ALLOW_ONLY }
            .map { it.packageName }
            .filter { it.isNotBlank() && it != ownPackage }
            .toSet()

    fun apply(
        context: Context,
        desired: Set<String>,
    ) {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val admin = ComponentName(context, JieXianDeviceAdminReceiver::class.java)
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val current: Set<String> =
            preferences.getStringSet(KEY_SUSPENDED, emptySet())?.toSet() ?: emptySet()
        val toSuspend = desired - current
        val toUnsuspend = current - desired
        runCatching {
            if (toSuspend.isNotEmpty()) {
                manager.setPackagesSuspended(admin, toSuspend.toTypedArray(), true)
            }
            if (toUnsuspend.isNotEmpty()) {
                manager.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false)
            }
        }
        preferences
            .edit()
            .putStringSet(KEY_SUSPENDED, Collections.unmodifiableSet(desired))
            .apply()
    }
}
