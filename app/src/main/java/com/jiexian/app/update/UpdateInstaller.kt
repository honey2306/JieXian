package com.jiexian.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateInstaller(
    private val context: Context,
) {
    private val repository = UpdateRepository(context)

    suspend fun install(release: ReleaseInfo) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "请先允许界限安装应用更新", Toast.LENGTH_LONG).show()
                val intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            return
        }
        val apk = repository.downloadAndVerify(release)
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("jiexian.apk", 0L, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val callback =
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            session.commit(callback.intentSender)
        } catch (error: Throwable) {
            session.abandon()
            throw error
        } finally {
            session.close()
        }
    }
}
