@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vastufirst.app.update

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** The iPhone's [RulesStore]: one file in Application Support, marked do-not-back-up. */
object IosRulesStore : RulesStore() {
    private val path: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
        NSFileManager.defaultManager.createDirectoryAtPath(base, true, null, null)
        NSURL.fileURLWithPath(base).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
        "$base/published-rules.json"
    }

    override fun readText(): String? =
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) else null

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun writeAtomically(body: String): Boolean =
        (body as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)

    override fun deleteText(): Boolean {
        val fm = NSFileManager.defaultManager
        return !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, null)
    }
}
