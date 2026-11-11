package com.jiexian.app.enforce

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.jiexian.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Runs shell commands through a Shizuku user service.
 *
 * Shizuku starts that service with the ADB shell identity, so commands inside it have privileges
 * this app lacks (suspending packages, writing accessibility settings). The service lives only as
 * long as this app does; each enforcement pass binds it on demand.
 */
object ShizukuShell {
    private const val TIMEOUT_MILLIS = 10_000L
    private const val SERVICE_VERSION = 2

    private val mutex = Mutex()
    private val lock = Any()
    private var service: IShellService? = null

    suspend fun run(
        context: Context,
        command: String,
    ): String? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val shell = ensureService(context) ?: return@withLock null
                runCatching { shell.run(command) }.getOrNull()
            }
        }

    private suspend fun ensureService(context: Context): IShellService? {
        synchronized(lock) { service }?.let { return it }
        val deferred = CompletableDeferred<IShellService?>()
        val pendingArgs = buildArgs(context)
        val pendingConnection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val shell = IShellService.Stub.asInterface(binder)
                    synchronized(lock) { service = shell }
                    deferred.complete(shell)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    synchronized(lock) { service = null }
                }
            }
        val bound =
            runCatching { Shizuku.bindUserService(pendingArgs, pendingConnection) }
                .isSuccess
        if (!bound) {
            deferred.complete(null)
            return null
        }
        val shell = withTimeoutOrNull(TIMEOUT_MILLIS) { deferred.await() }
        if (shell == null) {
            runCatching { Shizuku.unbindUserService(pendingArgs, pendingConnection, true) }
        }
        return shell
    }

    private fun buildArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku
            .UserServiceArgs(ComponentName(context, ShellUserService::class.java))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(SERVICE_VERSION)

    /** Called when the Shizuku service dies; the next command binds again. */
    fun reset() {
        synchronized(lock) { service = null }
    }
}
