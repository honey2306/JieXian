package com.jiexian.app.update

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

class UpdateNotifier(
    private val context: Context,
) {
    init {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "GitHub 新版本通知"
                    setShowBadge(false)
                },
            )
        }
    }

    fun show(release: ReleaseInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent =
            PendingIntent.getActivity(
                context,
                9200,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(0xFFDAE7CC.toInt())
                .setContentTitle("界限 ${release.versionName} 已就绪")
                .setContentText("打开应用，确认后下载并安装新版本")
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(9200, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify.
        }
    }

    companion object {
        private const val CHANNEL_ID = "app_updates"
    }
}
