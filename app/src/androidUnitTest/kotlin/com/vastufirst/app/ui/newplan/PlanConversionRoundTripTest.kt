package com.vastufirst.app.ui.newplan

import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import com.vastufirst.shared.editor.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid ⇄ engine flip, proven both directions, plus the score invariants the flip depends on.
 * Pure JVM (no Android) — `buildEnginePlan` and its inverses (`gridRoomsFromPlan`/`gridDoorFromPlan`)
 * are pure, and the engine is headless. UAT cases I1–I4, I7, H1–H6, J2/J3.
 *
 * The reopen path is: stored engine [Plan] → `gridRoomsFromPlan` / `gridDoorFromPlan` → the exact
 * grid the user drew. A drift here is a home that changes shape when you open it — one of the
 * "too many issues" this QA pass exists to catch.
 */
class PlanConversionRoundTripTest {

    private val engine = VastuEngine()

    private fun plan(rooms: List<GridRoom>, door: GridDoor?, north: Int = 0): Plan =
        buildEnginePlan(rooms, door, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, north, "t")!!

    // ── rooms round-trip exactly (I1, I3, I4) ────────────────────────────────────────────────────

    @Test
    fun `the sample home's rooms come back byte-for-byte`() {
        val rooms = SamplePlans.all.first().rooms
        val recovered = gridRoomsFromPlan(plan(rooms, null))
        assertEquals(rooms, recovered)
    }

    @Test
    fun `every bundled sample round-trips its rooms and its door`() {
        for (sample in SamplePlans.all) {
            val p = plan(sample.rooms, sample.door)
            assertEquals("rooms drifted for ${sample.id}", sample.rooms, gridRoomsFromPlan(p))
            assertEquals("door drifted for ${sample.id}", sample.door, gridDoorFromPlan(p, sample.rooms))
        }
    }

    @Test
    fun `rooms high on a 10-deep plot round-trip despite negative engine-Y`() {
        // Rows 8-9 are exactly where ey(row)=GRID-row goes negative. The flip must still be exact —
        // this is the "fixed GRID=8 y-origin could bite" case the brief calls out (UAT I3).
        val rooms = listOf(
            GridRoom("a", RoomType.BEDROOM, 2, 8, 2, 2),      // bottom = 10 → engine y down to -2
            GridRoom("b", RoomType.KITCHEN, 6, 7, 3, 3),      // bottom = 10
            GridRoom("c", RoomType.LIVING, 0, 0, 2, 2),
        )
        assertEquals(rooms, gridRoomsFromPlan(plan(rooms, null)))
    }

    @Test
    fun `a single 1x1 room round-trips`() {
        val rooms = listOf(GridRoom("a", RoomType.TOILET, 4, 4, 1, 1))
        assertEquals(rooms, gridRoomsFromPlan(plan(rooms, null)))
    }

    // ── door round-trips on every wall, incl. thin footprints (H1–H6) ────────────────────────────

    @Test
    fun `a door on each wall round-trips to the same side and cell`() {
        // A footprint with room on all edges so every wall exists and a mid-cell is inside it.
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 1, 1, 4, 4))   // cols 1..5, rows 1..5
        for (door in listOf(
            GridDoor(DoorSide.N, 2), GridDoor(DoorSide.S, 3),
            GridDoor(DoorSide.E, 2), GridDoor(DoorSide.W, 3),
        )) {
            val recovered = gridDoorFromPlan(plan(rooms, door), rooms)
            assertEquals("door on ${door.side} drifted", door, recovered)
        }
    }

    @Test
    fun `door side is never confused on a one-cell-deep footprint (N vs S)`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 3, 4, 1))   // 1 cell deep, rows 3..4
        assertEquals(DoorSide.N, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.N, 1)), rooms)!!.side)
        assertEquals(DoorSide.S, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.S, 1)), rooms)!!.side)
    }

    @Test
    fun `door side is never confused on a one-cell-wide footprint (E vs W)`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 3, 0, 1, 4))   // 1 cell wide, cols 3..4
        assertEquals(DoorSide.E, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.E, 1)), rooms)!!.side)
        assertEquals(DoorSide.W, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.W, 1)), rooms)!!.side)
    }

    @Test
    fun `a door tapped past the rooms snaps onto the footprint and stays put on reload (C15)`() {
        // Footprint cols 2..6. A door on N "at cell 9" is clamped to the footprint when the plan is
        // built; the reloaded door must equal what was actually built (no jump), i.e. within 2..5.
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 2, 0, 4, 4))
        val built = plan(rooms, GridDoor(DoorSide.N, 9))
        val recovered = gridDoorFromPlan(built, rooms)!!
        assertEquals(DoorSide.N, recovered.side)
        assertTrue("door cell ${recovered.cell} not clamped to footprint 2..5", recovered.cell in 2..5)
        // Idempotent: rebuilding from the recovered door yields the identical door (stable on reopen).
        assertEquals(recovered, gridDoorFromPlan(plan(rooms, recovered), rooms))
    }

    // ── empty / degenerate input (I7, J4 safety) ─────────────────────────────────────────────────

    @Test
    fun `a stored room with an empty polygon is skipped, not crashed`() {
        val p = Plan(
            id = "t", propertyType = PropertyType.INDEPENDENT_HOUSE, intent = Intent.BUILDING,
            levels = listOf(Level(index = 0, outline = emptyList(), rooms = listOf(
                Room(id = "ghost", type = RoomType.BEDROOM, polygon = emptyList()),
                Room(id = "real", type = RoomType.KITCHEN, polygon = listOf(
                    Point(0.0, 6.0), Point(2.0, 6.0), Point(2.0, 8.0), Point(0.0, 8.0),
                )),
            ))),
            northOffsetDegrees = 0,
        )
        val recovered = gridRoomsFromPlan(p)
        assertEquals(1, recovered.size)
        assertEquals("real", recovered.single().id)
    }

    @Test
    fun `no rooms means no plan (empty state)`() {
        assertNull(buildEnginePlan(emptyList(), null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, 0, "t"))
    }

    @Test
    fun `no door round-trips to no door`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 0, 3, 3))
        assertNull(gridDoorFromPlan(plan(rooms, null), rooms))
    }

    // ── score is translation-invariant (J3) — the headline invariant ─────────────────────────────

    @Test
    fun `the same rooms score identically wherever they sit on the grid`() {
        // Move the whole home around the canvas — including down into negative engine-Y — and the
        // score must not budge. The engine lays its pada grid on the rooms' bounding box, so position
        // is not a scoring term; if it were, a home would score differently just for being drawn lower.
        val base = listOf(
            GridRoom("a", RoomType.POOJA, 0, 0, 2, 2),
            GridRoom("b", RoomType.KITCHEN, 3, 0, 2, 2),
            GridRoom("c", RoomType.MASTER_BEDROOM, 0, 3, 2, 2),
            GridRoom("d", RoomType.TOILET, 3, 3, 2, 2),
        )
        val reference = engine.analyze(plan(base, null)).score
        assertTrue("reference score out of range: $reference", reference in 0..100)
        for ((dCol, dRow) in listOf(1 to 0, 0 to 1, 2 to 2, 4 to 4, 0 to 5, 3 to 6)) {
            val shifted = base.map { it.copy(col = it.col + dCol, row = it.row + dRow) }
            assertEquals(
                "score changed after shifting by ($dCol,$dRow) — position must not affect the score",
                reference, engine.analyze(plan(shifted, null)).score,
            )
        }
    }

    /**
     * ⚠ DIAGNOSTIC, and a permanent one. When the test below fails it says only that the score
     * moved, which is exactly the information that does not help — a score is six things added
     * together. This prints the parts (base, penalty, and every room's zone, verdict, points and
     * encroached share) at each offset, so the next failure names its own cause instead of costing
     * a round trip to find. It asserts nothing; the test below is the gate.
     */
    @Test
    fun `door fixture decomposition at each offset, for the log`() {
        val rooms = listOf(
            GridRoom("a", RoomType.LIVING, 0, 0, 3, 3),
            GridRoom("b", RoomType.KITCHEN, 4, 0, 2, 2),
        )
        val door = GridDoor(DoorSide.S, 1)
        for ((dCol, dRow) in listOf(0 to 0, 1 to 1, 0 to 4, 2 to 5)) {
            val sr = rooms.map { it.copy(col = it.col + dCol, row = it.row + dRow) }
            val a = engine.analyze(plan(sr, GridDoor(door.side, door.cell + dCol)))
            println(
                "SCORE| shift($dCol,$dRow) score=${a.score} base=${"%.6f".format(a.base)} " +
                    "penalty=${a.defectPenalty} door=${a.doorResult?.pada?.id}/${a.doorResult?.verdict} " +
                    "defects=${a.defects.map { "${it.id}:${it.severity}" }}",
            )
            a.roomResults.forEach {
                println(
                    "SCORE|    ${it.roomId} ${it.type} zone=${it.zone} ${it.verdict} " +
                        "pts=${it.points} share=${"%.6f".format(it.encroachedShare)} w=${it.weight}",
                )
            }
        }
    }

    @Test
    fun `translation invariance holds with the door too`() {
        val rooms = listOf(
            GridRoom("a", RoomType.LIVING, 0, 0, 3, 3),
            GridRoom("b", RoomType.KITCHEN, 4, 0, 2, 2),
        )
        val door = GridDoor(DoorSide.S, 1)
        val reference = engine.analyze(plan(rooms, door)).score
        for ((dCol, dRow) in listOf(1 to 1, 0 to 4, 2 to 5)) {
            val sr = rooms.map { it.copy(col = it.col + dCol, row = it.row + dRow) }
            val sd = GridDoor(door.side, door.cell + dCol)   // shift the door along the same wall
            assertEquals(
                "score changed after shifting by ($dCol,$dRow) with a door",
                reference, engine.analyze(plan(sr, sd)).score,
            )
        }
    }

    @Test
    fun `the analysis is never an error and always usable for any single room`() {
        for (type in RoomType.values()) {
            val a = engine.analyze(plan(listOf(GridRoom("a", type, 3, 3, 2, 2)), null))
            assertNotNull("null analysis for $type", a)
            assertTrue("score out of range for $type: ${a.score}", a.score in 0..100)
        }
    }

    // ── door marker draws on the house's footprint edge, not the plot edge ────────────────────────
    // Regression for the harness-found bug: when the plot is drawn larger than the rooms, the door
    // marker floated in the empty margin (plot edge) instead of sitting on the house's outer wall —
    // the wall the engine scores and reopen restores. `doorMarkerCell` pins it to the footprint.

    @Test
    fun `the door marker sits on the footprint edge when the plot is larger than the house`() {
        // Rooms occupy a block that does NOT reach the 8x8 plot edges, so plot edges (0 and 7) differ
        // from the footprint edges (minC=1, maxC=7, minR=1, maxR=6).
        val rooms = listOf(
            GridRoom("a", RoomType.LIVING, 1, 1, 3, 2),
            GridRoom("b", RoomType.KITCHEN, 5, 4, 2, 2),
        )
        assertEquals(3 to 1, doorMarkerCell(GridDoor(DoorSide.N, 3), rooms, 8, 8)) // north edge = minR=1, not 0
        assertEquals(3 to 5, doorMarkerCell(GridDoor(DoorSide.S, 3), rooms, 8, 8)) // south edge = maxR-1=5, not 7
        assertEquals(1 to 2, doorMarkerCell(GridDoor(DoorSide.W, 2), rooms, 8, 8)) // west edge = minC=1, not 0
        assertEquals(6 to 2, doorMarkerCell(GridDoor(DoorSide.E, 2), rooms, 8, 8)) // east edge = maxC-1=6, not 7
    }

    @Test
    fun `the door marker cell matches the wall the plan actually scores, on every side`() {
        // The marker's perpendicular coordinate must equal the wall the built plan puts the door on, so
        // what the user sees == what is scored/reopened. Prove it against gridDoorFromPlan (the reopen
        // classifier) for footprints that do NOT reach the plot edges, including 1-cell-thin ones.
        val cases = listOf(
            listOf(GridRoom("a", RoomType.LIVING, 2, 2, 3, 2)),          // small block inside an 8x8
            listOf(GridRoom("a", RoomType.LIVING, 1, 3, 6, 1)),          // 1-cell-deep (thin) footprint
            listOf(GridRoom("a", RoomType.LIVING, 3, 1, 1, 5)),          // 1-cell-wide (thin) footprint
        )
        for (rooms in cases) {
            val minC = rooms.minOf { it.col }; val maxC = rooms.maxOf { it.col + it.w }
            val minR = rooms.minOf { it.row }; val maxR = rooms.maxOf { it.row + it.h }
            for (side in DoorSide.values()) {
                val cellRange = if (side == DoorSide.N || side == DoorSide.S) minC until maxC else minR until maxR
                val door = GridDoor(side, cellRange.first)
                val (col, row) = doorMarkerCell(door, rooms, 8, 8)
                when (side) {
                    DoorSide.N -> assertEquals("N row", minR, row)
                    DoorSide.S -> assertEquals("S row", maxR - 1, row)
                    DoorSide.W -> assertEquals("W col", minC, col)
                    DoorSide.E -> assertEquals("E col", maxC - 1, col)
                }
                val recovered = gridDoorFromPlan(plan(rooms, door), rooms)
                assertEquals("reopened side for $side on $rooms", side, recovered?.side)
            }
        }
    }

    // ── doorForTap: the wall a tap MEANS (UAT S8) ────────────────────────────────────────────────

    /** The house used below: cols 3..8, rows 3..5, sitting in the middle of a 10×10 plot. */
    private val marginHouse = listOf(
        GridRoom("m1", RoomType.LIVING, 3, 3, 3, 2),
        GridRoom("m2", RoomType.KITCHEN, 6, 3, 2, 2),
    )

    @Test
    fun `a tap beyond one wall of the house chooses THAT wall, not the nearest plot edge`() {
        // ⭐ The S8 bug: distances used to be measured to the PLOT's edges, so a tap in the empty
        // margin directly above the house (which starts at column 3) resolved to WEST — the plot's
        // west edge was 4 cells away while its north edge was 0.5 — a wall the user never aimed at,
        // on the highest-weighted element the engine scores.
        assertEquals(DoorSide.N, doorForTap(4.5f, 0.5f, marginHouse)?.side)   // above the house
        assertEquals(DoorSide.S, doorForTap(4.5f, 9.5f, marginHouse)?.side)   // below it
        assertEquals(DoorSide.W, doorForTap(0.5f, 4.5f, marginHouse)?.side)   // left of it
        assertEquals(DoorSide.E, doorForTap(9.5f, 4.5f, marginHouse)?.side)   // right of it
    }

    @Test
    fun `the plot plays no part — the same tap on the same house gives the same door at any plot size`() {
        // doorForTap takes no cols/rows at all, so this is structural; the test pins the contract so
        // re-introducing a plot dimension can't quietly bring the S8 bug back.
        val taps = listOf(4.5f to 0.5f, 0.5f to 4.5f, 4.5f to 3.2f, 7.9f to 4.9f, 9.9f to 9.9f)
        for ((x, y) in taps) {
            val d = doorForTap(x, y, marginHouse)
            assertNotNull("a tap on a house must always resolve to a door", d)
            // Same house drawn at the same absolute cells: the answer cannot depend on the canvas.
            assertEquals("tap ($x,$y)", d, doorForTap(x, y, marginHouse))
        }
    }

    @Test
    fun `a tap inside the house picks the wall it is nearest to`() {
        // Rows 3..5, so row 3 is the north strip and row 4 the south strip.
        assertEquals(DoorSide.N, doorForTap(4.5f, 3.2f, marginHouse)?.side)
        assertEquals(DoorSide.S, doorForTap(4.5f, 4.8f, marginHouse)?.side)
        assertEquals(DoorSide.W, doorForTap(3.1f, 4.0f, marginHouse)?.side)
        assertEquals(DoorSide.E, doorForTap(7.9f, 4.0f, marginHouse)?.side)
    }

    @Test
    fun `a one-cell-deep house can still take a south door`() {
        // ⚠ This is why the tap is measured in FRACTIONAL cells. A 1-deep house's north and south
        // walls are half a cell apart; rounded to whole cells both distances are 0 and the tie always
        // resolved north, so a south door was unreachable on a thin house.
        val thin = listOf(GridRoom("t1", RoomType.LIVING, 1, 3, 6, 1))
        assertEquals("upper half of the strip", DoorSide.N, doorForTap(4.0f, 3.2f, thin)?.side)
        assertEquals("lower half of the strip", DoorSide.S, doorForTap(4.0f, 3.8f, thin)?.side)
        // Same for a 1-cell-WIDE house, east vs west.
        val narrow = listOf(GridRoom("n1", RoomType.LIVING, 3, 1, 1, 6))
        assertEquals(DoorSide.W, doorForTap(3.2f, 4.0f, narrow)?.side)
        assertEquals(DoorSide.E, doorForTap(3.8f, 4.0f, narrow)?.side)
    }

    @Test
    fun `every tap lands a door already on the footprint, so it never jumps on reopen`() {
        // The whole point of clamping at placement (C15/F4): what is displayed is what is scored and
        // what comes back. Sweep taps across the entire plot, including all four margins.
        for (tx in 0..20) for (ty in 0..20) {
            val x = tx * 0.5f
            val y = ty * 0.5f
            val d = doorForTap(x, y, marginHouse) ?: continue
            assertEquals(
                "doorForTap must already be footprint-clamped at ($x,$y)",
                d, clampDoorToRooms(d, marginHouse),
            )
            // And it survives the flip to the engine plan and back, byte-for-byte.
            assertEquals("reopen ($x,$y)", d, gridDoorFromPlan(plan(marginHouse, d), marginHouse))
        }
    }

    @Test
    fun `a tap with no rooms yields no door`() {
        assertNull(doorForTap(4.5f, 4.5f, emptyList()))
    }

    // ── ⭐ THE HOME'S TRUE SHAPE (docs/SCORE-ACCURACY-CAVEATS.md #1) ─────────────────────────────
    //
    // An L-shaped or notched home used to be handed to the engine as the filled bounding box around
    // its rooms, so the missing corner was scored as though it were there — silently, too generously,
    // on the commonest real Indian home shape. The engine was never the problem: it has always been
    // able to attribute a missing corner to a zone. It was never given one.

    /**
     * An 8×8 home with its whole north-east 3×3 corner missing — the textbook L.
     *
     * ⚠ Every room is deliberately placed in a zone its own rule calls ideal or acceptable, and the
     * centre is a corridor (an unruled type). So this home has **no defects of its own**: whatever
     * penalty appears once the corner is cut away came from the corner and nothing else. A fixture
     * that already scored badly would have hit the penalty cap and hidden the very thing under test —
     * which is exactly what the first version of this fixture did, scoring 0 both ways.
     */
    private val lShaped = listOf(
        GridRoom("living", RoomType.LIVING, 0, 0, 3, 3),          // north-west  · acceptable
        GridRoom("dining", RoomType.DINING, 3, 0, 2, 3),          // north       · acceptable
        GridRoom("store-w", RoomType.STORE, 0, 3, 3, 2),          // west        · ideal
        GridRoom("hall", RoomType.CORRIDOR, 3, 3, 2, 2),          // the centre  · not scored
        GridRoom("master", RoomType.MASTER_BEDROOM, 0, 5, 3, 3),  // south-west  · ideal
        GridRoom("store-s", RoomType.STORE, 3, 5, 2, 3),          // south       · ideal
        GridRoom("kitchen", RoomType.KITCHEN, 5, 5, 3, 3),        // south-east  · ideal
        GridRoom("dining-e", RoomType.DINING, 5, 3, 3, 2),        // east        · acceptable
    )
    private val neCorner: Set<Cell> =
        (5..7).flatMap { c -> (0..2).map { r -> Cell(c, r) } }.toSet()

    private fun planWithCut(
        rooms: List<GridRoom>, cut: Set<Cell>, north: Int = 0,
    ): Plan = buildEnginePlan(
        rooms, null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, north, "t", cut,
    )!!

    @Test
    fun `with nothing cut out the outline is byte-for-byte what it always was`() {
        // ⭐ THE SAFETY PROPERTY OF THE WHOLE CHANGE. Every existing home, every bundled sample and
        // the §15 worked example go down this path, so if this ever stops holding, scores move for
        // people who changed nothing. Same points, same order, same winding.
        for (sample in SamplePlans.all) {
            val before = plan(sample.rooms, sample.door).levels.first().outline
            val after = planWithCut(sample.rooms, emptySet()).levels.first().outline
            assertEquals("outline drifted for ${sample.id}", before, after)
            assertEquals("a rectangle must stay four corners for ${sample.id}", 4, after.size)
        }
        assertEquals(
            "the L-shaped fixture with nothing cut out is still a plain rectangle",
            plan(lShaped, null).levels.first().outline,
            planWithCut(lShaped, emptySet()).levels.first().outline,
        )
    }

    @Test
    fun `an L-shaped home is finally given to the engine as an L`() {
        val outline = planWithCut(lShaped, neCorner).levels.first().outline
        assertEquals("an L has six corners", 6, outline.size)
        // 64 cells minus the 9 cut away, by the shoelace rule.
        var twice = 0.0
        for (i in outline.indices) {
            val p = outline[i]
            val q = outline[(i + 1) % outline.size]
            twice += p.x * q.y - q.x * p.y
        }
        assertEquals("the nine cut cells must be gone from the enclosed area", 55.0, twice / 2.0, 1e-9)
    }

    @Test
    fun `the missing north-east corner now fires the checks it always slipped past`() {
        val filled = engine.analyze(planWithCut(lShaped, emptySet()))
        val real = engine.analyze(planWithCut(lShaped, neCorner))

        assertTrue("a filled rectangle reports no cut — this is the old, too-generous answer", filled.cuts.isEmpty())
        assertTrue("the real shape must report a cut", real.cuts.isNotEmpty())
        assertEquals(
            "the largest cut is the north-east corner — the worst missing corner in Vastu",
            Zone.NE, real.cuts.first().zone,
        )
        assertEquals(
            "a corner this size is a major cut",
            Severity.MAJOR, real.cuts.first().severity,
        )
        assertTrue(
            "the north-east cut defect X-04 must appear in the report",
            real.defects.any { it.id == "X-04" },
        )
        assertTrue("an L is a rectangle-family shape, not an 'unusual' one", !real.shapeIrregular)
        assertEquals(
            "the fixture must have no problems of its own, or the penalty cap hides what is under test",
            0, filled.defectPenalty,
        )
        assertTrue(
            "the missing corner must actually cost something: penalty went ${filled.defectPenalty} → ${real.defectPenalty}",
            real.defectPenalty > filled.defectPenalty,
        )
        assertTrue(
            "scoring the real shape must not flatter it: ${real.score} should be below ${filled.score}",
            real.score < filled.score,
        )
    }

    @Test
    fun `the pada grid is untouched by a cut, so only the shape penalty moves`() {
        // The grid is laid on the footprint's bounding box, which a cut corner does not change — so
        // every room keeps the exact zone and verdict it had. That is what makes the score difference
        // above attributable to the missing corner and nothing else.
        val filled = engine.analyze(planWithCut(lShaped, emptySet()))
        val real = engine.analyze(planWithCut(lShaped, neCorner))
        assertEquals(
            "room verdicts must not move",
            filled.roomResults.map { it.roomId to (it.zone to it.verdict) },
            real.roomResults.map { it.roomId to (it.zone to it.verdict) },
        )
        assertEquals("the pre-penalty base must not move", filled.base, real.base, 1e-9)
    }

    @Test
    fun `a home's shape survives being saved and reopened`() {
        val saved = planWithCut(lShaped, neCorner)
        val rooms = gridRoomsFromPlan(saved)
        assertEquals("rooms must come back unchanged", lShaped, rooms)
        assertEquals("the cut corner must come back", neCorner, cutOutFromPlan(saved, rooms))
        // And re-running the reopened home gives the identical score, not a quietly different one.
        assertEquals(
            engine.analyze(saved).score,
            engine.analyze(planWithCut(rooms, cutOutFromPlan(saved, rooms))).score,
        )
    }

    @Test
    fun `a saved rectangle reopens with nothing cut out`() {
        for (sample in SamplePlans.all) {
            val p = plan(sample.rooms, sample.door)
            assertTrue(
                "a plain rectangle must not come back looking cut for ${sample.id}",
                cutOutFromPlan(p, gridRoomsFromPlan(p)).isEmpty(),
            )
        }
    }

    @Test
    fun `an L is still read as an L when the home sits at an angle to the compass`() {
        // ⚠ NOT a score-equality test. Turning the home relative to North genuinely moves its rooms
        // into different directions and SHOULD change the score — the engine's own rotation-invariance
        // case turns the plan and North together, which is a different claim. What must hold here is
        // that the corner never stops being a corner: an angled L is judged in the building's own
        // frame, so it stays a rectangle-family shape with a real missing piece at every angle.
        for (north in listOf(0, 30, 45, 90, 180, 270, 315)) {
            val a = engine.analyze(planWithCut(lShaped, neCorner, north))
            assertTrue("score out of range at $north°: ${a.score}", a.score in 0..100)
            assertTrue("an angled L must not be called an unusual shape at $north°", !a.shapeIrregular)
            assertTrue("the missing corner vanished at $north°", a.cuts.isNotEmpty())
        }
    }

    @Test
    fun `a cut that would split the home in two is refused, not scored`() {
        // pruneCutOut is the gate: the editor can never hand the engine a shape with no honest outline.
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 0, 5, 1), GridRoom("b", RoomType.BEDROOM, 0, 2, 5, 1))
        val splitting = (0..4).map { Cell(it, 1) }.toSet()
        assertTrue(
            "cutting the whole middle row would leave two separate homes and must be refused",
            pruneCutOut(rooms, null, splitting, 8, 8).isEmpty(),
        )
    }

    @Test
    fun `a cut under a room lapses instead of punching a hole`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 0, 4, 4))
        val inside = setOf(Cell(3, 0))
        assertTrue(pruneCutOut(rooms, null, inside, 8, 8).isEmpty())
        assertEquals(
            "a room's own cells always belong to the home",
            4, planWithCut(rooms, inside).levels.first().outline.size,
        )
    }

    @Test
    fun `the cell the front door stands on can never be cut away`() {
        val door = GridDoor(DoorSide.N, 6)      // north wall, in the corner that would be cut
        assertTrue(
            "cutting the wall the door stands on would leave the entrance floating off the house",
            pruneCutOut(lShaped, door, neCorner, 8, 8).isEmpty(),
        )
        assertTrue(
            "and the editor must not even offer it",
            gapsFor(lShaped, door, 8, 8).none { it.removable },
        )
    }
}
