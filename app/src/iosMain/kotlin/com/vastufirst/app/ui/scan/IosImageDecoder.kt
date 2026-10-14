@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vastufirst.app.ui.scan

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPDFDocumentCreateWithURL
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGPDFMediaBox
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.posix.memcpy

/**
 * The iPhone's half of "shrink the picture to 1,400 px, JPEG quality 88" (iPhone plan, stage 4).
 *
 * ⚠ THE NUMBERS ARE ANDROID'S, ON PURPOSE (AndroidImageDecoder: MAX_EDGE, JPEG_QUALITY). The reader's
 * accuracy was measured on pictures made that way; a different size or quality is a different input,
 * and the measurements would no longer apply. A PDF is page 1 only, drawn on white, never enlarged —
 * also exactly as Android does it.
 */
object IosImageDecoder : ImageDecoder {
    private const val MAX_EDGE = 1400.0
    private const val JPEG_QUALITY = 0.88

    override suspend fun toJpeg(source: Any): DecodedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val image: UIImage? = when (source) {
                is UIImage -> source
                is NSData -> UIImage(data = source)
                is NSURL -> if (source.pathExtension?.lowercase() == "pdf") firstPdfPage(source)
                else source.path?.let { UIImage(contentsOfFile = it) }
                else -> null
            }
            image?.let { encode(it) }
        }.getOrNull()
    }

    /** Draw [image] at most MAX_EDGE on its longest side (never enlarged), then JPEG it. */
    private fun encode(image: UIImage): DecodedImage? {
        val (w0, h0) = image.size.useContents { width to height }
        if (w0 <= 0.0 || h0 <= 0.0) return null
        val scale = minOf(1.0, MAX_EDGE / maxOf(w0, h0))
        val w = maxOf(1.0, (w0 * scale).toInt().toDouble())
        val h = maxOf(1.0, (h0 * scale).toInt().toDouble())
        val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0; opaque = true }
        val drawn = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format).imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, w, h))
        }
        val jpeg = UIImageJPEGRepresentation(drawn, JPEG_QUALITY) ?: return null
        return DecodedImage(jpeg.toByteArray(), w / h)
    }

    /** Page 1 of a PDF, on white, at its own size or smaller (1 point = 1 pixel, as Android's PdfRenderer). */
    private fun firstPdfPage(url: NSURL): UIImage? {
        @Suppress("UNCHECKED_CAST")
        val cfUrl = CFBridgingRetain(url) as CFURLRef?
        val doc = CGPDFDocumentCreateWithURL(cfUrl)
        CFBridgingRelease(cfUrl)
        if (doc == null) return null
        try {
            if (CGPDFDocumentGetNumberOfPages(doc) == 0uL) return null
            val page = CGPDFDocumentGetPage(doc, 1u) ?: return null
            val (pw, ph) = CGPDFPageGetBoxRect(page, kCGPDFMediaBox).useContents { size.width to size.height }
            if (pw <= 0.0 || ph <= 0.0) return null
            val scale = minOf(1.0, MAX_EDGE / maxOf(pw, ph))
            val w = maxOf(1.0, (pw * scale).toInt().toDouble())
            val h = maxOf(1.0, (ph * scale).toInt().toDouble())
            val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0; opaque = true }
            return UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format).imageWithActions { ctx ->
                UIColor.whiteColor.setFill()
                UIRectFill(CGRectMake(0.0, 0.0, w, h))
                val cg = ctx?.CGContext
                // PDF space is upside down to UIKit's: flip, then scale the page into the picture.
                CGContextTranslateCTM(cg, 0.0, h)
                CGContextScaleCTM(cg, w / pw, -h / ph)
                CGContextDrawPDFPage(cg, page)
            }
        } finally {
            CGPDFDocumentRelease(doc)
        }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
