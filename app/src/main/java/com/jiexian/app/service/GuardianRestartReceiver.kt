package com.jiexian.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jiexian.app.data.RuleRepository
import com.jiexian.app.enforce.ShizukuEnforcement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GuardianRestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            GuardianScheduler.ACTION_WATCHDOG,
            ACTION_UNLOCK_CHECK,
            -> {
                GuardianLog.record(context, "自愈唤醒：${intent.action}")
                GuardianScheduler.start(context)
                syncEnforcement(context)
            }
        }
    }

    /** Releases expired suspensions and restores the grant after a reboot, without the UI. */
    private fun syncEnforcement(context: Context) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ShizukuEnforcement.sync(context, RuleRepository.get(context))
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNLOCK_CHECK = "com.jiexian.app.action.UNLOCK_CHECK"
    }
}
