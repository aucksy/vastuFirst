@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vastufirst.app.platform

import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

/**
 * The iPhone's crash note (iPhone plan, stage 4) — what Android's CrashLog does: when the app dies, write
 * what went wrong into the app's own storage; next time, Settings offers to email it, showing every word
 * before anything is sent. Same contents: app version, phone, and the technical trace — never a home.
 */
object IosCrashLog {
    private const val MAX_TRACE_CHARS = 8_000
    private var installed = false

    private val path: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
        NSFileManager.defaultManager.createDirectoryAtPath(base, true, null, null)
        "$base/last-crash.txt"
    }

    fun install(versionName: String) {
        if (installed) return
        installed = true
        val file = path
        setUnhandledExceptionHook { e ->
            runCatching {
                val device = UIDevice.currentDevice
                val text = buildString {
                    appendLine("VastuFirst $versionName")
                    appendLine("iOS ${device.systemVersion} · ${device.model}")
                    appendLine()
                    append(e.stackTraceToString().take(MAX_TRACE_CHARS))
                }
                @Suppress("CAST_NEVER_SUCCEEDS")
                (text as NSString).writeToFile(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            }
        }
    }

    fun last(): String? = runCatching {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) null
        else NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)?.ifBlank { null }
    }.getOrNull()

    fun clear() {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }
}
