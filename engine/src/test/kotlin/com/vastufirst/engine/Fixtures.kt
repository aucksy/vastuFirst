package com.vastufirst.engine

import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType

/** Test fixtures. Same module as the engine, so `internal` [Geometry] is available. */
internal object Fixtures {

    fun rect(x: Double, y: Double, w: Double, h: Double): List<Point> =
        listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))

    /**
     * Plan `sample-01` — "Builder's draft, 2BHK" (Product PRD §15). The Phase 1 acceptance
     * fixture. Square footprint (0,0)–(100,100), expected score == 31.
     */
    fun sample01(north: Int = 0): Plan {
        val rooms = listOf(
            Room("pooja", RoomType.POOJA, rect(6.0, 74.0, 28.0, 20.0)),
            Room("bed2", RoomType.BEDROOM, rect(38.0, 70.0, 26.0, 24.0)),
            Room("toilet", RoomType.TOILET, rect(68.0, 74.0, 26.0, 20.0)),
            Room("kitchen", RoomType.KITCHEN, rect(6.0, 40.0, 28.0, 26.0)),
            Room("stairs", RoomType.STAIRCASE, rect(42.0, 41.0, 17.0, 18.0)),
            Room("living", RoomType.LIVING, rect(66.0, 38.0, 28.0, 30.0)),
            Room("master", RoomType.MASTER_BEDROOM, rect(6.0, 6.0, 34.0, 28.0)),
        )
        val door = Door("main", Point(80.0, 6.0), Point(60.0, 0.0), Point(100.0, 0.0), isMainEntrance = true)
        val outline = rect(0.0, 0.0, 100.0, 100.0)
        return Plan(
            id = "sample-01",
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(Level(0, outline, rooms, listOf(door))),
            site = null,
            northOffsetDegrees = north,
        )
    }

    /**
     * `sample-01`, physically rotated so that the engine's own +north rotation brings it back
     * to the canonical geometry. Pre-rotating by −deg about the footprint centroid and setting
     * northOffsetDegrees = deg is the exact inverse of what the engine applies. Used by the
     * rotation-invariance test (§15).
     */
    fun rotated(deg: Int): Plan {
        val base = sample01(0)
        val lvl = base.levels[0]
        val origin = Geometry.centroid(lvl.outline)
        fun p(pt: Point) = Geometry.rotate(pt, -deg.toDouble(), origin)
        fun poly(ps: List<Point>) = ps.map(::p)
        val rooms = lvl.rooms.map { it.copy(polygon = poly(it.polygon)) }
        val doors = lvl.doors.map { it.copy(centre = p(it.centre), wallStart = p(it.wallStart), wallEnd = p(it.wallEnd)) }
        return base.copy(
            levels = listOf(lvl.copy(outline = poly(lvl.outline), rooms = rooms, doors = doors)),
            northOffsetDegrees = deg,
        )
    }

    /** A clean, well-placed plan (`sample-02`) used to prove the engine also rewards good layouts. */
    fun sample02(): Plan {
        val rooms = listOf(
            Room("living", RoomType.LIVING, rect(66.0, 66.0, 30.0, 30.0)),       // NE — ideal
            Room("kitchen", RoomType.KITCHEN, rect(66.0, 4.0, 30.0, 28.0)),      // SE — ideal
            Room("master", RoomType.MASTER_BEDROOM, rect(4.0, 4.0, 34.0, 30.0)), // SW — ideal
            Room("bed2", RoomType.BEDROOM, rect(4.0, 66.0, 28.0, 30.0)),         // NW — ideal
            Room("store", RoomType.STORE, rect(4.0, 40.0, 24.0, 20.0)),          // W — ideal
        )
        // North-wall door at ~17.7° → pada N3 "Mukhya" (AUSPICIOUS).
        val door = Door("main", Point(66.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), isMainEntrance = true)
        val outline = rect(0.0, 0.0, 100.0, 100.0)
        return Plan(
            id = "sample-02",
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(Level(0, outline, rooms, listOf(door))),
            site = null,
            northOffsetDegrees = 0,
        )
    }
}
