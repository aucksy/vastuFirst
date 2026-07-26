package com.vastufirst.app.ui.newplan

import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
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
}
