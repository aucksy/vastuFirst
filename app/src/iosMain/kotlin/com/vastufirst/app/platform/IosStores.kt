@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vastufirst.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ReaderChoice
import com.vastufirst.data.PlanPhotoStore
import com.vastufirst.designsystem.foundation.VastuHaptics
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import platform.posix.memcpy

/*
 * The iPhone's own storage and touches (iPhone plan, stage 3). Same keys and same meanings as the
 * Android versions, so a person moving between phones meets the same app.
 */

/** "May we read your plan?" — the same versioned key Android uses (AndroidPlanReadingConsent). */
object UserDefaultsPlanReadingConsent : PlanReadingConsent {
    private const val KEY = "plan_reading_consent_v2"
    override fun isGranted(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(KEY)
    override fun set(granted: Boolean) = NSUserDefaults.standardUserDefaults.setBool(granted, KEY)
}

/** Which reader Settings chose (testing lever), or null for the default. */
object UserDefaultsReaderChoice : ReaderChoice {
    private const val KEY = "reader_model"
    override fun chosen(): String? = NSUserDefaults.standardUserDefaults.stringForKey(KEY)
    override fun set(model: String?) {
        val d = NSUserDefaults.standardUserDefaults
        if (model == null) d.removeObjectForKey(KEY) else d.setObject(model, KEY)
    }
}

/**
 * Plan photos of unfinished homes, in the app's own Application Support folder.
 *
 * ⭐ MARKED "DO NOT BACK UP". The privacy policy says homes and plan pictures stay on this phone; on
 * Android, backups are off for that reason (v0.37.0). On the iPhone the same promise needs this flag,
 * or iCloud would copy the folder.
 */
object IosPlanPhotoStore : PlanPhotoStore {
    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
        val path = "$base/draft-photos"
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
        NSURL.fileURLWithPath(path).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
        path
    }

    private fun file(draftId: String) = "$dir/${draftId.filter { it.isLetterOrDigit() || it == '-' }}.jpg"

    override suspend fun save(draftId: String, jpeg: ByteArray) {
        val data = jpeg.usePinned { NSData.create(bytes = it.addressOf(0), length = jpeg.size.toULong()) }
        data.writeToFile(file(draftId), true)
    }

    override suspend fun load(draftId: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(file(draftId)) ?: return null
        val size = data.length.toInt()
        if (size == 0) return null
        return ByteArray(size).also { out -> out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }

    override suspend fun delete(draftId: String) {
        NSFileManager.defaultManager.removeItemAtPath(file(draftId), null)
    }

    override suspend fun deleteAll() {
        val fm = NSFileManager.defaultManager
        (fm.contentsOfDirectoryAtPath(dir, null) ?: emptyList<Any>()).forEach { name ->
            fm.removeItemAtPath("$dir/$name", null)
        }
    }
}

/** The design kit's touches (buttons, dial detents) from Apple's feedback generators. */
object IosVastuHaptics : VastuHaptics {
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val selection = UISelectionFeedbackGenerator()
    private val notification = UINotificationFeedbackGenerator()
    override fun tap() = light.impactOccurred()
    override fun tick() = selection.selectionChanged()
    override fun confirm() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    override fun reject() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
}

/** The decimal mark the phone's own region uses ("4.7" or "4,7") — numbers follow the phone, words stay English. */
@Composable
fun iosDecimalMark(): Char = remember {
    NSNumberFormatter().apply { locale = NSLocale.currentLocale }.decimalSeparator.firstOrNull() ?: '.'
}
