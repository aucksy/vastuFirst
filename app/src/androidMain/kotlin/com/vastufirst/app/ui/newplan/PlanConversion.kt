package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room

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

    // Footprint = bounding box of the placed rooms.
    val minC = rooms.minOf { it.col }
    val maxC = rooms.maxOf { it.col + it.w }
    val minR = rooms.minOf { it.row }
    val maxR = rooms.maxOf { it.row + it.h }
    val outline = listOf(
        Point(ex(minC), ey(maxR)), Point(ex(maxC), ey(maxR)),
        Point(ex(maxC), ey(minR)), Point(ex(minC), ey(minR)),
    )

    val doors = door?.let { d ->
        val (centre, ws, we) = doorGeometry(d, minC, maxC, minR, maxR)
        listOf(Door(id = "door-main", centre = centre, wallStart = ws, wallEnd = we, isMainEntrance = true))
    } ?: emptyList()

    return Plan(
        id = planId,
        propertyType = propertyType,
        intent = theIntent,
        levels = listOf(Level(index = 0, outline = outline, rooms = engineRooms, doors = doors)),
        northOffsetDegrees = north,
    )
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
