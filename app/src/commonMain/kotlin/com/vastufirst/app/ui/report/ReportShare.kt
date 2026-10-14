package com.vastufirst.app.ui.report

import androidx.compose.runtime.Composable

/**
 * Hands a finished report to the phone's own share sheet as a PDF (WhatsApp, email, Drive…). Each phone
 * draws [ReportDocument] onto A4 pages its own way: Android with PdfDocument, the iPhone with
 * UIGraphicsPDFRenderer. Nothing is uploaded by us — the person chooses where it goes.
 */
interface ReportSharer {
    /** Write the PDF and open the share sheet. False when the PDF could not be made. */
    fun share(document: ReportDocument, fileName: String): Boolean
}

@Composable
expect fun rememberReportSharer(): ReportSharer

/** A safe file name from a home's name: letters, digits and dashes, never empty. */
fun reportFileName(homeName: String): String {
    val base = homeName.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        .replace(Regex("-+"), "-").trim('-').take(40)
    return "VastuFirst-${base.ifBlank { "report" }}.pdf"
}

/** A4 in PDF points, and the margins both phones use. */
internal const val PAGE_WIDTH = 595
internal const val PAGE_HEIGHT = 842
internal const val PAGE_MARGIN = 48
