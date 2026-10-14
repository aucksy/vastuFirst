package com.vastufirst.app.ui.scan

import com.vastufirst.shared.scan.PlanImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * The plan picture's pixels on the iPhone, decoded by Skia (what Compose draws with there), packed as
 * ARGB ints exactly like Android's Bitmap.getPixels — so the wall snap sees the same numbers.
 */
actual fun DecodedImage.toPlanImage(): PlanImage? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return null
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    if (!bitmap.allocPixels(info)) return null
    if (!image.readPixels(bitmap, 0, 0)) return null
    val raw = bitmap.readPixels(info, w * 4, 0, 0) ?: return null
    val pixels = IntArray(w * h) { i ->
        val o = i * 4
        val b = raw[o].toInt() and 0xFF
        val g = raw[o + 1].toInt() and 0xFF
        val r = raw[o + 2].toInt() and 0xFF
        val a = raw[o + 3].toInt() and 0xFF
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    PlanImage.fromArgb(w, h, pixels)
}.getOrNull()
