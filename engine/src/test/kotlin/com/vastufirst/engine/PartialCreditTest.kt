package com.vastufirst.engine

import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ⭐⭐ PARTIAL CREDIT FOR A ROOM THAT ONLY CLIPS A FORBIDDEN ZONE (§4.5.2b, owner decision 9 Aug 2026).
 *
 * **What this is fixing, in the owner's own words: "pretty much every house I scan scores lower
 * than 4."** He was right, and the cause was measured before anything was changed. A room lost
 * everything the moment it crossed a forbidden line by the encroachment threshold — about 2 % of
 * itself, roughly a five-square-foot corner on a thousand-square-foot flat. It fell from as much as
 * 100 points to 10 with no gradient between, and the same corner was then charged AGAIN as a full
 * defect penalty. Across five real recorded plans that single behaviour took the bundled 3BHK to
 * 0.5 and a real Greencourt flat to 1.0, and more than half of every problem raised was a room
 * merely touching the central Brahmasthan — which in a real flat something almost always does.
 *
 * ⚠⚠ **THE FINDING IS NEVER SILENCED, AND THAT IS THE WHOLE DESIGN.** The one way this change could
 * have cost real accuracy was to wire the credit into the VERDICT: a room that stopped being a
 * DEFECT would vanish from the report entirely, because the detector only walks defective rooms. So
 * the verdict is untouched and only the arithmetic softens — a toilet clipping the North-East is
 * still reported, explained and remedied exactly as before. The first test below is that guarantee,
 * and it must never be deleted to make another one pass.
 */
class PartialCreditTest {

    /**
     * A living room sitting squarely in the East — its ideal zone — whose left edge runs about a
     * tenth of the way into the central Brahmasthan. Before this change it scored 10 out of 100,
     * exactly the same as a living room built entirely in the centre.
     */
    private fun clippingPlan(): Plan {
        val rooms = listOf(
            // x 63→97: mostly the East band (starts at 66.67), ~11 % of it over the centre line.
            Room("living", RoomType.LIVING, Fixtures.rect(63.0, 34.0, 34.0, 32.0)),
            // Wholly inside the North-East, which a toilet may never occupy. Nothing to discount.
            Room("toilet", RoomType.TOILET, Fixtures.rect(68.0, 74.0, 26.0, 20.0)),
        )
        return Plan(
            id = "clip-01",
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(Level(0, Fixtures.rect(0.0, 0.0, 100.0, 100.0), rooms, emptyList())),
            site = null,
            northOffsetDegrees = 0,
        )
    }

    private val analysis = VastuEngine().analyze(clippingPlan())
    private fun room(id: String) = analysis.roomResults.first { it.roomId == id }

    @Test
    fun `a clipping room is still reported as a problem`() {
        assertEquals(Verdict.DEFECT, room("living").verdict, "the verdict must not soften")
        assertTrue(
            analysis.defects.any { it.roomId == "living" },
            "partial credit must never remove a finding from the report — only its arithmetic",
        )
    }

    @Test
    fun `a clipping room keeps most of its marks instead of losing everything`() {
        val living = room("living")
        // Ideal zone is worth 100; the defect floor is 10. About a tenth over the line, so it should
        // land near 90 — not at the floor, and not at a clean 100 either.
        assertTrue(
            living.points in 80..97,
            "a room a tenth over the line scored ${living.points}; expected most of its marks",
        )
        assertTrue(living.encroachedShare < 0.2, "only a small share of this room is over the line")
    }

    @Test
    fun `a room wholly inside a forbidden zone still scores the floor`() {
        val toilet = room("toilet")
        assertEquals(Verdict.DEFECT, toilet.verdict)
        assertEquals(10, toilet.points, "nothing is being clipped here — the room IS in the zone")
        assertEquals(1.0, toilet.encroachedShare, 0.001)
    }

    @Test
    fun `the corner is no longer charged twice`() {
        // The living room's own points already fell in proportion. Its penalty must fall in step,
        // or the same small overlap is still being billed at full price on the other side of the
        // sum — which was half the reason ordinary homes bottomed out.
        val ifEveryDefectPaidInFull = analysis.defects.sumOf { fullPenaltyFor(it.severity.name) }
        assertTrue(
            analysis.defectPenalty < ifEveryDefectPaidInFull,
            "penalty ${analysis.defectPenalty} should be under $ifEveryDefectPaidInFull — the " +
                "living room is barely over the line and must not pay as if it sat in the centre",
        )
        // …but the toilet genuinely IS in the North-East, and that one still pays everything.
        assertTrue(
            analysis.defectPenalty >= 8,
            "a room wholly in a forbidden zone must still cost its full penalty",
        )
    }

    /**
     * ⭐⭐ THE FINE IS CHARGED AT THE ROOM'S OWN SHARE — not at double it (owner ruling, 10 Aug 2026).
     *
     * Partial credit already hands a room back its marks in proportion: a room a fifth over the line
     * keeps four fifths of them. The penalty did not match. It reached FULL strength once a room was
     * merely HALF over, so that same room kept 80 % of its marks and paid 40 % of the fine — one
     * corner billed twice, at two different rates, which is the very thing §4.5.2b was written to
     * stop. It was only ever a conservative half-step, and it is now the honest whole one.
     *
     * Measured across the eight recorded real plans before it was changed: the median home rose from
     * 3.9 to 5.0 out of 10, all eight improved, none fell, and **not one finding left any report** —
     * the only candidate of the eight examined that bought nothing with the reader's findings.
     *
     * This test fails the moment the ramp constant stops being 1.0, which is the point: the value is
     * a Vastu-neutral piece of arithmetic that is easy to "tune" back without anyone noticing.
     */
    @Test
    fun `the fine is charged at the same share as the marks, never at double it`() {
        val shareOf = analysis.roomResults.associate { it.roomId to it.encroachedShare }
        val atTheRoomsOwnShare = analysis.defects
            .filter { it.provenance != Provenance.DISP }
            .sumOf { d ->
                val share = d.roomId?.let(shareOf::get) ?: 1.0
                Math.round(fullPenaltyFor(d.severity.name) * share).toInt()
            }
        assertEquals(
            minOf(atTheRoomsOwnShare, 30), analysis.defectPenalty,
            "a room must pay exactly the share of the fine it is over the line by — no ramp multiplier",
        )

        // And the behavioural half: what the engine charges must be strictly LESS than the old
        // half-way ramp would have charged on this same home. Reverting the constant fails here even
        // if the sum above is ever rewritten in the same shape as the code it checks.
        val atTheOldHalfWayRamp = analysis.defects
            .filter { it.provenance != Provenance.DISP }
            .sumOf { d ->
                val share = d.roomId?.let(shareOf::get) ?: 1.0
                Math.round(fullPenaltyFor(d.severity.name) * (share / 0.5).coerceAtMost(1.0)).toInt()
            }
        assertTrue(
            analysis.defectPenalty < atTheOldHalfWayRamp,
            "penalty ${analysis.defectPenalty} is not below the old half-way ramp's " +
                "$atTheOldHalfWayRamp — the clipping room is still being charged at double its share",
        )
    }

    private fun fullPenaltyFor(severity: String) = when (severity) {
        "MAJOR" -> 8
        "MODERATE" -> 4
        else -> 1
    }

    /**
     * ⭐ The §15 worked example is untouched, and that is not luck — it was checked before a line was
     * written. Both of its condemned rooms (the North-East toilet and the central staircase) sit
     * WHOLLY on forbidden ground, so there is no share to give back and the arithmetic is identical.
     * A change that quietly moved the acceptance fixture would be a change nobody could review.
     */
    @Test
    fun `the acceptance fixture still scores 31`() {
        assertEquals(31, VastuEngine().analyze(Fixtures.sample01()).score)
    }

    /**
     * ⚠ REGRESSION. The first cut of partial credit summed FRACTIONAL penalties and subtracted the
     * total, which put the final rounding on a knife edge — the same home scored 0.4 drawn in one
     * corner of the canvas and 0.6 drawn in another. Position is never a scoring term (§0.4), so a
     * fractional penalty quietly made the app wrong in a way no reader could ever have explained.
     * Each defect's scaled penalty is now rounded to a whole number before it is summed, and the
     * share itself is quantised. `PlanConversionRoundTripTest` owns the app-level version of this;
     * this one keeps the guarantee inside the engine, where the arithmetic actually lives.
     */
    @Test
    fun `the same home scores the same wherever it sits`() {
        val engine = VastuEngine()
        val reference = engine.analyze(clippingPlan()).score
        for (shift in listOf(1.0, 4.0, 7.5, 23.0)) {
            val moved = clippingPlan().let { p ->
                val lvl = p.levels.first()
                p.copy(
                    levels = listOf(
                        lvl.copy(
                            outline = lvl.outline.map { it.copy(x = it.x + shift, y = it.y + shift) },
                            rooms = lvl.rooms.map { r ->
                                r.copy(polygon = r.polygon.map { it.copy(x = it.x + shift, y = it.y + shift) })
                            },
                        ),
                    ),
                )
            }
            assertEquals(
                reference, engine.analyze(moved).score,
                "score moved after shifting the whole home by $shift — position must never score",
            )
        }
    }
}
