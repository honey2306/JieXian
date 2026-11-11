package com.jiexian.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jiexian.app.MainActivity
import com.jiexian.app.R
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.data.UsageRepository
import com.jiexian.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the guardian process alive.
 *
 * The accessibility service is bound by the system, but several launchers/vendors kill the
 * whole app when its task is swiped away. A low-priority foreground service keeps the process
 * (and the accessibility binding) alive after the task is removed, and the ongoing notification
 * makes the guardian state visible.
 */
class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { NotificationManagerCompat.from(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val overlayGuard by lazy {
        val repository = RuleRepository.get(this)
        val usage = UsageRepository(this)
        OverlayGuard(
            context = this,
            repository = repository,
            usageRepository = usage,
            snapshotter = LockSnapshotter(repository, usage),
        )
    }

    private val overlayPulse =
        object : Runnable {
            override fun run() {
                // Runs only while a fallback is possible; the accessibility path blocks faster.
                if (!PermissionUtils.isAccessibilityEnabledInSettings(this@GuardService) &&
                    PermissionUtils.canDrawOverlays(this@GuardService)
                ) {
                    val accessibilityRunning = ProtectionState.serviceRunning.value
                    scope.launch {
                        runCatching { overlayGuard.tick(accessibilityRunning = accessibilityRunning) }
                    }
                } else {
                    overlayGuard.hide()
                }
                handler.postDelayed(this, OVERLAY_INTERVAL_MILLIS)
            }
        }

    private val statusPulse =
        object : Runnable {
            override fun run() {
                if (!guardPossible()) {
                    stopSelf()
                    return
                }
                updateNotification()
                handler.postDelayed(this, STATUS_INTERVAL_MILLIS)
            }
        }

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        GuardianLog.record(this, "常驻守护启动")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startInForeground()
        if (!guardPossible()) {
            stopSelf()
            return START_NOT_STICKY
        }
        GuardianScheduler.ensureWatchdog(this)
        handler.removeCallbacks(statusPulse)
        handler.post(statusPulse)
        handler.removeCallbacks(overlayPulse)
        handler.post(overlayPulse)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Some launchers stop the whole app when the task is swiped away. Re-arm the watchdog
        // so the process comes back even if the system ignores START_STICKY.
        GuardianLog.record(this, "任务被划掉，守护仍在")
        GuardianScheduler.ensureWatchdog(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        overlayGuard.hide()
        scope.cancel()
        GuardianLog.record(this, "常驻守护停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val type =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type) }
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification() =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setColor(MOONLIGHT)
            .setContentTitle(
                when {
                    accessibleAlive() -> "界限 · 守护运行中"
                    PermissionUtils.canDrawOverlays(this) -> "界限 · 悬浮窗拦截中"
                    else -> "界限 · 守护已停止"
                },
            ).setContentText(
                when {
                    accessibleAlive() -> "应用切换时检查额度与时段，退出多任务也不会中断"
                    PermissionUtils.canDrawOverlays(this) -> "无障碍被系统停止，正在用悬浮窗继续拦截"
                    else -> "无障碍服务被系统停止，点此重新开启"
                },
            ).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .apply {
                if (!accessibleAlive()) addAction(0, "重新开启", accessibilitySettingsIntent())
            }.build()

    /** The guard can do something with accessibility, or at least with the overlay fallback. */
    private fun guardPossible(): Boolean =
        PermissionUtils.isAccessibilityEnabledInSettings(this) || PermissionUtils.canDrawOverlays(this)

    private fun accessibleAlive(): Boolean = GuardianHeartbeat.isAlive(this)

    private fun alive(): Boolean = accessibleAlive() || PermissionUtils.canDrawOverlays(this)

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun accessibilitySettingsIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            NOTIFICATION_ID + 1,
            PermissionUtils.accessibilitySettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "守护状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻通知，用于让守护在退出多任务后继续运行"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "guardian_status"
        private const val NOTIFICATION_ID = 4201
        private const val STATUS_INTERVAL_MILLIS = 30_000L
        private const val OVERLAY_INTERVAL_MILLIS = 1_500L
        private const val MOONLIGHT = 0xFFDAE7CC.toInt()

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        /** Starts or stops the foreground guardian to match the accessibility toggle. */
        fun sync(context: Context) = GuardianScheduler.start(context)
    }
}
