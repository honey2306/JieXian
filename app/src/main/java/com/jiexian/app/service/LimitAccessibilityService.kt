package com.jiexian.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
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
    private var foregroundPackage: String? = null
    private var lastAccessibilityEventAt = 0L
    private var checkInFlight = false
    private var blockInFlight = false

    private val usageRecheck = Runnable { checkForeground(resolveFromUsage = true) }

    private val periodicCheck =
        object : Runnable {
            override fun run() {
                scope.launch {
                    snapshotter.snapshotAll()
                    runCatching { reminderScheduler.check() }
                    runCatching { updateChecker.check() }
                    checkForeground(resolveFromUsage = true)
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = RuleRepository.get(this)
        usageRepository = UsageRepository(this)
        snapshotter = LockSnapshotter(repository, usageRepository)
        reminderScheduler = ReminderScheduler(this, repository)
        updateChecker = UpdateChecker(this)
        ProtectionState.setRunning(true)
        handler.post(periodicCheck)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        lastAccessibilityEventAt = System.currentTimeMillis()
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
        ProtectionState.setRunning(false)
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
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
                val packageName = foregroundPackage ?: return@launch
                snapshotter.snapshotPackage(packageName)
                repository
                    .activeLocksForPackage(packageName, System.currentTimeMillis())
                    .maxByOrNull { it.unlockAt }
                    ?.let { block(it) }
            } finally {
                checkInFlight = false
            }
        }
    }

    private fun block(lock: com.jiexian.app.model.ActiveLock) {
        if (blockInFlight) return
        blockInFlight = true
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            val intent =
                BlockedActivity.createIntent(this, lock).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            runCatching { startActivity(intent) }
            foregroundPackage = null
            handler.postDelayed({ blockInFlight = false }, 1_000)
        }, 180)
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5_000L
    }
}
