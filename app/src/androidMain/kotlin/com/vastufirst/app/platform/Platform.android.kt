package com.vastufirst.app.platform

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.vastufirst.app.BuildConfig
import com.vastufirst.app.CrashLog
import com.vastufirst.app.ui.common.relativeUpdated
import com.vastufirst.app.ui.common.rememberDayClock
import com.vastufirst.app.ui.scan.clearPlanPhotos

/* Android's answers to the doorways in commonMain Platform.kt — the very code the screens used before. */

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun updatedLabel(updatedAt: Long, now: Long): String = relativeUpdated(updatedAt, now)

@Composable
actual fun rememberToday(): Long = rememberDayClock()

@Composable
actual fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

@Composable
actual fun rememberDeviceServices(): DeviceServices {
    val context = LocalContext.current
    return remember(context) {
        object : DeviceServices {
            override val bakedReaderKey: String = BuildConfig.PLAN_READER_KEY
            override fun lastCrash(): String? = CrashLog.lastCrash(context)
            override fun clearCrash() = CrashLog.clear(context)
            override fun clearPlanPhotos() = clearPlanPhotos(context)

            /**
             * ⚠ ACTION_SENDTO with a mailto: URI, never ACTION_SEND. SENDTO can only be answered by
             * something that actually handles email, so the chooser cannot offer to post the crash to a
             * social app. No mail app on the device is a real case on a cheap phone: the caller is told
             * whether anything opened and keeps the report if nothing did.
             */
            override fun sendCrashEmail(text: String): Boolean {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:contact@vastufirst.com")
                    putExtra(Intent.EXTRA_SUBJECT, "VastuFirst — something went wrong")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Anything you were doing when it happened (optional):" + LINE_BREAK + LINE_BREAK +
                            LINE_BREAK + "--- technical detail, nothing personal ---" + LINE_BREAK + text,
                    )
                }
                return runCatching { context.startActivity(intent) }.isSuccess
            }
        }
    }
}

/** A blank line in the email body, built from a character code so no editing pass can eat an escape. */
private val LINE_BREAK: String = 10.toChar().toString()
