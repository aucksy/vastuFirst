@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vastufirst.app.ui.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererContext
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawInRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake

/**
 * The iPhone: the report drawn on A4 pages with UIGraphicsPDFRenderer (real text), written to the
 * app's temporary folder, then offered to the iPhone's share sheet.
 */
@Composable
actual fun rememberReportSharer(): ReportSharer = remember {
    object : ReportSharer {
        override fun share(document: ReportDocument, fileName: String): Boolean = runCatching {
            val path = NSTemporaryDirectory() + fileName
            val url = NSURL.fileURLWithPath(path)
            val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH.toDouble(), PAGE_HEIGHT.toDouble()))
            val ok = renderer.writePDFToURL(url, withActions = { ctx -> drawDocument(document, ctx) }, error = null)
            if (!ok) return@runCatching false
            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            var top = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (top?.presentedViewController != null) top = top.presentedViewController
            top?.presentViewController(sheet, animated = true, completion = null)
            top != null
        }.getOrDefault(false)
    }
}

private fun drawDocument(doc: ReportDocument, ctx: UIGraphicsPDFRendererContext?) {
    val width = (PAGE_WIDTH - 2 * PAGE_MARGIN).toDouble()
    val bottom = (PAGE_HEIGHT - PAGE_MARGIN).toDouble()
    var y = PAGE_MARGIN.toDouble()
    ctx?.beginPage()

    val ink = UIColor(red = 31 / 255.0, green = 36 / 255.0, blue = 33 / 255.0, alpha = 1.0)
    val muted = UIColor(red = 110 / 255.0, green = 112 / 255.0, blue = 104 / 255.0, alpha = 1.0)
    val accent = UIColor(red = 95 / 255.0, green = 132 / 255.0, blue = 101 / 255.0, alpha = 1.0)

    fun draw(text: String, size: Double, color: UIColor, bold: Boolean = false, gapAfter: Double = 4.0, indent: Double = 0.0) {
        val font = if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
        val attrs = mapOf<Any?, Any?>(NSFontAttributeName to font, NSForegroundColorAttributeName to color)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val s = text as NSString
        val h = s.boundingRectWithSize(CGSizeMake(width - indent, 10_000.0), NSStringDrawingUsesLineFragmentOrigin, attrs, null)
            .useContents { size.height }
        if (y + h > bottom && y > PAGE_MARGIN) {
            ctx?.beginPage()
            y = PAGE_MARGIN.toDouble()
        }
        s.drawInRect(CGRectMake(PAGE_MARGIN + indent, y, width - indent, h), withAttributes = attrs)
        y += h + gapAfter
    }

    draw(doc.title, 20.0, ink, bold = true)
    draw(doc.subtitle, 10.0, muted, gapAfter = 16.0)
    doc.blocks.forEach { block ->
        when (block) {
            is ReportBlock.Heading -> { y += 6.0; draw(block.text, 14.0, accent, bold = true, gapAfter = 8.0) }
            is ReportBlock.Score -> {
                draw("${block.score} · ${block.band}", 18.0, ink, bold = true)
                draw(block.sentence, 11.0, ink, gapAfter = 8.0)
            }
            is ReportBlock.Text -> draw(block.text, 10.5, if (block.quiet) muted else ink, gapAfter = 6.0)
            is ReportBlock.Item -> {
                draw(block.badge, 8.0, accent, bold = true, gapAfter = 2.0)
                draw(block.title, 12.0, ink, bold = true, gapAfter = 2.0)
                block.meta?.let { draw(it, 10.0, muted) }
                block.lines.forEach { l ->
                    l.label?.let { draw(it, 8.5, accent, bold = true, gapAfter = 1.0, indent = 8.0) }
                    draw(l.text, 10.0, if (l.quiet) muted else ink, indent = 8.0)
                }
                y += 8.0
            }
        }
    }
}
