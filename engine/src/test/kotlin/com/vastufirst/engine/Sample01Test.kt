package com.vastufirst.engine

import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The anchor test (Product PRD §15). If sample-01 is not 31, the engine is wrong, not the fixture.
 */
class Sample01Test {

    private val analysis = VastuEngine().analyze(Fixtures.sample01())

    @Test
    fun `sample-01 scores exactly 31`() {
        assertEquals(31, analysis.score, "sample-01 must reproduce the §15 worked example exactly")
    }

    @Test
    fun `base and penalty match the worked example`() {
        assertEquals(47.27, (analysis.base * 100).toInt() / 100.0, 0.01)
        assertEquals(16, analysis.defectPenalty)
    }

    @Test
    fun `defects contain X-01 and X-03, both MAJOR, and nothing else`() {
        assertEquals(listOf("X-01", "X-03"), analysis.defects.map { it.id }.sorted())
        assertTrue(analysis.defects.all { it.severity == Severity.MAJOR })
    }

    @Test
    fun `door resolves to E6 Satya, INAUSPICIOUS`() {
        val door = analysis.doorResult!!
        assertEquals("E6", door.pada.id)
        assertEquals(PadaVerdict.INAUSPICIOUS, door.verdict)
        assertEquals(15, door.points)
        assertEquals(145.7, (door.bearing * 10).toInt() / 10.0, 0.1)
    }

    @Test
    fun `room verdicts match the §15 table`() {
        fun verdict(id: String) = analysis.roomResults.first { it.roomId == id }.verdict
        assertEquals(Verdict.NOT_SCORED, verdict("pooja"))     // R-05 disputed (W-12)
        assertEquals(Verdict.ACCEPTABLE, verdict("bed2"))       // N
        assertEquals(Verdict.DEFECT, verdict("toilet"))         // NE
        assertEquals(Verdict.SUBOPTIMAL, verdict("kitchen"))    // W — Brahmasthan clip below threshold
        assertEquals(Verdict.DEFECT, verdict("stairs"))         // Brahmasthan
        assertEquals(Verdict.IDEAL, verdict("living"))          // E
        assertEquals(Verdict.IDEAL, verdict("master"))          // SW
    }

    @Test
    fun `disputes contain W-12, cuts and extensions empty, shape regular`() {
        assertTrue(analysis.disputes.any { it.id == "W-12" })
        assertTrue(analysis.cuts.isEmpty())
        assertTrue(analysis.extensions.isEmpty())
        assertFalse(analysis.shapeIrregular)
    }

    @Test
    fun `notAssessed lists the Tier C and D rules (no fixtures or site supplied)`() {
        assertTrue(analysis.notAssessed.containsAll(listOf("X-09", "X-11", "X-12", "X-13")))
    }

    @Test
    fun `ruleSetVersion is stamped`() {
        assertEquals("2026.07.19-1", analysis.ruleSetVersion)
    }
}
