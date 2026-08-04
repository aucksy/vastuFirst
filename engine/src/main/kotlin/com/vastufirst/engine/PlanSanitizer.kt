package com.vastufirst.engine

import com.vastufirst.shared.AnalysisNote
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Door
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.NoteLevel
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.Room

internal data class SanitizedLevel(
    val outline: List<Point>,
    val rooms: List<Room>,
    val doors: List<Door>,
    val fixtures: List<Fixture>,
)

internal data class SanitizeResult(
    val level: SanitizedLevel?,          // null ⇒ not enough to score (INSUFFICIENT)
    val northOffset: Int,
    val quality: AnalysisQuality,
    val notes: List<AnalysisNote>,
)

/**
 * The production never-crash gate. Real plans arrive malformed — NaN coordinates from a bad
 * import, a footprint someone forgot to close, a self-touching trace, a zero-area "room". This
 * sanitises the input so the engine can ALWAYS produce a usable score, and records in plain,
 * non-alarming language what it recovered from. It never throws and never emits the words
 * "irregular", "error" or "cannot analyze".
 */
internal object PlanSanitizer {

    fun sanitize(plan: Plan): SanitizeResult {
        val notes = mutableListOf<AnalysisNote>()
        var quality = AnalysisQuality.OK

        val north = ((plan.northOffsetDegrees % 360) + 360) % 360

        val level = plan.levels.firstOrNull { it.index == 0 } ?: plan.levels.firstOrNull()
        if (level == null) {
            notes += AnalysisNote("no-layout", "Add your home's layout to see its score.", NoteLevel.WARNING)
            return SanitizeResult(null, north, AnalysisQuality.INSUFFICIENT, notes)
        }

        // Rooms first — a usable room set can rebuild a missing outline.
        val rooms = ArrayList<Room>(level.rooms.size)
        var droppedRooms = 0
        for (room in level.rooms) {
            val poly = Geometry.cleanRing(room.polygon)
            if (poly.size >= 3 && !isDegenerate(poly)) rooms += room.copy(polygon = poly) else droppedRooms++
        }
        if (droppedRooms > 0) {
            quality = AnalysisQuality.DEGRADED
            // "room(s)" was programmer plural on a user-facing note (4 Aug 2026 audit).
            val noun = if (droppedRooms == 1) "room" else "rooms"
            notes += AnalysisNote("skipped-rooms", "Skipped $droppedRooms $noun we couldn't read clearly.", NoteLevel.WARNING)
        }

        // Outline — clean, then recover if unusable, then tidy a self-touching trace.
        var outline = Geometry.cleanRing(level.outline)
        if (outline.size < 3 || isDegenerate(outline)) {
            val hull = Geometry.convexHull(rooms.flatMap { it.polygon })
            if (hull.size >= 3 && !isDegenerate(hull)) {
                outline = hull
                quality = AnalysisQuality.DEGRADED
                // We guessed the outline from the rooms — it may miss a real cut/extension, so the
                // corner/shape checks aren't reliable. Say so; don't let it read as "shape is fine".
                notes += AnalysisNote(
                    "outline-rebuilt",
                    "We estimated the home's outline from your rooms — add the real outline for accurate corner and shape checks.",
                    NoteLevel.WARNING,
                )
            } else {
                notes += AnalysisNote("no-outline", "Add your home's outline to see its score.", NoteLevel.WARNING)
                return SanitizeResult(null, north, AnalysisQuality.INSUFFICIENT, notes)
            }
        } else if (Geometry.isSelfIntersecting(outline)) {
            val hull = Geometry.convexHull(outline)
            if (hull.size >= 3 && !isDegenerate(hull)) {
                outline = hull
                quality = AnalysisQuality.DEGRADED
                // Tidying a self-crossing trace to its hull can erase a genuine cut — flag it so a
                // missing corner is never silently reported as clear.
                notes += AnalysisNote(
                    "outline-tidied",
                    "The outline crossed itself, so we simplified it — please recheck it for accurate corner and shape checks.",
                    NoteLevel.WARNING,
                )
            }
        }

        // Doors — keep them; guide gently where the entrance can't be scored.
        val doors = level.doors.filter { Geometry.isFinite(it.centre) }
        val mainCount = doors.count { it.isMainEntrance }
        when {
            mainCount == 0 -> notes += AnalysisNote(
                "no-main-door", "Mark your main entrance to include it in the score.", NoteLevel.INFO,
            )
            mainCount > 1 -> notes += AnalysisNote(
                "multiple-main-doors", "More than one main entrance was marked — using the first.", NoteLevel.INFO,
            )
        }

        val fixtures = level.fixtures.filter { Geometry.isFinite(it.position) }

        return SanitizeResult(
            SanitizedLevel(outline, rooms, doors, fixtures), north, quality, notes,
        )
    }

    /** Near-zero area relative to the shape's own extent — a line, a sliver, a duplicate ring. */
    private fun isDegenerate(poly: List<Point>): Boolean {
        val xs = poly.map { it.x }; val ys = poly.map { it.y }
        val span = (xs.max() - xs.min()) + (ys.max() - ys.min())
        if (span <= 1e-12) return true
        return Geometry.area(poly) <= span * span * 1e-9
    }
}
