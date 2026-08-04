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

    // ---- a room inside another room's rectangle SURVIVES (a measured reversal) -------------------

    @Test
    fun `⭐ a typed room drawn wholly inside another room is kept, and the big room gives way`() {
        // ⚠ This asserts the OPPOSITE of what it used to. The geometric sub-area drop was measured
        // across every recorded real reply (41 plans) and fired 24 times — every single one a real
        // scored room: nine toilets, a pooja, balconies, a study, the owner's own master-bedroom
        // toilet. Not one genuine dressing area ever reached it, because genuine sub-areas drop BY
        // NAME before geometry runs. A reader that lays out template rectangles draws real rooms
        // inside other rooms' boxes all the time; deleting them was deleting the home's toilets.
        val out = ScanMapper.map(
            draft(
                box("MASTER BEDROOM", 0.0, 0.0, 0.6, 0.6),
                box("STORE", 0.1, 0.1, 0.15, 0.15),   // wholly inside the bedroom's rectangle
                box("KITCHEN", 0.6, 0.0, 0.4, 0.6),
                box("LIVING ROOM", 0.0, 0.6, 1.0, 0.4),
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertTrue(placed.notes.dropped.isEmpty(), "nothing may be dropped: ${placed.notes.dropped}")
        val store = placed.rooms.single { it.type == RoomType.STORE }
        assertTrue(store.rect != null, "the contained room reaches the grid where it was read")
        val master = placed.rooms.single { it.type == RoomType.MASTER_BEDROOM }
        assertTrue(
            RoomFlag.OVERLAP_TRIMMED in master.flags,
            "the room it sat inside absorbs the cut and is flagged for the user",
        )
        assertEditorInvariants(placed)
    }

    @Test
    fun `adjacent rooms that merely touch survive with nothing dropped`() {
        val out = ScanMapper.map(goodDraft())
        assertTrue(outcomeNotes(out).dropped.isEmpty())
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
    fun `⭐⭐ the same rooms PLACE when the plan prints their sizes`() {
        // ⚠ THE GATE THAT THREW AWAY THE OWNER'S OWN FLAT. Fourteen rooms is an ordinary Indian
        // apartment — three balconies, a utility, a vestibule, a passage — and the room count binned
        // every one of them for being over a limit whose own comment called the cut "a judgement,
        // not a measurement".
        //
        // Where the rooms state their own dimensions, the reader's rectangles decide only which room
        // is left of which — the thing it gets right — and every measurement comes from text it
        // reads at ~95 %. So the identical layout that is refused above is trusted here, and the ONLY
        // difference is that these captions carry the size the plan prints.
        val names = listOf(
            "LIVING ROOM", "KITCHEN", "MASTER BEDROOM", "BEDROOM", "TOILET", "BATH", "POOJA",
            "DINING", "STUDY", "STORE", "BALCONY", "UTILITY", "CORRIDOR", "GUEST ROOM",
        )
        val boxes = names.mapIndexed { i, n ->
            ScanBox(
                label = n,
                x = (i % 5) * 0.2, y = (i / 5) * 0.33,
                w = 0.10 + (i % 4) * 0.03, h = 0.15 + (i % 3) * 0.08,
                confidence = 0.9,
                printedSize = "${10 + i}'-0\" X ${8 + (i % 5)}'-0\"",
            )
        }
        val out = ScanMapper.map(draft(*boxes.toTypedArray()))
        assertIs<ScanOutcome.Placed>(out, "rooms that state their own sizes are placeable")
    }

    @Test
    fun `⭐ sizes with ASCII fractions count as sizes at the gate`() {
        // The furnished-render regression (plan doc §3p): the sheet prints ½ but the reader TYPES
        // 1/2, the glyph-only parser failed those pairs, and an 18-room reply carrying a printed
        // size on nearly every room was refused as TOO_MANY_ROOMS. The same layout as above, with
        // every second caption in the reader's ASCII form, must still count as sized and place.
        val names = listOf(
            "LIVING ROOM", "KITCHEN", "MASTER BEDROOM", "BEDROOM", "TOILET", "BATH", "POOJA",
            "DINING", "STUDY", "STORE", "BALCONY", "UTILITY", "CORRIDOR", "GUEST ROOM",
        )
        val boxes = names.mapIndexed { i, n ->
            ScanBox(
                label = n,
                x = (i % 5) * 0.2, y = (i / 5) * 0.33,
                w = 0.10 + (i % 4) * 0.03, h = 0.15 + (i % 3) * 0.08,
                confidence = 0.9,
                printedSize = if (i % 2 == 0) {
                    "${10 + i}'-71/2\" X ${8 + (i % 5)}'-31/2\""
                } else {
                    "${10 + i}'-0\" X ${8 + (i % 5)}'-0\""
                },
            )
        }
        val out = ScanMapper.map(draft(*boxes.toTypedArray()))
        assertIs<ScanOutcome.Placed>(out, "ASCII-fraction sizes must count at the gate")
    }

    @Test
    fun `⭐ a sheet that names a lift is a whole floor, not one home`() {
        // The distinction the room count was proxying for, said outright. Measured across the
        // 30-plan corpus: every sheet naming a lift is a shared floor plate, and no single-home plan
        // names one — including three genuine 21-room villas.
        val out = ScanMapper.map(
            draft(
                ScanBox("LIVING ROOM", 0.0, 0.0, 0.4, 0.4, 0.9, "12'-0\" X 14'-0\""),
                ScanBox("KITCHEN", 0.4, 0.0, 0.3, 0.3, 0.9, "9'-0\" X 11'-0\""),
                ScanBox("MASTER BEDROOM", 0.0, 0.4, 0.4, 0.4, 0.9, "13'-0\" X 12'-0\""),
                ScanBox("TOILET", 0.4, 0.4, 0.2, 0.2, 0.9, "5'-0\" X 7'-0\""),
                ScanBox("LIFT", 0.7, 0.0, 0.2, 0.2, 0.9, "6'-0\" X 6'-0\""),
            ),
        )
        val assisted = assertIs<ScanOutcome.Assisted>(out)
        assertEquals(AssistReason.FLOOR_PLATE, assisted.reason)
        assertTrue(assisted.rooms.isNotEmpty(), "the room list still survives — that is the product")
        assertTrue(assisted.rooms.all { it.rect == null }, "Assisted must not carry geometry")
    }

    @Test
    fun `⭐⭐ a LAYOUT that runs past the bottom of the page is shrunk onto it, never deleted`() {
        // ⚠ The reader routinely returns rooms outside the unit square — it lays out a template and
        // the template runs long. Three of the fifteen on the owner's own sheet did. Cutting them
        // off at the edge DELETED one of his three balconies outright and flattened his utility to
        // a third of its depth, before anything else in the mapper had run — and a room that never
        // arrives is one the user cannot correct.
        val out = ScanMapper.map(
            draft(
                box("LIVING ROOM", 0.05, 0.05, 0.4, 0.3),
                box("KITCHEN", 0.5, 0.05, 0.3, 0.3),
                box("MASTER BEDROOM", 0.05, 0.4, 0.4, 0.3),
                box("TOILET", 0.5, 0.4, 0.2, 0.2),
                box("STORE", 0.5, 0.9, 0.3, 0.15),      // the tail of the template, running long…
                box("BALCONY", 0.05, 1.05, 0.3, 0.15),  // …and off the bottom with it
            ),
        )
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertTrue(
            placed.rooms.any { it.type == RoomType.BALCONY },
            "the room past the bottom of the page must be shrunk onto it, not amputated: " +
                placed.rooms.map { it.label },
        )
    }

    @Test
    fun `⚠ ONE room past the edge is that room being wrong, and is still clamped or dropped`() {
        // The safety rail on the change above, and the tests that caught its absence. Shrinking a
        // whole home to accommodate a single hallucinated rectangle would let one bad box pull every
        // real room smaller — the "one stray rectangle inflates the frame" failure this file warns
        // about. So the shrink needs the overrun to be SHARED. See ScanMapper.MIN_SPILLED_ROOMS.
        val one = listOf(
            box("LIVING ROOM", 0.0, 0.0, 1.0, 0.6),
            box("KITCHEN", 0.8, 0.6, 0.5, 0.4), // the only room out of bounds
            box("MASTER BEDROOM", 0.0, 0.6, 0.8, 0.4),
        )
        assertEquals(one, ScanMapper.shrinkToPage(one), "one stray room must not rescale the home")
    }

    @Test
    fun `shrinking to the page leaves a reply that already fits exactly as it was`() {
        // One scale and one offset applied to every room cancel exactly against a grid framed on the
        // rooms' own bounding box, so this must be a literal no-op for anything already in the page.
        val rooms = goodDraft().rooms
        assertEquals(rooms, ScanMapper.shrinkToPage(rooms))
    }

    @Test
    fun `a reply nowhere near the page is left to the clamp`() {
        // Beyond twice the page it is not a layout at all, and pretending otherwise would let two
        // absurd rectangles shrink a whole home into a corner.
        val rooms = listOf(
            box("LIVING ROOM", 0.0, 0.0, 0.5, 0.5),
            box("KITCHEN", 0.0, 8.0, 0.5, 0.5),
            box("TOILET", 0.0, 9.0, 0.5, 0.5),
        )
        assertEquals(rooms, ScanMapper.shrinkToPage(rooms))
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

    // ---- the tint frame (prompt v4 building box, owner 4 Aug 2026) ---------------------------------

    /**
     * The on-photo tint's fix: a sane building box composes each room's SOURCE onto the page;
     * no box, or a mad one, leaves the source exactly as today. Placement maths never reads it.
     */
    @Test
    fun `a sane building box composes the tint source onto the page`() {
        val b = ScanBox(x = 0.25, y = 0.10, w = 0.50, h = 0.80)
        val room = ScanBox(label = "KITCHEN", x = 0.0, y = 0.0, w = 1.0, h = 0.5)
        val page = ScanMapper.pageSource(b, room)
        assertEquals(0.25, page.x, 1e-9)
        assertEquals(0.10, page.y, 1e-9)
        assertEquals(0.50, page.w, 1e-9)
        assertEquals(0.40, page.h, 1e-9)
        assertEquals("KITCHEN", page.label, "everything but the frame survives")
    }

    @Test
    fun `no building box, or a mad one, leaves the tint source untouched`() {
        val room = ScanBox(label = "LOBBY", x = 0.2, y = 0.2, w = 0.3, h = 0.3)
        assertEquals(room, ScanMapper.pageSource(null, room), "older replies pass through")
        assertEquals(room, ScanMapper.pageSource(ScanBox(x = 0.0, y = 0.0, w = 0.01, h = 0.9), room), "a sliver is not a building")
        assertEquals(room, ScanMapper.pageSource(ScanBox(x = 0.9, y = 0.0, w = 0.5, h = 0.9), room), "a box off the page is ignored")
    }

    @Test
    fun `a v4 reply's building box is parsed and reaches every source box`() {
        val draft = RecordedScans.parseDraft(
            """{"planType":"2D_PLAN","hasRoomLabels":true,
                "building":{"x":0.4,"y":0.2,"w":0.5,"h":0.6},
                "rooms":[
                  {"label":"KITCHEN","size":"2920X2100","x":0.0,"y":0.0,"w":0.5,"h":0.5,"confidence":0.9},
                  {"label":"LOBBY","size":"3050X3900","x":0.5,"y":0.5,"w":0.5,"h":0.5,"confidence":0.9}
                ],"planConfidence":0.9}""",
        )
        checkNotNull(draft)
        val building = checkNotNull(draft.building) { "the building box must parse" }
        assertEquals(0.5, building.w, 1e-9)
        val out = ScanMapper.map(draft)
        val sources = out.scannedRoomsForTest().map { it.source!! }
        assertTrue(sources.all { it.x >= 0.4 - 1e-9 && it.x + it.w <= 0.9 + 1e-9 },
            "every tint stays inside the building's page box: $sources")
    }

    private fun ScanOutcome.scannedRoomsForTest(): List<ScannedRoom> = when (this) {
        is ScanOutcome.Placed -> rooms
        is ScanOutcome.Assisted -> rooms
        is ScanOutcome.Refused -> emptyList()
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
