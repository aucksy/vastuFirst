// PlanImage.kt — the photograph itself, in the only two channels the wall snap reads.
//
// ⭐ WHY THIS EXISTS. Everything the scan pipeline knew about a plan came from the model's reply: a
// list of rooms with rectangles it GUESSED. The picture those rectangles were guessed from was
// carried along only to be shown. But the picture is where the walls are, and a wall is the one
// thing on a plan that says exactly where a room ends. WallSnap reads it, and this is the form it
// reads it in — brightness and colourfulness per pixel, nothing else — so that the reading is pure
// Kotlin (no Android, no codec) and can be tested with a drawing made in code.
//
// Built in `:app` from a Bitmap, in a test from ImageIO, in the eval tools from the same bytes; the
// arithmetic downstream is identical for all three.
package com.vastufirst.shared.scan

/**
 * A decoded picture as two byte planes, row-major, top-left origin.
 *
 * [luma] is brightness 0..255 (0.299 R + 0.587 G + 0.114 B, integer). [saturation] is max(R,G,B) −
 * min(R,G,B), 0..255 — zero for every grey, high for a wooden table or a green plant. Walls on a
 * plan are grey; furniture in a render is not, and that difference is load-bearing in WallSnap.
 *
 * Bytes are read back unsigned via [lumaAt] / [saturationAt]; the arrays themselves are exposed for
 * the one hot loop that must not pay a call per pixel.
 */
class PlanImage(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val saturation: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "a picture has to have a size: ${width}x$height" }
        require(luma.size == width * height && saturation.size == width * height) {
            "planes must be width*height bytes: ${luma.size}/${saturation.size} for ${width}x$height"
        }
    }

    fun lumaAt(x: Int, y: Int): Int = luma[y * width + x].toInt() and 0xFF
    fun saturationAt(x: Int, y: Int): Int = saturation[y * width + x].toInt() and 0xFF

    companion object {
        /**
         * From packed ARGB pixels (Android's `Bitmap.getPixels`, Java's `BufferedImage.getRGB`).
         * Alpha is ignored: a transparent pixel over the paper is paper.
         */
        fun fromArgb(width: Int, height: Int, argb: IntArray): PlanImage {
            require(argb.size >= width * height) { "need ${width * height} pixels, got ${argb.size}" }
            val luma = ByteArray(width * height)
            val sat = ByteArray(width * height)
            for (i in 0 until width * height) {
                val p = argb[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                luma[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
                sat[i] = (maxOf(r, g, b) - minOf(r, g, b)).toByte()
            }
            return PlanImage(width, height, luma, sat)
        }
    }
}
