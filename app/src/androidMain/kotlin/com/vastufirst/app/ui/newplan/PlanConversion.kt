package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.fixturesFor
import com.vastufirst.app.ui.details.siteAnswersFromPlan
import com.vastufirst.app.ui.details.siteFor
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.Cell
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.Footprint
import com.vastufirst.shared.editor.Gap
import com.vastufirst.shared.editor.GridPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Grid → engine [Plan] conversion, as a PURE function (Product PRD §4.1).
 *
 * Lifted verbatim out of [NewPlanViewModel.buildPlan] so the screenshot harness can build the exact
 * same engine input the app builds — and therefore render the score-driven screens (Mark North,
 * Score, Report) against a real, engine-computed Analysis instead of a hand-faked one. The ViewModel
 * now delegates here, so there is ONE source of truth for the flip and the door geometry.
 *
 * Grid rows increase DOWNWARD; engine Y increases NORTH (up), so rows are flipped. Cell units are
 * used directly as (arbitrary, self-consistent) plan units — the engine is scale-free.
 */
fun buildEnginePlan(
    rooms: List<GridRoom>,
    door: GridDoor?,
    intent: Intent?,
    propertyType: PropertyType,
    north: Int,
    planId: String,
    /** Cells of the bounding box the user has told us are NOT part of their home — the missing corner
     *  of an L, a notch. Empty (the default) reproduces the bounding-box footprint byte for byte. */
    cutOut: Set<Cell> = emptySet(),
    /** The optional extras — water tank, tree, road — that let the rest of the engine's rules run. */
    siteAnswers: SiteAnswers = SiteAnswers(),
): Plan? {
    val theIntent = intent ?: return null
    if (rooms.isEmpty()) return null

    fun ex(col: Int) = col.toDouble()                 // east grows with column
    fun ey(row: Int) = (GRID - row).toDouble()        // north grows as row decreases

    val engineRooms = rooms.map { r ->
        val x0 = ex(r.col); val x1 = ex(r.col + r.w)
        val yTop = ey(r.row); val yBottom = ey(r.row + r.h)
        Room(
            id = r.id,
            type = r.type,
            polygon = listOf(
                Point(x0, yBottom), Point(x1, yBottom), Point(x1, yTop), Point(x0, yTop),
            ),
        )
    }

    val minC = rooms.minOf { it.col }
    val maxC = rooms.maxOf { it.col + it.w }
    val minR = rooms.minOf { it.row }
    val maxR = rooms.maxOf { it.row + it.h }
    // ⭐ The footprint is the home's TRUE outline — the bounding box MINUS whatever the user told us
    // is not their home (docs/SCORE-ACCURACY-CAVEATS.md #1). With nothing cut out this produces
    // exactly the four points, in exactly the order, the app has always produced — proven by
    // PlanConversionRoundTripTest, and the reason the §15 worked example cannot move. With a corner
    // cut out the engine finally sees an L and its cut / missing-corner checks fire, which they never
    // could before. The engine itself is untouched: it has always been able to score this shape.
    val outline = cutOutlineOrNull(rooms, cutOut)?.map { Point(ex(it.x), ey(it.y)) }
        ?: listOf(
            Point(ex(minC), ey(maxR)), Point(ex(maxC), ey(maxR)),
            Point(ex(maxC), ey(minR)), Point(ex(minC), ey(minR)),
        )

    val doors = door?.let { d ->
        val (centre, ws, we) = doorGeometry(d, minC, maxC, minR, maxR)
        listOf(Door(id = "door-main", centre = centre, wallStart = ws, wallEnd = we, isMainEntrance = true))
    } ?: emptyList()

    // The extras are positioned AGAINST THIS OUTLINE, so a home with a cut corner files its water
    // tank against its real shape rather than a rectangle it never had.
    val fixtures = fixturesFor(siteAnswers, outline, north)

    return Plan(
        id = planId,
        propertyType = propertyType,
        intent = theIntent,
        levels = listOf(
            Level(index = 0, outline = outline, rooms = engineRooms, doors = doors, fixtures = fixtures),
        ),
        site = siteFor(siteAnswers, outline),
        northOffsetDegrees = north,
    )
}

/** Rebuild the optional extras from a saved home, so reopening it never drops what it was scored with. */
fun siteAnswersFromSavedPlan(plan: Plan): SiteAnswers {
    val level = plan.levels.firstOrNull() ?: return SiteAnswers()
    return siteAnswersFromPlan(level.outline, plan.northOffsetDegrees, level.fixtures, plan.site)
}

/** A grid room as the pure geometry sees it — id and type stripped, because shape doesn't need them. */
fun GridRoom.cellRect() = CellRect(col, row, w, h)

/**
 * The home's outline in GRID corners, ordered so that mapping it through the north-flip lands on the
 * SAME winding the bounding-box rectangle has always used. Null when nothing is cut out, or when the
 * cut would leave a shape with no single honest outline — both of which fall back to the rectangle.
 *
 * ⚠ The reversal is not cosmetic. [Footprint.trace] walks the ring with the interior on one side in
 * SCREEN coordinates, where rows grow downward; the flip to engine space (`ey = GRID − row`) mirrors
 * the shape and therefore reverses its winding. Reversing here means a home with nothing cut out
 * produces the identical four points in the identical order it always did — which is precisely why
 * no engine test needed opening for this change.
 */
private fun cutOutlineOrNull(rooms: List<GridRoom>, cutOut: Set<Cell>): List<GridPoint>? {
    if (cutOut.isEmpty()) return null
    val rects = rooms.map { it.cellRect() }
    val cells = Footprint.homeCells(rects, cutOut)
    if (cells == Footprint.boundingCells(rects)) return null      // every cut lapsed under a room
    return Footprint.trace(cells)?.asReversed()
}

/** The gaps the app must ask the user about, with the front door's own cell held back as uncuttable. */
fun gapsFor(rooms: List<GridRoom>, door: GridDoor?, cols: Int, rows: Int): List<Gap> =
    Footprint.gaps(rooms.map { it.cellRect() }, protectedCells = protectedCells(rooms, door, cols, rows))

/** Cells that must remain part of the home: today, the cell the front-door marker stands on. */
fun protectedCells(rooms: List<GridRoom>, door: GridDoor?, cols: Int, rows: Int): Set<Cell> {
    if (door == null || rooms.isEmpty()) return emptySet()
    val (c, r) = doorMarkerCell(door, rooms, cols, rows)
    return setOf(Cell(c, r))
}

/**
 * Drop cut-out cells that no longer make sense after a room edit — ones now outside the home's
 * bounding box, ones a room has grown over, and (as a whole) any set that would no longer leave a
 * single solid home. Editing rooms must never leave the plan holding a shape the app cannot draw.
 */
fun pruneCutOut(rooms: List<GridRoom>, door: GridDoor?, cutOut: Set<Cell>, cols: Int, rows: Int): Set<Cell> {
    if (cutOut.isEmpty()) return cutOut
    val rects = rooms.map { it.cellRect() }
    val box = Footprint.boundingCells(rects)
    val occupied = Footprint.occupiedCells(rects)
    val guarded = protectedCells(rooms, door, cols, rows)
    val kept = cutOut.filter { it in box && it !in occupied && it !in guarded }.toSet()
    if (kept.isEmpty()) return emptySet()
    return if (Footprint.trace(box - kept) != null) kept else emptySet()
}

/** Rebuild which cells were cut out from a reopened home's stored outline (the inverse of the above). */
fun cutOutFromPlan(plan: Plan, rooms: List<GridRoom>): Set<Cell> {
    val level = plan.levels.firstOrNull() ?: return emptySet()
    if (level.outline.size < 5) return emptySet()          // a rectangle has four corners: nothing cut
    val ring = level.outline.map { GridPoint(it.x.roundToInt(), (GRID - it.y).roundToInt()) }
    return Footprint.cutOutFromOutline(rooms.map { it.cellRect() }, ring)
}

/**
 * Rebuild the placed grid rooms from a stored engine [Plan] — the exact inverse of the [buildEnginePlan]
 * flip (engine y = GRID − row), so a reopened home shows its rooms again. Lives here, next to the
 * forward flip, so the two can never drift and the round-trip is unit-testable without a ViewModel.
 */
fun gridRoomsFromPlan(plan: Plan): List<GridRoom> {
    val level = plan.levels.firstOrNull() ?: return emptyList()
    return level.rooms.mapNotNull { room ->
        if (room.polygon.isEmpty()) return@mapNotNull null
        val xs = room.polygon.map { it.x }
        val ys = room.polygon.map { it.y }
        val x0 = xs.min(); val x1 = xs.max()
        val yTop = ys.max(); val yBottom = ys.min()
        GridRoom(
            id = room.id,
            type = room.type,
            col = x0.roundToInt(),
            row = (GRID - yTop).roundToInt(),
            w = (x1 - x0).roundToInt().coerceAtLeast(1),
            h = (yTop - yBottom).roundToInt().coerceAtLeast(1),
        )
    }
}

/** Rebuild the placed door from a stored [Plan], classifying its wall from the footprint edges. */
fun gridDoorFromPlan(plan: Plan, rooms: List<GridRoom>): GridDoor? {
    val level = plan.levels.firstOrNull() ?: return null
    val d = level.doors.firstOrNull { it.isMainEntrance } ?: return null
    if (rooms.isEmpty()) return null
    val minC = rooms.minOf { it.col }
    val maxC = rooms.maxOf { it.col + it.w }
    val minR = rooms.minOf { it.row }
    val maxR = rooms.maxOf { it.row + it.h }
    val yNorth = (GRID - minR).toDouble()   // ey(minR)
    val ySouth = (GRID - maxR).toDouble()   // ey(maxR)
    val xEast = maxC.toDouble()
    val xWest = minC.toDouble()
    val eps = 1e-6
    val horizontal = abs(d.wallStart.y - d.wallEnd.y) < eps
    return when {
        horizontal && abs(d.centre.y - yNorth) < eps -> GridDoor(DoorSide.N, (d.centre.x - 0.5).roundToInt())
        horizontal && abs(d.centre.y - ySouth) < eps -> GridDoor(DoorSide.S, (d.centre.x - 0.5).roundToInt())
        abs(d.centre.x - xEast) < eps -> GridDoor(DoorSide.E, ((GRID - d.centre.y) - 0.5).roundToInt())
        abs(d.centre.x - xWest) < eps -> GridDoor(DoorSide.W, ((GRID - d.centre.y) - 0.5).roundToInt())
        else -> null
    }
}

/**
 * Where the door marker is DRAWN on the grid — on the rooms' FOOTPRINT edge (the house's outer wall),
 * never the plot edge. Pure so the "displayed == scored == reloaded" guarantee is testable: this is the
 * same footprint the engine scores ([doorGeometry] uses minR/maxR/minC/maxC), the same wall reopen lands
 * on (the plot collapses to the footprint via [gridSizeForRooms]), and where [placeDoor] clamps the door.
 * Drawing the perpendicular edge on the plot boundary instead left the door floating in the empty margin
 * above/beside the house whenever the plot was drawn larger than the rooms. Found by rendering
 * tools/grid-prototype/harness.html and looking. Returns the (col, row) cell the marker occupies.
 */
fun doorMarkerCell(door: GridDoor, rooms: List<GridRoom>, cols: Int, rows: Int): Pair<Int, Int> {
    val minC = rooms.minOfOrNull { it.col } ?: 0
    val maxC = rooms.maxOfOrNull { it.col + it.w } ?: cols
    val minR = rooms.minOfOrNull { it.row } ?: 0
    val maxR = rooms.maxOfOrNull { it.row + it.h } ?: rows
    return when (door.side) {
        DoorSide.N -> door.cell to minR
        DoorSide.S -> door.cell to (maxR - 1)
        DoorSide.W -> minC to door.cell
        DoorSide.E -> (maxC - 1) to door.cell
    }
}

/**
 * Which front door a tap means. Pure, because this decides the **most heavily weighted input in the
 * whole score** and it used to be a lambda buried inside the Composable that no test could reach.
 *
 * [xCells]/[yCells] are the tap in fractional cell units (pixel ÷ cell size), not whole cells — see
 * the thin-house note below.
 *
 * ⭐ The side is chosen by distance to the **HOUSE's** walls (the rooms' footprint), never the plot's
 * edges. Measuring to the plot meant that with a plot drawn larger than the house, a tap just above
 * the rooms could resolve to *West* purely because the plot's west edge happened to be nearer than
 * its north edge — a wall the user never aimed at (UAT S8). The plot does not appear in this function
 * at all, which is the fix stated structurally: the same tap on the same house gives the same door at
 * any plot size.
 *
 * Distances are **signed**, so a tap out in the empty margin is negative for the wall it lies beyond
 * and that wall wins — exactly what "I tapped above the house" should mean.
 *
 * ⚠ Fractional cells, not whole ones, because a **1-cell-deep house** has its north and south walls
 * half a cell apart: rounded to whole cells both distances are 0 and the tie would always resolve
 * north, so the user could never place a south door on a thin house. Comparing the continuous tap
 * position against the wall lines resolves it the way the finger meant.
 *
 * The position ALONG the wall is clamped onto the footprint, as it always was: that is where the
 * engine scores it ([doorGeometry]), where it is drawn ([doorMarkerCell]) and where reopen puts it,
 * so displayed == scored == reloaded. Returns null when there is no house to attach a wall to.
 */
fun doorForTap(xCells: Float, yCells: Float, rooms: List<GridRoom>): GridDoor? {
    if (rooms.isEmpty()) return null
    val fMinC = rooms.minOf { it.col }; val fMaxC = rooms.maxOf { it.col + it.w }
    val fMinR = rooms.minOf { it.row }; val fMaxR = rooms.maxOf { it.row + it.h }
    // Distance from the tap to each of the house's four wall LINES. Negative = beyond that wall.
    val distN = yCells - fMinR
    val distS = fMaxR - yCells
    val distW = xCells - fMinC
    val distE = fMaxC - xCells
    val cCol = floor(xCells).toInt().coerceIn(fMinC, fMaxC - 1)
    val cRow = floor(yCells).toInt().coerceIn(fMinR, fMaxR - 1)
    return when (minOf(distN, distS, distW, distE)) {
        distN -> GridDoor(DoorSide.N, cCol)
        distS -> GridDoor(DoorSide.S, cCol)
        distW -> GridDoor(DoorSide.W, cRow)
        else -> GridDoor(DoorSide.E, cRow)
    }
}

/**
 * How far off an outer wall a named entrance may still sit and count as being ON it, in cells.
 *
 * ⚠ One, not zero. The rooms reaching this point have been snapped to a drawing grid and re-shaped
 * to the sizes their sheet prints, so a foyer drawn hard against the outer wall can legitimately
 * come to rest one cell in — a wall's thickness, or a corridor that took the edge strip. Demanding
 * a perfect flush would have made the answer depend on rounding, which is the worst possible reason
 * to ask somebody a question we had already answered. Two cells would start reaching inner lobbies.
 */
const val ENTRANCE_WALL_REACH = 1

/**
 * ⭐⭐ THE FRONT DOOR, READ OFF THE PLAN — when, and only when, the plan says where it is.
 *
 * The owner asked for this of his own sheet (6 Aug 2026): *"we are asking user to mark Entry Door
 * but cant we do it ourselves when Entry is clearly marked? we ask only if its not"*. His plan
 * prints ENTRY and FOYER in as many letters, and we were still making him tap.
 *
 * ⚠ THIS IS NOT DOOR DETECTION, and the difference is the whole safety of it. Asking a reader to
 * FIND a door — a swing arc, a gap in a wall — benchmarks at 39 %, which is why `ScanTypes` rule S3
 * says there is no door anywhere in the wire format and there still isn't. This reads a room NAME,
 * the thing the reader does at ~95 %, and then applies our own arithmetic: a room called the
 * entrance is where you come in, so the front door is on whichever outer wall that room is part of.
 * No model is asked anything, and nothing here reaches the reader.
 *
 * It refuses far more often than it answers, on purpose, because a wrong front door is the single
 * most expensive mistake the app can make — it is the heaviest input the engine weighs. It answers
 * only when all three hold:
 *   · the plan named exactly ONE entrance. Two foyers is a plan we do not understand.
 *   · that room is ON an outer wall, within [ENTRANCE_WALL_REACH]. An entrance floating in the
 *     middle of the footprint is an inner lobby, and which wall the visitor came through is then a
 *     guess.
 *   · one wall is the clear nearest, or — in a corner — one contact is clearly the longer. A square
 *     entrance in a corner has two equal claims and gets neither.
 *
 * Everything it returns is still shown to the user and still changeable; "we asked only if we had
 * to" is not the same as "we decided for you and said nothing".
 */
fun frontDoorFromEntrance(rooms: List<GridRoom>): GridDoor? {
    if (rooms.isEmpty()) return null
    val entrance = rooms.filter { it.type == RoomType.ENTRANCE }.singleOrNull() ?: return null
    val minC = rooms.minOf { it.col }
    val maxC = rooms.maxOf { it.col + it.w }
    val minR = rooms.minOf { it.row }
    val maxR = rooms.maxOf { it.row + it.h }

    // How far the entrance sits from each outer wall, and how much wall it would occupy if that is
    // the one. Contact length breaks a corner tie: a foyer 6 cells along the west wall and 1 cell
    // along the north wall is a west entrance.
    val walls = listOf(
        Triple(DoorSide.N, entrance.row - minR, entrance.w),
        Triple(DoorSide.S, maxR - (entrance.row + entrance.h), entrance.w),
        Triple(DoorSide.W, entrance.col - minC, entrance.h),
        Triple(DoorSide.E, maxC - (entrance.col + entrance.w), entrance.h),
    )
    val nearest = walls.minOf { it.second }
    if (nearest > ENTRANCE_WALL_REACH) return null         // an inner lobby, not a way in
    val onWall = walls.filter { it.second == nearest }
    val best = onWall.maxByOrNull { it.third } ?: return null
    if (onWall.count { it.third == best.third } > 1) return null   // a corner with no longer side

    // Along the wall, the middle of the entrance itself — clamped onto the footprint, exactly as a
    // tapped door is, so displayed == scored == reloaded.
    val cell = when (best.first) {
        DoorSide.N, DoorSide.S -> (entrance.col + entrance.w / 2).coerceIn(minC, maxC - 1)
        DoorSide.W, DoorSide.E -> (entrance.row + entrance.h / 2).coerceIn(minR, maxR - 1)
    }
    return GridDoor(best.first, cell)
}

/** The door centre + wall span on the footprint perimeter for the chosen side/cell. */
private fun doorGeometry(d: GridDoor, minC: Int, maxC: Int, minR: Int, maxR: Int): Triple<Point, Point, Point> {
    fun ex(col: Double) = col
    fun ey(row: Double) = (GRID - row)
    val alongCol = (d.cell + 0.5).coerceIn(minC + 0.5, maxC - 0.5)
    val alongRow = (d.cell + 0.5).coerceIn(minR + 0.5, maxR - 0.5)
    return when (d.side) {
        DoorSide.N -> Triple(
            Point(ex(alongCol), ey(minR.toDouble())),
            Point(ex(minC.toDouble()), ey(minR.toDouble())), Point(ex(maxC.toDouble()), ey(minR.toDouble())),
        )
        DoorSide.S -> Triple(
            Point(ex(alongCol), ey(maxR.toDouble())),
            Point(ex(minC.toDouble()), ey(maxR.toDouble())), Point(ex(maxC.toDouble()), ey(maxR.toDouble())),
        )
        DoorSide.E -> Triple(
            Point(ex(maxC.toDouble()), ey(alongRow)),
            Point(ex(maxC.toDouble()), ey(minR.toDouble())), Point(ex(maxC.toDouble()), ey(maxR.toDouble())),
        )
        DoorSide.W -> Triple(
            Point(ex(minC.toDouble()), ey(alongRow)),
            Point(ex(minC.toDouble()), ey(minR.toDouble())), Point(ex(minC.toDouble()), ey(maxR.toDouble())),
        )
    }
}
