package com.vastufirst.app.ui.report

import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ⭐ THE SHARED PDF SAYS WHAT THE REPORT SAYS — ALL OF IT (14 Oct 2026).
 *
 * A report never loses a finding (CLAUDE.md §2h), and the PDF is the report leaving the phone: so every
 * finding's reasoning, every room, every disagreement between the schools, and every thing we could
 * not check must be in it. And the advice rules the screen obeys hold in the PDF too: a buyer or a
 * resident is never told to change the layout (ReportIntentTest's ban list).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ReportExportTest {

    private fun text(a: Analysis, intent: Intent): String {
        val doc = buildReportDocument(a, intent, homeName = "Test home", dateLabel = "14 Oct 2026")
        return buildString {
            appendLine(doc.title); appendLine(doc.subtitle)
            doc.blocks.forEach { b ->
                when (b) {
                    is ReportBlock.Heading -> appendLine(b.text)
                    is ReportBlock.Score -> { appendLine(b.score); appendLine(b.band); appendLine(b.sentence) }
                    is ReportBlock.Text -> appendLine(b.text)
                    is ReportBlock.Item -> {
                        appendLine(b.badge); appendLine(b.title); b.meta?.let(::appendLine)
                        b.lines.forEach { l -> l.label?.let(::appendLine); appendLine(l.text) }
                    }
                }
            }
        }
    }

    @Test
    fun every_finding_room_and_disagreement_is_in_the_pdf() {
        for (a in listOf(RenderFixtures.sampleAnalysis, RenderFixtures.flatAnalysis, RenderFixtures.prayerRoomAnalysis)) {
            val pdf = text(a, Intent.BUILDING)
            a.defects.forEach { d -> assertTrue(d.explanation in pdf, "missing finding: ${d.explanation.take(60)}") }
            assertEquals(a.roomResults.size, buildReportDocument(a, Intent.BUILDING, "h", "d").blocks
                .count { it is ReportBlock.Item && it.badge != "START HERE" && !it.badge.startsWith("#") &&
                    it.badge != "SCHOOLS DISAGREE" && !it.title.startsWith("Front door") }, "every room is one item")
            a.disputes.forEach { disp ->
                assertTrue(disp.title in pdf, "missing dispute: ${disp.title}")
                assertTrue(disp.readingA.text in pdf && disp.readingB.text in pdf, "a dispute must show both readings")
            }
            a.notChecked.forEach { assertTrue(it.label in pdf, "missing not-checked item: ${it.label}") }
            assertTrue("/ 10" in pdf, "the score is in it")
        }
    }

    @Test
    fun a_buyer_or_resident_is_never_told_to_change_the_layout() {
        val banned = listOf(
            "Change the layout", "renovate", "still free to make", "Nothing is built yet",
            "Move or resize", "still on paper", "on the drawing",
        )
        for (intent in listOf(Intent.BUYING, Intent.LIVING)) {
            val pdf = text(RenderFixtures.sampleAnalysis, intent)
            banned.forEach { phrase -> assertFalse(phrase.lowercase() in pdf.lowercase(), "\"$phrase\" in a ${intent.name} PDF") }
        }
    }

    @Test
    fun the_file_name_is_safe_and_never_empty() {
        assertEquals("VastuFirst-Builder-s-draft-2BHK.pdf", reportFileName("Builder's draft — 2BHK"))
        assertEquals("VastuFirst-report.pdf", reportFileName("  ///  "))
    }

    // The Android PDF itself: A4 pages, more than one for a full report, and real PDF bytes out.
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun the_android_pdf_is_written_on_a4_pages() {
        val pdf = writeReportPdf(buildReportDocument(RenderFixtures.sampleAnalysis, Intent.BUILDING, "h", "d"))
        try {
            assertTrue(pdf.pages.size >= 2, "a full report runs to more than one page, was ${pdf.pages.size}")
            assertTrue(pdf.pages.all { it.pageWidth == PAGE_WIDTH && it.pageHeight == PAGE_HEIGHT })
            val bytes = java.io.ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray()
            assertTrue(bytes.size > 1000 && String(bytes, 0, 4) == "%PDF", "not a PDF: ${bytes.size} bytes")
        } finally {
            pdf.close()
        }
    }
}
