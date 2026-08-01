package com.vastufirst.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * ⭐ CRASH REPORTING, WITHOUT A CRASH-REPORTING SERVICE.
 *
 * A paid app needs to know when it breaks on somebody's phone. The usual answer is Crashlytics or
 * Sentry — both of which mean an account the owner does not have yet, a third party receiving data
 * from every user, and a privacy-policy line saying so. None of that can happen before Tuesday, and
 * shipping with no visibility at all is worse.
 *
 * So: the app catches its own crash, writes it to its own private storage, and the NEXT launch
 * offers to send it — by email, from the user's own mail app, with the text visible before they hit
 * send. Nothing is transmitted without a person choosing to. It costs nothing, needs no account, and
 * is more honest than a silent background upload.
 *
 * ⚠ WHAT IS DELIBERATELY NOT IN THE REPORT: no home, no room, no score, no name. Only the technical
 * trace, the app version and the phone model. There is nothing in a stack trace that identifies a
 * person, and this file is the reason that stays true — everything the app knows about the user
 * lives in the database, and the database is never read here.
 *
 * ⚠ THE HANDLER MUST NEVER SWALLOW THE CRASH. It records, then hands the exception to whatever
 * handler was already installed — otherwise the app hangs on a dead screen instead of restarting,
 * which is a worse experience than the crash.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_TRACE_CHARS = 8_000

    /** Install the recorder. Safe to call twice; the second call is a no-op. */
    fun install(context: Context, versionName: String, versionCode: Int) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Recorder) return
        Thread.setDefaultUncaughtExceptionHandler(Recorder(app, versionName, versionCode, previous))
    }

    /** The last recorded crash, or null. Read once on launch to decide whether to offer the report. */
    fun lastCrash(context: Context): String? {
        val f = file(context)
        return runCatching { if (f.exists()) f.readText().ifBlank { null } else null }.getOrNull()
    }

    /** Forget it — after the user sends it, or says no. Never keep asking about the same crash. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private class Recorder(
        private val context: Context,
        private val versionName: String,
        private val versionCode: Int,
        private val next: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                File(context.filesDir, FILE_NAME).writeText(
                    buildString {
                        appendLine("VastuFirst $versionName ($versionCode)")
                        appendLine("Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine("Thread: ${t.name}")
                        appendLine()
                        append(trace.take(MAX_TRACE_CHARS))
                    },
                )
            }
            // ⚠ Always hand it on. Eating the exception here leaves the process alive with a dead
            // window — a frozen app is harder to explain than one that closes.
            next?.uncaughtException(t, e) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
