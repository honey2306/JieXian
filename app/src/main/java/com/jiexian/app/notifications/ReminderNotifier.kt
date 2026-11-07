package com.jiexian.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jiexian.app.MainActivity
import com.jiexian.app.R
import com.jiexian.app.domain.DueReminder
import com.jiexian.app.domain.ReminderKind
import com.jiexian.app.service.ReminderSender

class ReminderNotifier(
    private val context: Context,
) : ReminderSender {
    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannel()
    }

    override fun send(reminder: DueReminder): Boolean {
        if (!canNotify()) return false
        val openApp =
            PendingIntent.getActivity(
                context,
                reminder.key.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(0xFFDAE7CC.toInt())
                .setContentTitle(reminder.title)
                .setContentText(reminder.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(
                    if (reminder.kind ==
                        ReminderKind.SCREEN_TIME
                    ) {
                        NotificationCompat.CATEGORY_STATUS
                    } else {
                        NotificationCompat.CATEGORY_REMINDER
                    },
                ).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        return try {
            manager.notify(notificationId(reminder), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val systemManager = context.getSystemService(NotificationManager::class.java)
            val channel = systemManager.getNotificationChannel(CHANNEL_ID) ?: return false
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel =
            NotificationChannel(CHANNEL_ID, "使用提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "应用额度、禁用时段和今日亮屏时长提醒"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "usage_reminders"

        private fun notificationId(reminder: DueReminder): Int =
            (reminder.kind.ordinal + 1) * 100_000_000 + (reminder.key.hashCode() and 0x0FFF_FFFF)
    }
}
