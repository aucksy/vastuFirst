package com.vastufirst.shared.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ⭐⭐ Shaping rooms to the sizes the plan PRINTS, pinned against the owner's own flat.
 *
 * Green Court, Sector 90, Gurgaon — Category II. The rectangles below are what the shipped app
 * actually drew, read off his screenshot; the captions are what the sheet actually prints.
 *
 * **Four of its six dimensioned rooms came out with their orientation inverted**: the lobby, the
 * kitchen, the bedroom and the passage were all drawn wider than deep when the plan says the
 * opposite, and the passage — a 2'-3" strip — was drawn three times wider than tall. A room's Vastu
 * direction follows its shape, so this was moving the number the customer pays for.
 *
 * ⚠ These expectations live in Kotlin rather than in the fuzz mirror on purpose: the mirror carries a
 * deliberately cut-down synonym table, so `W.C`, `LOBBY` and `PASSAGE` do not resolve there and the
 * plan would silently map to something else. Same lesson the recorded-reply pins already learned.
 */
class ScanReshapeTest {

    /** The owner's flat as the reader saw it: his captions, and the boxes the app drew from them. */
    private fun gurgaonFlat() = ScanDraft(
        planType = PlanImageType.TWO_D_PLAN,
        hasRoomLabels = true,
        planConfidence = 0.95,
        rooms = listOf(
            ScanBox("BALCONY 6'-0\" WIDE", x = 0.0, y = 0.0, w = 1.0, h = 0.3, confidence = 0.9),
            ScanBox("W.C 5'-0\"X2'-11\"", x = 0.0, y = 0.3, w = 0.4, h = 0.1, confidence = 0.9),
            ScanBox("BATH 5'-0\"X3'-8½\"", x = 0.0, y = 0.4, w = 0.4, h = 0.2, confidence = 0.9),
            ScanBox("KITCHEN 6'-11\"X9'-7\"", x = 0.0, y = 0.6, w = 0.4, h = 0.1, confidence = 0.9),
            ScanBox("BED ROOM 10'-0\"X10'-6\"", x = 0.4, y = 0.3, w = 0.6, h = 0.4, confidence = 0.9),
            ScanBox("LOBBY 10'-0\"X12'-9\"", x = 0.4, y = 0.7, w = 0.6, h = 0.2, confidence = 0.9),
            ScanBox("PASSAGE 2'-3\"X9'-6\"", x = 0.7, y = 0.9, w = 0.3, h = 0.1, confidence = 0.9),
        ),
    )

    private fun placed(): ScanOutcome.Placed {
        val out = ScanMapper.map(gurgaonFlat(), imageAspect = 1.0)
        assertIs<ScanOutcome.Placed>(out, "a 7-room single-home plan must place")
        return out
    }

    private fun ScanOutcome.Placed.room(caption: String) =
        rooms.firstOrNull { it.label.startsWith(caption) }
            ?: throw AssertionError("$caption did not survive: ${rooms.map { it.label }}")

    @Test
    fun `the lobby is no longer drawn as a wide bar`() {
        // Printed 10'-0" x 12'-9", so slightly deeper than wide. The app first drew it 6 wide x 2
        // deep — three times wider than deep, the room this whole complaint began with.
        //
        // ⚠ Stated honestly rather than asserted prettily: it now comes out SQUARE, not deeper. The
        // bedroom directly above it, drawn at its own printed depth, occupies the cells the lobby
        // needs to reach its full depth, and the lobby is the room that gives way because it is the
        // biggest. That is not a shaping failure — it is the two rooms' true sizes not fitting the
        // positions the reader guessed for them, which is a placement question and is open.
        //
        // What must never come back is the wide bar, so that is what this pins.
        val r = placed().room("LOBBY").rect!!
        assertTrue(r.h >= r.w, "LOBBY printed 10'x12'9\" must never be wider than deep, got ${r.w}x${r.h}")
        assertTrue(r.w * r.h >= 6, "and it must stay one of the bigger rooms, got ${r.w}x${r.h}")
    }

    @Test
    fun `⭐ the passage now KEEPS its printed shape instead of being squashed to fit`() {
        // ⚠ This used to be the honest-limits case: the passage is printed 2'-3" x 9'-6", over four
        // times taller than wide, and the lobby beside it — grown to its own true depth and pushed
        // upward by the bottom of the grid — covered exactly the cells the reader had put it in. The
        // room survived only by falling back to the shape it was READ with, arriving flagged.
        //
        // It no longer needs the fallback. The smallest room is placed first, so the 2'-3" strip takes
        // its printed shape and the lobby, which can absorb a cut, is the one that gives way. A strip
        // this thin is exactly the kind of room that used to be lost altogether.
        val p = placed()
        val passage = p.room("PASSAGE")
        val r = passage.rect!!
        assertTrue(r.h > r.w, "PASSAGE printed 2'3\" x 9'6\" must be far deeper than wide, got ${r.w}x${r.h}")
        assertEquals(1, r.w, "a 2'-3\" strip is one cell across")
        assertTrue(
            RoomFlag.OVERLAP_TRIMMED !in passage.flags,
            "it takes its printed shape outright now, so nothing should be flagged",
        )
    }

    @Test
    fun `the kitchen is deeper than it is wide, as the plan says`() {
        // Printed 6'-11" x 9'-7". Drawn 4 wide x 1 deep. The kitchen is the joint-highest-weighted
        // room the engine scores, so its direction is the most expensive one to get wrong.
        val r = placed().room("KITCHEN").rect!!
        assertTrue(r.h > r.w, "KITCHEN printed 6'11\"x9'7\" must be deeper than wide, got ${r.w}x${r.h}")
    }

    @Test
    fun `the rooms the plan really does print wider than deep stay that way`() {
        // The rule is not "always make it tall" — it is "believe the sheet". The WC genuinely is
        // wider than it is deep, and it must not be flipped along with the others.
        val wc = placed().room("W.C").rect!!
        assertTrue(wc.w >= wc.h, "W.C printed 5'0\"x2'11\" must not be deeper than wide, got ${wc.w}x${wc.h}")
    }

    @Test
    fun `⭐ a one-dimension strip caption sets the strip's DEPTH, and the freed row collapses`() {
        // ⚠ This asserts the OPPOSITE of what it used to. "BALCONY 6'-0\" WIDE" was treated as no
        // size at all, so the balcony kept the reader's sketch rectangle — three rows deep, nearly
        // a third of the grid — while every sized room around it shrank to print scale. That is
        // the exact defect the owner rejected v0.6.4 for on his 336 sq ft plan: the balcony
        // towering over the bedroom it belongs to. The caption means "this strip is six feet
        // deep": the depth now comes from it (6' ≈ 2 cells on this plan's scale), the LENGTH stays
        // the full-width wall the reader drew it along, the shrink opens toward the outer edge —
        // never toward the sized room it touches — and the fully-empty row it leaves behind is
        // deleted, so the grid ends where the home does. Same numbers pinned in the mirror
        // (`reshape-flat` in sim.mjs); `--inject=no-strip-captions` restores the tower there.
        val p = placed()
        val balcony = p.room("BALCONY").rect!!
        assertEquals(10, balcony.w, "the strip keeps the reader's full-width length")
        assertEquals(2, balcony.h, "its depth is the printed 6'-0\" on this plan's own scale")
        assertEquals(0, balcony.row, "and it still hugs the home's north edge")
        assertEquals(9, p.rows, "the row the shrink freed is collapsed — the grid ends at the home")
    }

    @Test
    fun `the relative sizes follow the plan — a lobby is far bigger than a toilet`() {
        // The printed areas differ ninefold (127 sq ft against 15). The reader had drawn them three
        // cells apart. Whatever the exact numbers, the ordering must now be right.
        val p = placed()
        val lobby = p.room("LOBBY").rect!!
        val wc = p.room("W.C").rect!!
        assertTrue(lobby.w * lobby.h > wc.w * wc.h, "the lobby must be drawn bigger than the WC")
    }

    @Test
    fun `every room still lands inside the grid and none overlaps`() {
        // The guarantees the editor is held to. Re-shaping must not be able to break them, whatever
        // the printed numbers say — the fuzz suite proves this across random replies, this pins it
        // on the one real plan we have.
        val p = placed()
        for (r in p.rooms) {
            val q = r.rect!!
            assertTrue(q.w >= 1 && q.h >= 1, "${r.label} is under a cell")
            assertTrue(q.col >= 0 && q.row >= 0 && q.col + q.w <= p.cols && q.row + q.h <= p.rows,
                "${r.label} is off the grid: $q in ${p.cols}x${p.rows}")
        }
        for (i in p.rooms.indices) {
            for (j in i + 1 until p.rooms.size) {
                val a = p.rooms[i].rect!!
                val b = p.rooms[j].rect!!
                val hit = a.col < b.col + b.w && b.col < a.col + a.w &&
                    a.row < b.row + b.h && b.row < a.row + a.h
                assertTrue(!hit, "${p.rooms[i].label} overlaps ${p.rooms[j].label}")
            }
        }
    }

    @Test
    fun `a plan that prints no sizes at all is left completely alone`() {
        // The commonest case by far — roughly three quarters of captions in the corpus carry no
        // dimensions — so it must be a no-op, not a slightly different answer.
        // Captions spelled out rather than derived by trimming at the first space — "BED ROOM" would
        // have become "BED", which resolves to nothing and would have tested a refusal by accident.
        val plainCaptions = listOf("BALCONY", "W.C", "BATH", "KITCHEN", "BED ROOM", "LOBBY", "PASSAGE")
        val plain = gurgaonFlat().let { d ->
            d.copy(rooms = d.rooms.mapIndexed { i, b -> b.copy(label = plainCaptions[i]) })
        }
        val a = ScanMapper.map(plain, imageAspect = 1.0)
        val b = ScanMapper.map(plain, imageAspect = 1.0)
        assertEquals(a, b, "mapping must stay deterministic")
        assertIs<ScanOutcome.Placed>(a)
    }
}
