package com.vastufirst.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picked file → the bytes we send. Images via `BitmapFactory`, PDFs via `PdfRenderer` — which has
 * been **in the platform since API 21**, so a PDF costs us no dependency at all against the 30 MB
 * APK budget (NFR §10).
 *
 * ⭐ **1400 px and JPEG quality 88 are not arbitrary.** They are exactly what
 * `tools/scan-eval/batch-real.py` fed the model when the 30 real plans were measured, so the app
 * sends the model the same thing the measurements were taken on. Changing either number invalidates
 * the accuracy figures the whole feature is designed around.
 *
 * Downscaling is a **cost** control before it is a bandwidth one: a scan is billed per token and the
 * image is tokenised into the same stream, so the price scales with resolution. At this size a scan
 * measured 2 061 prompt tokens ≈ ₹0.15.
 *
 * Never throws. A file that cannot be opened comes back `null`, which the screen shows as "we
 * couldn't open that file" with something to do about it.
 */
class AndroidImageDecoder(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImageDecoder {

    override suspend fun toJpeg(source: Any): DecodedImage? = withContext(io) {
        val uri = source as? Uri ?: return@withContext null
        runCatching {
            val bitmap = if (isPdf(uri)) firstPdfPage(uri) else decodeImage(uri)
            bitmap?.let { encode(it) }
        }.getOrNull()
    }

    private fun isPdf(uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" ||
            uri.toString().endsWith(".pdf", ignoreCase = true)

    /** Page 1 only: a Vastu score is for one floor, and the user picks which page by cropping. */
    private fun firstPdfPage(uri: Uri): Bitmap? {
        val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return fd.use { descriptor ->
            PdfRenderer(descriptor).use renderer@{ renderer ->
                if (renderer.pageCount == 0) return@renderer null
                renderer.openPage(0).use { page ->
                    val scale = MAX_EDGE.toFloat() / maxOf(page.width, page.height).toFloat()
                    val w = (page.width * minOf(scale, 1f)).toInt().coerceAtLeast(1)
                    val h = (page.height * minOf(scale, 1f)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // A PDF page renders with a transparent background; flattened to JPEG that goes
                    // BLACK and takes every wall line with it. Paint the paper in first.
                    android.graphics.Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }

    private fun decodeImage(uri: Uri): Bitmap? {
        // Two passes: measure with inJustDecodeBounds so a 12 MP photo is never fully decoded into
        // memory just to be shrunk (that is an OOM on a low-end phone, and min SDK here is 26).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_EDGE) return decoded
        val s = MAX_EDGE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * s).toInt().coerceAtLeast(1),
            (decoded.height * s).toInt().coerceAtLeast(1),
            /* filter = */ true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun encode(bitmap: Bitmap): DecodedImage {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val aspect = if (bitmap.height > 0) bitmap.width.toDouble() / bitmap.height.toDouble() else null
        bitmap.recycle()
        return DecodedImage(out.toByteArray(), aspect)
    }

    internal companion object {
        /** Matches `tools/scan-eval/batch-real.py`. Do not change without re-measuring accuracy. */
        const val MAX_EDGE = 1400
        const val JPEG_QUALITY = 88

        /** Largest power-of-two subsample that still leaves the image at or above [MAX_EDGE]. */
        fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= MAX_EDGE) {
                longest /= 2
                sample *= 2
            }
            return sample
        }
    }
}
