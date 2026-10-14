package com.vastufirst.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/*
 * ⭐ THE DOORWAYS (iPhone plan, stage 2, 14 Oct 2026). The screens are shared by both phones; these
 * are the few jobs each phone does its own way. androidMain answers them exactly as the app always
 * has (so Android does not change by a pixel); iosMain answers them for the iPhone.
 */

/** Milliseconds since 1970, from the phone's clock. */
expect fun nowMillis(): Long

/** "today", "yesterday", "3 days ago", "8 Jul" — when a home was last changed, in the phone's time zone. */
expect fun updatedLabel(updatedAt: Long, now: Long): String

/** A calendar date in the phone's time zone, English month: "14 Oct 2026". */
expect fun dateLabel(millis: Long): String

/** The current time, recomposing once when the local day changes (so "today" becomes "yesterday"). */
@Composable
expect fun rememberToday(): Long

/** The window's height in dp — how tall the editor's plan may grow before the buttons fall off. */
@Composable
expect fun screenHeightDp(): Int

/** Run [onBack] instead of leaving the screen when the person goes back (Back key, gesture or swipe). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** A JPEG or PNG, decoded for drawing — null if it will not decode. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** What Settings needs from the phone itself. */
interface DeviceServices {
    /** The plan-reading key baked into this build ("" when none, or when reads go through the relay). */
    val bakedReaderKey: String

    /** The crash the app recorded last time it ran, or null. */
    fun lastCrash(): String?

    fun clearCrash()

    /** Hand [text] to the phone's own mail app. True only when a mail app actually opened. */
    fun sendCrashEmail(text: String): Boolean

    /** Delete every plan photo the app keeps on the phone. */
    fun clearPlanPhotos()
}

@Composable
expect fun rememberDeviceServices(): DeviceServices
