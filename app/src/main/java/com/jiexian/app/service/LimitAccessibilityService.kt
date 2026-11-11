package com.jiexian.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.jiexian.app.admin.DeviceOwnerEnforcement
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.RuleType
import com.jiexian.app.ui.BlockedActivity
import com.jiexian.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LimitAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: RuleRepository
    private lateinit var usageRepository: UsageRepository
    private lateinit var snapshotter: LockSnapshotter
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var updateChecker: UpdateChecker
    private lateinit var guardScopeProvider: GuardScopeProvider
    private val appLabelCache = mutableMapOf<String, String>()
    private var foregroundPackage: String? = null
    private var lastAccessibilityEventAt = 0L
    private var checkInFlight = false
    private var blockInFlight = false

    private val usageRecheck = Runnable { checkForeground(resolveFromUsage = true) }

    private val periodicCheck =
        object : Runnable {
            override fun run() {
                scope.launch {
                    GuardianHeartbeat.signal(this@LimitAccessibilityService)
                    snapshotter.snapshotAll()
                    runCatching {
                        DeviceOwnerEnforcement.sync(this@LimitAccessibilityService, repository)
                    }
                    runCatching { reminderScheduler.check() }
                    runCatching { updateChecker.check() }
                    checkForeground(resolveFromUsage = true)
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        initialize()
    }

    /** Also called after a rebind, which the system can do without a fresh process. */
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        initialize()
    }

    private fun initialize() {
        repository = RuleRepository.get(this)
        usageRepository = UsageRepository(this)
        snapshotter = LockSnapshotter(repository, usageRepository)
        reminderScheduler = ReminderScheduler(this, repository)
        updateChecker = UpdateChecker(this)
        guardScopeProvider = GuardScopeProvider(this)
        ProtectionState.setRunning(true)
        GuardianHeartbeat.signal(this)
        GuardianLog.record(this, "无障碍服务已连接")
        // The foreground service keeps this process alive when the task is swiped away.
        GuardService.sync(this)
        handler.removeCallbacks(periodicCheck)
        handler.post(periodicCheck)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        lastAccessibilityEventAt = System.currentTimeMillis()
        GuardianHeartbeat.signal(this)
        foregroundPackage = packageName.takeUnless { it == applicationContext.packageName }
        checkForeground()
        // Some launchers do not emit a new accessibility event when a task is resumed.
        // Recheck UsageStats after the transition and after the block debounce expires.
        handler.removeCallbacks(usageRecheck)
        handler.postDelayed(usageRecheck, 350L)
        handler.postDelayed(usageRecheck, 1_500L)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        GuardianLog.record(this, "无障碍服务被系统解绑")
        ProtectionState.setRunning(false)
        handler.removeCallbacksAndMessages(null)
        // Keep the guardian process around so the system can rebind the service.
        GuardService.sync(this)
        return true
    }

    override fun onDestroy() {
        ProtectionState.setRunning(false)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun checkForeground(resolveFromUsage: Boolean = false) {
        if (checkInFlight || blockInFlight) return
        checkInFlight = true
        scope.launch {
            try {
                if (resolveFromUsage) {
                    usageRepository.latestForegroundResume()?.let { resume ->
                        if (resume.atMillis >= lastAccessibilityEventAt) {
                            foregroundPackage = resume.packageName.takeUnless { it == applicationContext.packageName }
                        }
                    }
                }
                GuardianHeartbeat.signal(this@LimitAccessibilityService)
                val packageName = foregroundPackage ?: return@launch
                snapshotter.snapshotPackage(
                    packageName = packageName,
                    allowOnlyRules = activeAllowOnlyRules(packageName),
                )
                runCatching {
                    DeviceOwnerEnforcement.sync(this@LimitAccessibilityService, repository)
                }
                repository
                    .activeLocksForPackage(packageName, System.currentTimeMillis())
                    .maxByOrNull { it.unlockAt }
                    ?.let { block(it) }
            } finally {
                checkInFlight = false
            }
        }
    }

/**
     * Allow-only rules apply to everything the user could launch, so they are not part of the
     * target list of the source rule.
     * They are only passed for packages that are safe to restrict; the entries themselves
     * stay usable.
     */
    private suspend fun activeAllowOnlyRules(packageName: String) =
        repository
            .allowOnlyRules()
            .takeIf { rules ->
                rules.isNotEmpty() && guardScopeProvider.scope().canRestrict(packageName)
            }.orEmpty()

    private fun block(lock: ActiveLock) {
        if (blockInFlight) return
        blockInFlight = true
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            val displayLock =
                if (lock.type == RuleType.ALLOW_ONLY) {
                    lock.copy(appName = appLabel(lock.packageName))
                } else {
                    lock
                }
            val intent =
                BlockedActivity.createIntent(this, displayLock).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            runCatching { startActivity(intent) }
            foregroundPackage = null
            handler.postDelayed({ blockInFlight = false }, 1_000)
        }, 180)
    }

    private fun appLabel(packageName: String): String =
        appLabelCache.getOrPut(packageName) {
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName)
        }

    companion object {
        private const val CHECK_INTERVAL_MS = 5_000L
    }
}
