package com.jiexian.app.enforce

/**
 * Runs inside the process Shizuku starts with the ADB shell identity.
 *
 * Shizuku instantiates this class itself and casts it to a binder, so it must extend the AIDL
 * stub directly instead of being an Android Service.
 */
class ShellUserService : IShellService.Stub() {
    override fun run(command: String): String =
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            stdout + stderr
        }.getOrDefault("")
}
