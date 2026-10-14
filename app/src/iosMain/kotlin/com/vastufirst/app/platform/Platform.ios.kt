package com.vastufirst.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication

/* The iPhone's answers to the doorways in commonMain Platform.kt (iPhone plan, stage 2). */

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/** Same words as Android's relativeUpdated: today, yesterday, N days ago, then "8 Jul" / "20 Dec 2025". */
actual fun updatedLabel(updatedAt: Long, now: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(updatedAt).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val days = then.daysUntil(today)
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "$days days ago"
        then.year == today.year -> "${then.dayOfMonth} ${MONTHS[then.monthNumber - 1]}"
        else -> "${then.dayOfMonth} ${MONTHS[then.monthNumber - 1]} ${then.year}"
    }
}

/** English month names, fixed: the app is English only, permanently. */
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
actual fun rememberToday(): Long {
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val t = nowMillis()
            if (updatedLabel(now, t) != "today") now = t
        }
    }
    return now
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenHeightDp(): Int {
    val px = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { px.toDp().value.toInt() }
}

/**
 * ⚠ Not wired yet on the iPhone (stage 3). The navigation's own swipe-back still leaves the screen;
 * what this skips is the one custom step VastuNav hangs on it (cancelling a North change opened from
 * the report). Stage 3 connects it to the iPhone's back gesture.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

@Composable
actual fun rememberDeviceServices(): DeviceServices = remember { IosDeviceServices }

private object IosDeviceServices : DeviceServices {
    // The iPhone build never carries a key: its reads go through our relay (reader-config useRelay).
    override val bakedReaderKey: String = ""

    override fun lastCrash(): String? = IosCrashLog.last()
    override fun clearCrash() = IosCrashLog.clear()

    // The iPhone keeps no camera files of its own (the camera hands back a picture in memory); the
    // photos of unfinished homes are removed with the homes, by the repository's own deleteAll.
    override fun clearPlanPhotos() = Unit

    override fun sendCrashEmail(text: String): Boolean {
        val subject = encode("VastuFirst — something went wrong") ?: return false
        val body = encode(text) ?: return false
        val url = NSURL.URLWithString("mailto:contact@vastufirst.com?subject=$subject&body=$body") ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return false
        app.openURL(url, emptyMap<Any?, Any>(), null)
        return true
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun encode(s: String): String? =
        (s as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet)
}
