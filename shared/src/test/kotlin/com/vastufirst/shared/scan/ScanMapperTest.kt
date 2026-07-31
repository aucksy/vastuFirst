package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.overlaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The mapper is where essentially all of scan's correctness risk lives, so it is held to the same
 * standard as the editor: pure, and tested against adversarial model output as well as the recorded
 * real replies (docs/SCAN-PLAN-READING-PLAN.md §4.3).
 */
class ScanMapperTest {

    private fun box(label: String, x: Double, y: Double, w: Double, h: Double, c: Double = 0.9) =
        ScanBox(label, x, y, w, h, c)

    private fun draft(vararg rooms: ScanBox, type: PlanImageType = PlanImageType.TWO_D_PLAN) =
        ScanDraft(planType = type, hasRoomLabels = true, rooms = rooms.toList(), planConfidence = 0.95)

    /** A clean 2×2 tiling of four real rooms — coverage 1.0, varied areas, no overlaps. */
    private fun goodDraft() = draft(
        box("LIVING ROOM", 0.0, 0.0, 0.6, 0.5),
        box("KITCHEN", 0.6, 0.0, 0.4, 0.3),
        box("MASTER BEDROOM", 0.0, 0.5, 0.5, 0.5),
        box("TOILET", 0.5, 0.5, 0.5, 0.5),
        box("POOJA", 0.6, 0.3, 0.4, 0.2),
    )

    // ---- L0 triage: the refusals a user can act on -------------------------------------------

    @Test
    fun `a 3D marketing render is refused, not read`() {
        // 5 of the 30 real plans were 3D renders. They do not fail loudly — they return
        // plausible-looking rectangles that are simply wrong, the worst case for a paid score.
        val out = ScanMapper.map(goodDraft().copy(planType = PlanImageType.THREE_D_RENDER))
        assertEquals(RefusalReason.NOT_2D, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `something that is not a plan at all is refused`() {
        val out = ScanMapper.map(goodDraft().copy(planType = PlanImageType.NOT_A_PLAN))
        assertEquals(RefusalReason.NOT_A_PLAN, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `a multi-unit sheet is refused with its own reason`() {
        // plan-001 of the real corpus: UNIT-1 … UNIT-4 plus a LIFT / STAIRCASE core.
        val out = ScanMapper.map(
            draft(
                box("UNIT-1", 0.0, 0.0, 0.5, 0.5),
                box("UNIT-2", 0.5, 0.0, 0.5, 0.5),
                box("UNIT-3", 0.0, 0.5, 0.5, 0.5),
                box("UNIT-4", 0.5, 0.5, 0.5, 0.5),
            ),
        )
        assertEquals(RefusalReason.MULTI_UNIT, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `one unit caption on a single home is a title, not a second home`() {
        val out = ScanMapper.map(
            draft(
                box("UNIT-1", 0.0, 0.0, 0.01, 0.01),
                box("LIVING ROOM", 0.0, 0.0, 0.6, 0.5),
                box("KITCHEN", 0.6, 0.0, 0.4, 0.5),
                box("MASTER BEDROOM", 0.0, 0.5, 1.0, 0.5),
            ),
        )
        assertTrue(out !is ScanOutcome.Refused, "one UNIT caption must not refuse the whole plan")
    }

    @Test
    fun `an unlabelled plan is refused — the owner made labels a precondition`() {
        val out = ScanMapper.map(goodDraft().copy(hasRoomLabels = false))
        assertEquals(RefusalReason.NO_LABELS, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `the model saying it could not read the plan is refused`() {
        val out = ScanMapper.map(ScanDraft(planType = PlanImageType.TWO_D_PLAN, unreadable = true))
        assertEquals(RefusalReason.NO_LABELS, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `a numbered-legend plan whose key was not resolved refuses for the right reason`() {
        // §3j D2: captions came back as "1"…"15". They pass a has-labels check while carrying no
        // usable names, so the refusal must be NO_LABELS — the thing the user can actually fix.
        val out = ScanMapper.map(draft(*(1..12).map { box("$it", 0.0, 0.0, 0.2, 0.2) }.toTypedArray()))
        assertEquals(RefusalReason.NO_LABELS, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `a 2D labelled plan with no rooms at all refuses as NO_ROOMS`() {
        val out = ScanMapper.map(ScanDraft(planType = PlanImageType.TWO_D_PLAN, hasRoomLabels = true))
        assertEquals(RefusalReason.NO_ROOMS, assertIs<ScanOutcome.Refused>(out).reason)
    }

    @Test
    fun `a reply with no planType field is read, not treated as 3D`() {
        // The §3e fixtures predate the triage prompt. "Not stated" must never mean "reject".
        val out = ScanMapper.map(goodDraft().copy(planType = PlanImageType.UNKNOWN))
        assertIs<ScanOutcome.Placed>(out)
    }

    // ---- adversarial geometry -----------------------------------------------------------------

    @Test
    fun `NaN and infinite coordinates are dropped, not propagated`() {
        val out = ScanMapper.map(
            draft(
                box("KITCHEN", Double.NaN, 0.0, 0.4, 0.4),
                box("TOILET", 0.0, 0.0, Double.POSITIVE_INFINITY, 0.4),
                box("LIVING ROOM", 0.0, 0.0, 1.0, 1.0),
            ),
        )
        val dropped = outcomeNotes(out).dropped
        assertEquals(2, dropped.count { it.reason == DropReason.INVALID_GEOMETRY })
    }

    @Test
    fun `inverted and zero-size rectangles are dropped`() {
        val out = ScanMapper.map(
            draft(
                box("KITCHEN", 0.5, 0.5, -0.2, 0.2),
                box("TOILET", 0.5, 0.5, 0.2, 0.0),
                box("LIVING ROOM", 0.0, 0.0, 1.0, 1.0),
            ),
        )
        assertEquals(2, outcomeNotes(out).dropped.count { it.reason == DropReason.INVALID_GEOMETRY })
    }

    @Test
    fun `a rectangle sticking out of the plan is clamped back in and flagged`() {
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 1.0, 0.6),
                box("KITCHEN", 0.8, 0.6, 0.5, 0.4), // sticks 0.3 past the east wall
                box("MASTER BEDROOM", 0.0, 0.6, 0.8, 0.4),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        val kitchen = placed.rooms.single { it.type == RoomType.KITCHEN }
        assertTrue(RoomFlag.OUT_OF_BOUNDS_CLAMPED in kitchen.flags)
        assertTrue(kitchen.rect!!.right <= placed.cols)
    }

    @Test
    fun `a rectangle entirely outside the plan is dropped`() {
        val out = ScanMapper.map(
            draft(
                box("KITCHEN", 1.5, 0.2, 0.3, 0.3),
                box("LIVING ROOM", 0.0, 0.0, 1.0, 1.0),
            ),
        )
        assertTrue(outcomeNotes(out).dropped.any { it.reason == DropReason.INVALID_GEOMETRY })
    }

    @Test
    fun `forty rooms do not break anything`() {
        val boxes = (0 until 40).map { i ->
            val col = i % 8
            val row = i / 8
            box(if (i % 2 == 0) "BEDROOM $i" else "TOILET $i", col / 8.0, row / 5.0, 1 / 8.0, 1 / 5.0)
        }
        val out = ScanMapper.map(draft(*boxes.toTypedArray()))
        // 40 identical-area boxes is the fabrication signature, so this lands in Assisted — which is
        // itself the assertion: nothing crashes and no invented layout reaches the user.
        assertIs<ScanOutcome.Assisted>(out)
    }

    @Test
    fun `a single room filling the whole plan is not a layout`() {
        val out = ScanMapper.map(draft(box("LIVING ROOM", 0.0, 0.0, 1.0, 1.0)))
        assertEquals(AssistReason.TOO_FEW_PLACED, assertIs<ScanOutcome.Assisted>(out).reason)
    }

    @Test
    fun `every room on top of every other still identifies them all`() {
        val out = ScanMapper.map(
            draft(*(1..5).map { box("BEDROOM $it", 0.0, 0.0, 1.0, 1.0, c = it / 10.0) }.toTypedArray()),
        )
        val assisted = assertIs<ScanOutcome.Assisted>(out)
        assertEquals(5, assisted.rooms.size, "the rooms were still identified — that is the saving")
        assertTrue(assisted.rooms.all { it.rect == null })
    }

    @Test
    fun `⭐ a small room squeezed between two bigger ones is KEPT, and they give way instead`() {
        // The toilet straddles the seam between two bigger, more confident rooms. This used to cost
        // the plan its toilet outright: cutting it clear of one pushed it into the other, there was no
        // single edge left to give, and it was dropped — a scored room, weight 2.5, gone from a paid
        // score with only a line in "we also saw" to show for it.
        //
        // The smallest room is now placed FIRST, so the cost lands where it can be absorbed: a cell
        // off a forty-cell living room is nothing, the same cell off a four-cell toilet is a quarter
        // of it. Across the 30 real plans this is the difference between 10 rooms lost and none.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 0.5, 1.0, c = 0.95),
                box("KITCHEN", 0.5, 0.0, 0.5, 1.0, c = 0.90),
                box("TOILET", 0.4, 0.4, 0.2, 0.2, c = 0.10),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(3, placed.rooms.size, "the toilet must survive")
        assertTrue(placed.notes.dropped.isEmpty(), "nothing may be reported as lost: ${placed.notes.dropped}")
        val toilet = placed.rooms.single { it.type == RoomType.TOILET }
        assertEquals(CellRect(4, 4, 2, 2), toilet.rect, "it keeps exactly the cells it was read in")
        assertTrue(RoomFlag.OVERLAP_TRIMMED !in toilet.flags, "and it is not the one that gave way")
        val living = placed.rooms.single { it.type == RoomType.LIVING }
        assertTrue(RoomFlag.OVERLAP_TRIMMED in living.flags, "the big room absorbs the cut and is flagged")
        assertEditorInvariants(placed)
    }

    @Test
    fun `a room drawn on top of another of the same size is dropped and reported`() {
        // What is left of the unresolvable case once the small room goes first: two rectangles in
        // exactly the same place. One of them can be placed and the other has nowhere to go — not a
        // cut, not a single free cell at its own corner — so it is dropped and SAID, never silently.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 0.5, 1.0, c = 0.95),
                box("KITCHEN", 0.5, 0.0, 0.5, 1.0, c = 0.90),
                box("BATH", 0.4, 0.4, 0.2, 0.2, c = 0.50),
                box("TOILET", 0.4, 0.4, 0.2, 0.2, c = 0.10),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(3, placed.rooms.size)
        assertTrue(placed.notes.dropped.any { it.label == "TOILET" && it.reason == DropReason.OVERLAP_UNRESOLVABLE })
        assertEditorInvariants(placed)
    }

    @Test
    fun `duplicate labels stay separate rooms`() {
        val out = ScanMapper.map(
            draft(
                box("BEDROOM", 0.0, 0.0, 0.5, 0.5),
                box("BEDROOM", 0.5, 0.0, 0.5, 0.3),
                box("KITCHEN", 0.5, 0.3, 0.5, 0.2),
                box("LIVING ROOM", 0.0, 0.5, 1.0, 0.5),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(2, placed.rooms.count { it.type == RoomType.BEDROOM })
    }

    @Test
    fun `an unrecognised caption is dropped and reported, never guessed`() {
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 1.0, 0.5),
                box("KITCHEN", 0.0, 0.5, 0.5, 0.5),
                box("ZORBING PIT", 0.5, 0.5, 0.5, 0.5),
            ),
        )
        val dropped = outcomeNotes(out).dropped
        assertEquals(1, dropped.count { it.reason == DropReason.UNKNOWN_LABEL })
        assertEquals("ZORBING PIT", dropped.single { it.reason == DropReason.UNKNOWN_LABEL }.label)
    }

    // ---- sub-areas, by geometry (owner decision D1) ---------------------------------------------

    @Test
    fun `a rectangle wholly inside another room is dropped as a sub-area`() {
        val out = ScanMapper.map(
            draft(
                box("MASTER BEDROOM", 0.0, 0.0, 0.6, 0.6),
                box("STORE", 0.1, 0.1, 0.15, 0.15),   // wholly inside the bedroom
                box("KITCHEN", 0.6, 0.0, 0.4, 0.6),
                box("LIVING ROOM", 0.0, 0.6, 1.0, 0.4),
            ),
        )
        val dropped = outcomeNotes(out).dropped
        assertEquals(listOf("STORE"), dropped.filter { it.reason == DropReason.SUB_AREA }.map { it.label })
    }

    @Test
    fun `an adjacent room that merely touches is not a sub-area`() {
        val out = ScanMapper.map(goodDraft())
        assertTrue(outcomeNotes(out).dropped.none { it.reason == DropReason.SUB_AREA })
    }

    @Test
    fun `a dressing area is dropped by name even when it is drawn outside its bedroom`() {
        val out = ScanMapper.map(
            draft(
                box("MASTER BEDROOM", 0.0, 0.0, 0.6, 0.6),
                box("DRESS", 0.6, 0.0, 0.4, 0.6),
                box("KITCHEN", 0.0, 0.6, 0.5, 0.4),
                box("LIVING ROOM", 0.5, 0.6, 0.5, 0.4),
            ),
        )
        assertTrue(outcomeNotes(out).dropped.any { it.label == "DRESS" && it.reason == DropReason.NOT_HABITABLE })
    }

    // ---- the two objective gates ----------------------------------------------------------------

    @Test
    fun `⭐ the model's own confidence is NEVER a gate`() {
        // S2, measured three times: planConfidence was 0.95 on a 100% read, a 25% read and on
        // fabricated geometry. Dropping it to 0.01 must change nothing at all.
        val confident = ScanMapper.map(goodDraft())
        val abject = ScanMapper.map(goodDraft().copy(planConfidence = 0.01))
        assertIs<ScanOutcome.Placed>(confident)
        val b = assertIs<ScanOutcome.Placed>(abject)
        assertEquals((confident as ScanOutcome.Placed).rooms, b.rooms)
    }

    @Test
    fun `⭐ uniform box areas mean the geometry was invented — even at full coverage`() {
        // §3j D2: a numbered plan returned 15 rooms all exactly 0.15×0.15 at confidence 0.95. Here
        // the fabrication tiles the plan perfectly, so ONLY the area-variance detector can catch it.
        val names = listOf(
            "LIVING ROOM", "KITCHEN", "MASTER BEDROOM", "BEDROOM", "TOILET", "BATH",
            "POOJA", "DINING", "STUDY", "STORE", "BALCONY", "UTILITY",
        )
        val boxes = names.mapIndexed { i, n ->
            box(n, (i % 4) * 0.25, (i / 4) * (1.0 / 3.0), 0.25, 1.0 / 3.0)
        }
        val out = ScanMapper.map(draft(*boxes.toTypedArray()))
        assertEquals(1.0, ScanMapper.coverageOf(boxes), 1e-9)
        assertEquals(AssistReason.UNIFORM_BOXES, assertIs<ScanOutcome.Assisted>(out).reason)
    }

    @Test
    fun `three same-sized rooms are plausible and are not called fabricated`() {
        val out = ScanMapper.map(
            draft(
                box("BEDROOM", 0.0, 0.0, 1.0, 1 / 3.0),
                box("KITCHEN", 0.0, 1 / 3.0, 1.0, 1 / 3.0),
                box("LIVING ROOM", 0.0, 2 / 3.0, 1.0, 1 / 3.0),
            ),
        )
        assertIs<ScanOutcome.Placed>(out)
    }

    @Test
    fun `⭐ a plan with too many rooms hands them over unplaced rather than inventing a layout`() {
        // A floor plate's worth of spaces. The names still come through — that is the product.
        val names = listOf(
            "LIVING ROOM", "KITCHEN", "MASTER BEDROOM", "BEDROOM", "TOILET", "BATH", "POOJA",
            "DINING", "STUDY", "STORE", "BALCONY", "UTILITY", "CORRIDOR", "GUEST ROOM",
        )
        val boxes = names.mapIndexed { i, n ->
            box(n, (i % 5) * 0.2, (i / 5) * 0.33, 0.10 + (i % 4) * 0.03, 0.15 + (i % 3) * 0.08)
        }
        val out = ScanMapper.map(draft(*boxes.toTypedArray()))
        val assisted = assertIs<ScanOutcome.Assisted>(out)
        assertEquals(AssistReason.TOO_MANY_ROOMS, assisted.reason)
        assertEquals(14, assisted.rooms.size, "L1 survives — the room list is the product")
        assertTrue(assisted.rooms.all { it.rect == null }, "Assisted must not carry geometry")
    }

    @Test
    fun `⭐ the gate is room count, and coverage decides nothing`() {
        // Guards the correction that cost this feature a wrong answer in BOTH directions. Coverage
        // was the gate until the model's rectangles were drawn back over real plans and looked at:
        // two plans with IDENTICAL coverage (0.421) placed well and badly, and the corpus's highest
        // coverage (0.760) placed worse than its lowest-but-one. So the same rooms must map the same
        // way whatever their coverage.
        assertEquals(12, ScanMapper.MAX_TRUSTED_ROOMS)

        val tiling = goodDraft().rooms                                        // coverage 1.0
        // The same five rooms in the same arrangement, shrunk into a quarter of the plan — so
        // coverage collapses to ~0.25 while nothing about the reading changes.
        val sparse = tiling.map { it.copy(x = it.x / 2, y = it.y / 2, w = it.w / 2, h = it.h / 2) }
        assertTrue(ScanMapper.coverageOf(sparse) < 0.3 && ScanMapper.coverageOf(tiling) > 0.99)

        val a = assertIs<ScanOutcome.Placed>(ScanMapper.map(draft(*tiling.toTypedArray())))
        val b = assertIs<ScanOutcome.Placed>(ScanMapper.map(draft(*sparse.toTypedArray())))
        assertEquals(a.rooms.map { it.type }, b.rooms.map { it.type })
        // ⭐ And now they are the SAME LAYOUT, cell for cell, not merely the same rooms. That is what
        // framing the grid on the home rather than on the picture buys: how much of the sheet the
        // drawing happens to occupy — a builder's logo, a title block, white paper — stops deciding
        // how big the home is drawn. This is the owner's report, as a property.
        assertEquals(
            a.rooms.map { it.type to it.rect },
            b.rooms.map { it.type to it.rect },
            "the same home read at half the scale must draw identically",
        )
        assertEquals(a.cols to a.rows, b.cols to b.rows)
    }

    // ---- overlaps: TRIM, never relocate ----------------------------------------------------------

    @Test
    fun `⭐ an overlapping room is TRIMMED where it was read, never moved somewhere free`() {
        // This is the anti-`fitWithoutOverlap` assertion. Relocating a room the user has not seen
        // silently misreports where the kitchen is — and the kitchen's zone is a scored input.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 0.6, 1.0, c = 0.95),
                box("KITCHEN", 0.5, 0.0, 0.5, 0.5, c = 0.40),  // overlaps the living room's east strip
                box("MASTER BEDROOM", 0.6, 0.5, 0.4, 0.5, c = 0.90),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        // The LIVING ROOM is the one that gives way here — it is the biggest, so it goes last and it
        // is the one that can afford the cut. Read across the whole plan: 0..6 wide by the full depth.
        val living = placed.rooms.single { it.type == RoomType.LIVING }
        assertTrue(RoomFlag.OVERLAP_TRIMMED in living.flags, "the trim must be flagged for the user")

        // ⭐ It kept its own top-LEFT corner and lost a strip off its east side — cut back, not moved.
        // This is the anti-relocation assertion: a room that travels silently misreports where it is,
        // and a room's position is what decides its Vastu direction.
        assertEquals(CellRect(0, 0, 5, 10), living.rect, "cut on one edge, anchored where it was read")
    }

    @Test
    fun `⭐ the smaller room keeps its cells, whatever its confidence`() {
        // The kitchen is the LEAST confident rectangle in this reply (0.40 against 0.95) and it still
        // keeps every cell, because it is smaller than the living room. Confidence only breaks ties
        // between rooms of equal size now; what a cut actually costs is proportional to the room.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 0.6, 1.0, c = 0.95),
                box("KITCHEN", 0.5, 0.0, 0.5, 0.5, c = 0.40),
                box("MASTER BEDROOM", 0.6, 0.5, 0.4, 0.5, c = 0.90),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        val kitchen = placed.rooms.single { it.type == RoomType.KITCHEN }
        assertEquals(CellRect(5, 0, 5, 5), kitchen.rect)
        assertTrue(RoomFlag.OVERLAP_TRIMMED !in kitchen.flags)
    }

    // ---- the invariants the editor downstream depends on ------------------------------------------

    @Test
    fun `everything Placed satisfies the editor's invariants`() {
        val placed = assertIs<ScanOutcome.Placed>(ScanMapper.map(goodDraft()))
        assertEditorInvariants(placed)
    }

    // ---- the grid ---------------------------------------------------------------------------------

    @Test
    fun `the grid follows the HOME's proportions and stays in the editor's range`() {
        assertEquals(ScanMapper.DEFAULT_GRID to ScanMapper.DEFAULT_GRID, ScanMapper.gridFor(null))
        assertEquals(ScanMapper.DEFAULT_GRID to ScanMapper.DEFAULT_GRID, ScanMapper.gridFor(0.0))
        assertEquals(ScanMapper.DEFAULT_GRID to ScanMapper.DEFAULT_GRID, ScanMapper.gridFor(Double.NaN))
        assertEquals(10 to 10, ScanMapper.gridFor(1.0))
        assertEquals(10 to 5, ScanMapper.gridFor(2.0))          // wide plan
        assertEquals(5 to 10, ScanMapper.gridFor(0.5))          // tall plan
        assertEquals(10 to 4, ScanMapper.gridFor(4.0))          // clamped at MIN_GRID
        assertEquals(4 to 10, ScanMapper.gridFor(0.2))
        for (a in listOf(0.1, 0.33, 0.75, 1.0, 1.6, 3.0, 9.0)) {
            val (c, r) = ScanMapper.gridFor(a)
            assertTrue(c in ScanMapper.MIN_GRID..ScanMapper.MAX_GRID, "cols $c out of range for aspect $a")
            assertTrue(r in ScanMapper.MIN_GRID..ScanMapper.MAX_GRID, "rows $r out of range for aspect $a")
        }
    }

    @Test
    fun `⭐ a room too small to round to a cell is drawn as ONE cell, never rounded away`() {
        // This used to be a drop. A rectangle whose edges rounded together had no cells left and was
        // reported as DEGENERATE — and because the grid was framed on the whole PICTURE, a home
        // occupying a third of a builder's sheet made every room a third of its proper size, so the
        // rooms this happened to were the small ones: toilets, pooja niches, utilities. Ten rooms
        // across the thirty real plans, every one of them silently absent from a paid score.
        //
        // Framing on the home removes most of it; this floor removes the rest. A room may round SMALL
        // — one cell in the right place, which the user can see and resize — but it cannot vanish.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.0, 0.0, 1.0, 0.5),
                box("KITCHEN", 0.0, 0.5, 0.6, 0.48),
                box("MASTER BEDROOM", 0.6, 0.5, 0.4, 0.48),
                // In the sliver along the bottom wall: ~0.16 of a cell each way.
                box("TOILET", 0.40, 0.985, 0.02, 0.012),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        val toilet = placed.rooms.single { it.type == RoomType.TOILET }
        assertEquals(CellRect(4, 9, 1, 1), toilet.rect, "a sliver becomes one cell where it was read")
        assertTrue(placed.notes.dropped.none { it.reason == DropReason.DEGENERATE }, "nothing rounds away")
        assertEditorInvariants(placed)
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private fun outcomeNotes(out: ScanOutcome): ScanNotes = out.notes

    private fun assertEditorInvariants(placed: ScanOutcome.Placed) {
        assertTrue(placed.cols in ScanMapper.MIN_GRID..ScanMapper.MAX_GRID)
        assertTrue(placed.rows in ScanMapper.MIN_GRID..ScanMapper.MAX_GRID)
        val rects = placed.rooms.map { it.rect!! }
        for (r in rects) {
            assertTrue(r.w >= 1 && r.h >= 1, "room smaller than a cell: $r")
            assertTrue(r.col >= 0 && r.row >= 0, "room off the top/left of the grid: $r")
            assertTrue(r.right <= placed.cols && r.bottom <= placed.rows, "room off the grid: $r")
        }
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertTrue(!rects[i].overlaps(rects[j]), "overlap: ${rects[i]} vs ${rects[j]}")
            }
        }
    }
}
