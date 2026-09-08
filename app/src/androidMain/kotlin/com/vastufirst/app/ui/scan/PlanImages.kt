// PlanImages.kt — the photograph as WallSnap reads it: brightness and colourfulness per pixel.
//
// The one Android-specific step in the wall snap. Everything that reads the planes is pure Kotlin
// in :shared; this only turns the JPEG the reader was sent into those planes, so the walls that
// correct the reader's rectangles are read from EXACTLY the picture the reader saw.
package com.vastufirst.app.ui.scan

import android.graphics.BitmapFactory
import com.vastufirst.shared.scan.PlanImage

/** The decoded picture, or null when the bytes do not decode — then the reply is used as read. */
fun DecodedImage.toPlanImage(): PlanImage? {
    val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
    return try {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        PlanImage.fromArgb(w, h, pixels)
    } catch (t: Throwable) {
        null
    } finally {
        bitmap.recycle()
    }
}
