package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ⭐⭐ THE OWNER'S OWN FLAT, AGAINST THE REPLY THE REAL API ACTUALLY GAVE FOR IT.
 *
 * ⚠ **What makes this file different from every scan test before it.** Every earlier fixture was a
 * plan somebody re-typed by hand with plausible noise added — including the one that proved the last
 * release's fix. The owner said so plainly: a fix validated against a re-typing of a plan has been
 * validated against a guess about the reader, not against the reader. `owner-flat.json` is the live
 * API's answer to his sheet, captured on 2 August 2026 and pasted in unedited.
 *
 * **What he was handed before this release:** thirteen identical squares parked in a row across the
 * top two rows of an empty grid, under the heading "Place your rooms". The app then asked him
 * whether the seven empty squares next to them — the leftovers of the parking row — were "in the
 * south" and not part of his home. They were at the top of the grid, directly under the word NORTH.
 *
 * **Why.** His flat has fifteen named spaces (three balconies, a utility, a vestibule, a passage, a
 * dressing area), which is an ordinary Indian apartment, and the gate was twelve. Everything the
 * reader returned was thrown away before it reached him.
 *
 * **What the reply actually says**, and it is three separate things:
 *
 *  - the fifteen room NAMES are right, in the order a person reads the sheet;
 *  - every room carries the size the plan PRINTS, thirteen of them exactly — and under the previous
 *    prompt we asked for none of it and got none of it;
 *  - the RECTANGLES are a template: four distinct x positions for fifteen rooms, every coordinate on
 *    a 0.05 lattice, `planConfidence` 0.95. Half the 30-plan corpus comes back the same way, and no
 *    wording fixes it — a prompt explicitly forbidding round coordinates was tried and only pushed
 *    more rooms off the page.
 *
 * So the rectangles are used for ARRANGEMENT and the printed text for every MEASUREMENT, which is
 * what the rest of this feature already assumed and had never been able to do.
 */
class OwnerFlatScanTest {

    private fun reply(): ScanDraft =
        assertNotNull(RecordedScans.load(RecordedScans.OWNER_FLAT), "the owner's flat is not bundled")
            .reply

    private fun placed(): ScanOutcome.Placed {
        val out = ScanMapper.map(reply(), imageAspect = 646.0 / 1400.0)
        return assertIs<ScanOutcome.Placed>(
            out,
            "his flat must arrive as a plan, not as a row of squares — this is the whole release",
        )
    }

    private fun ScanOutcome.Placed.rect(caption: String): CellRect =
        rooms.firstOrNull { it.label.equals(caption, ignoreCase = true) }?.rect
            ?: throw AssertionError("$caption did not survive: ${rooms.map { it.label }}")

    @Test
    fun `the recorded reply is what the API returned — fifteen rooms, all of them sized`() {
        val draft = reply()
        assertEquals(15, draft.rooms.size, "his sheet has fifteen named spaces")
        assertEquals(PlanImageType.TWO_D_PLAN, draft.planType)
        assertTrue(draft.hasRoomLabels)
        // ⭐ The change that made this release possible. Zero of these carried a size before.
        val sized = draft.rooms.count { RoomDimensions.of(it) != null }
        assertEquals(15, sized, "every room on his sheet prints its size and we now capture it")
    }

    @Test
    fun `⚠ the rectangles are a template, not a measurement — which is why we do not trust them`() {
        val draft = reply()
        // Four distinct left edges for fifteen rooms. A real home does not line up like that.
        val lefts = draft.rooms.map { it.x }.distinct()
        assertTrue(
            lefts.size <= 5,
            "the reader put fifteen rooms at ${lefts.size} distinct x positions — if this ever grows, " +
                "the reply stopped being a template and the note on this file needs revisiting",
        )
        // Every coordinate lands on a twentieth. Measured edges do not do this.
        val all = draft.rooms.flatMap { listOf(it.x, it.y, it.w, it.h) }
        val onLattice = all.count { v ->
            val twentieths = v * 20.0
            kotlin.math.abs(twentieths - kotlin.math.round(twentieths)) < 1e-6
        }
        assertEquals(all.size, onLattice, "every coordinate in this reply sits on a 0.05 lattice")
        // …and it says it is 95 % sure, exactly as it does on a read that is completely wrong (S2).
        assertEquals(0.95, draft.planConfidence, 1e-9)
    }

    @Test
    fun `⭐⭐ his flat is PLACED — a fifteen-room apartment is one home, not a floor of them`() {
        // Asserted against the REPLY, not the outcome: what proves the point is that his sheet is
        // over the old gate, and that number cannot drift with how overlaps happen to resolve.
        assertTrue(
            reply().rooms.size > ScanMapper.MAX_TRUSTED_ROOMS,
            "this test only proves anything if his flat is over the old room-count gate",
        )
        val p = placed()
        assertTrue(p.rooms.all { it.rect != null }, "a Placed outcome must carry geometry")
        assertTrue(p.rooms.size >= 10, "most of his home must survive; got ${p.rooms.size}")
    }

    @Test
    fun `⭐ the rooms that decide his score all arrive`() {
        val p = placed()
        val types = p.rooms.map { it.type }
        // The highest-weighted room types the engine scores, plus the rooms his plan is about.
        for (t in listOf(RoomType.MASTER_BEDROOM, RoomType.KITCHEN, RoomType.LIVING, RoomType.TOILET)) {
            assertTrue(t in types, "$t must survive to the grid; got $types")
        }
        assertEquals(2, types.count { it == RoomType.BEDROOM }, "his flat has two bedrooms plus a master")
        assertTrue(types.count { it == RoomType.BALCONY } >= 2, "his flat has three balconies")
    }

    /**
     * ⚠ Recorded rather than asserted away: his attached master-bedroom toilet is drawn by the
     * reader wholly inside the rectangle it gave the living room, so it is dropped as a sub-area —
     * and a toilet is a scored room. This is the known, deliberate cost of dropping sub-areas by
     * geometry (owner decision D1), and it is why every drop is shown to the user rather than being
     * silent. Pinned so it stays a stated trade rather than becoming a surprise.
     */
    @Test
    fun `⚠ a space we drop is always one we can tell the user about`() {
        val p = placed()
        assertTrue(
            p.notes.dropped.isNotEmpty(),
            "if nothing is dropped from this reply any more, that is good news and this test should " +
                "be rewritten rather than deleted",
        )
        assertTrue(
            p.notes.dropped.all { it.label.isNotBlank() },
            "every dropped space must be nameable on screen: ${p.notes.dropped}",
        )
    }

    @Test
    fun `⭐⭐ the grid is wide enough to draw his home honestly`() {
        // ⚠ THE PROOF OF [ScanMapper.widenForWidest], and deliberately the one asserted hardest,
        // because it depends only on the reply and the printed sizes — never on how overlaps happen
        // to resolve, so it cannot drift.
        //
        // The reader's rectangles put fifteen rooms at four distinct x positions, which makes his
        // flat look far narrower than it is, and the grid came out FOUR columns wide. His
        // living/dining is printed 7.25 m × 4.30 m: in four columns there is no way to draw a room
        // that size wider than it is deep, so it came out the wrong way round and the overlap
        // trimmer then ate into it.
        val p = placed()
        assertTrue(
            p.cols >= 5,
            "the grid came out ${p.cols}x${p.rows}; his widest room needs more columns than that",
        )
    }

    @Test
    fun `⭐ the big rooms are drawn the way his plan prints them`() {
        val p = placed()
        // LIVING/DINING is printed 7.25 m × 4.30 m — unmistakably wider than it is deep. Drawn the
        // other way round it sits in a different Vastu direction, and it is the room the whole flat
        // is arranged around.
        //
        // ⚠ `>=`, not `>`, and the difference is deliberate. Rounding is monotonic so it can flatten
        // this room to a square but can never invert it; what this must catch is the INVERSION. The
        // strict version is asserted in `tools/grid-prototype/sim.mjs`, where the exact result was
        // measured (3 × 2) — here the full synonym table brings more rooms into the same grid, so
        // pinning an exact shape would be pinning the trimmer's arithmetic rather than the property.
        val living = p.rect("LIVING/DINING")
        assertTrue(
            living.w >= living.h,
            "LIVING/DINING is printed 7.25m x 4.30m but was drawn ${living.w}x${living.h} — " +
                "deeper than wide, which puts it in a different direction",
        )
        // The kitchen is printed 4.07 m × 2.50 m, and it is the joint highest-weighted room the
        // engine scores.
        val kitchen = p.rect("KITCHEN")
        assertTrue(
            kitchen.w >= kitchen.h,
            "KITCHEN is printed 4.07m x 2.50m but was drawn ${kitchen.w}x${kitchen.h}",
        )
    }

    @Test
    fun `⭐ his home does not arrive as a scatter of islands`() {
        val p = placed()
        val rects = p.rooms.mapNotNull { it.rect }
        val box = com.vastufirst.shared.editor.Footprint.boundingCells(rects).size
        val empty = box - com.vastufirst.shared.editor.Footprint.occupiedCells(rects).size
        // The same standard the previous release's plan is held to: no more than two fifths empty.
        assertTrue(
            empty * 5 <= box * 2,
            "no more than two fifths of the home may be empty squares; got $empty of $box",
        )
    }
}
