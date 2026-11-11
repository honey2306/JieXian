package com.jiexian.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GuardianRestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            GuardianScheduler.ACTION_WATCHDOG,
            -> GuardianScheduler.start(context)
        }
    }
}
