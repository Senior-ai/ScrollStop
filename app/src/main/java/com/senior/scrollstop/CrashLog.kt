package com.senior.scrollstop

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last crash on the phone so the next launch can show it. ScrollStop has no internet
 * access, so this is the only way a crash report can reach anyone: the user shares it by hand.
 */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    buildString {
                        appendLine("ScrollStop ${versionName(app)} crashed on thread '${thread.name}'")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine()
                        append(error.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
