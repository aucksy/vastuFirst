package com.vastufirst.app.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android: the report drawn on A4 pages with PdfDocument — real, selectable text, so an architect can
 * search it — saved in the cache folder the file provider shares, then offered to the share sheet.
 */
@Composable
actual fun rememberReportSharer(): ReportSharer {
    val context = LocalContext.current
    return remember(context) {
        object : ReportSharer {
            override fun share(document: ReportDocument, fileName: String): Boolean = runCatching {
                val dir = File(context.cacheDir, "shared-reports").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // only ever the one being shared
                val file = File(dir, fileName)
                file.outputStream().use { out -> writeReportPdf(document).writeTo(out) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, document.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share your report").addNewTaskIfNeeded(context))
            }.isSuccess
        }
    }
}

private fun Intent.addNewTaskIfNeeded(context: Context): Intent =
    if (context is android.app.Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The whole document on as many A4 pages as it needs. Internal so a test can count its pages. */
internal fun writeReportPdf(doc: ReportDocument): PdfDocument {
    val pdf = PdfDocument()
    val width = PAGE_WIDTH - 2 * PAGE_MARGIN
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f

    fun newPage() {
        page?.let { pdf.finishPage(it) }
        pageNumber += 1
        page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = PAGE_MARGIN.toFloat()
    }

    fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun draw(text: String, p: TextPaint, gapAfter: Float, indent: Float = 0f) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, (width - indent).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()
        var line = 0
        while (line < layout.lineCount) {
            if (page == null || y + (layout.getLineBottom(line) - layout.getLineTop(line)) > PAGE_HEIGHT - PAGE_MARGIN) newPage()
            // As many lines as fit on this page, drawn from this layout.
            var last = line
            val top = layout.getLineTop(line)
            while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= PAGE_HEIGHT - PAGE_MARGIN) last++
            val canvas = page!!.canvas
            canvas.save()
            canvas.translate(PAGE_MARGIN + indent, y - top)
            canvas.clipRect(0f, top.toFloat(), (width - indent).toFloat(), layout.getLineBottom(last).toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += (layout.getLineBottom(last) - top).toFloat()
            line = last + 1
        }
        y += gapAfter
    }

    val ink = Color.rgb(31, 36, 33)
    val muted = Color.rgb(110, 112, 104)
    val accent = Color.rgb(95, 132, 101)

    newPage()
    draw(doc.title, paint(20f, ink, bold = true), 4f)
    draw(doc.subtitle, paint(10f, muted), 16f)
    doc.blocks.forEach { block ->
        when (block) {
            is ReportBlock.Heading -> { y += 6f; draw(block.text, paint(14f, accent, bold = true), 8f) }
            is ReportBlock.Score -> {
                draw("${block.score} · ${block.band}", paint(18f, ink, bold = true), 4f)
                draw(block.sentence, paint(11f, ink), 8f)
            }
            is ReportBlock.Text -> draw(block.text, paint(10.5f, if (block.quiet) muted else ink), 6f)
            is ReportBlock.Item -> {
                draw(block.badge, paint(8f, accent, bold = true), 2f)
                draw(block.title, paint(12f, ink, bold = true), 2f)
                block.meta?.let { draw(it, paint(10f, muted), 4f) }
                block.lines.forEach { l ->
                    l.label?.let { draw(it, paint(8.5f, accent, bold = true), 1f, indent = 8f) }
                    draw(l.text, paint(10f, if (l.quiet) muted else ink), 4f, indent = 8f)
                }
                y += 8f
            }
        }
    }
    page?.let { pdf.finishPage(it) }
    return pdf
}
