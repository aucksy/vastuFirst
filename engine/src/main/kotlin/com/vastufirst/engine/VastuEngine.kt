package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.SchoolProfile
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import com.vastufirst.shared.Verdict

/**
 * The Vastu engine (Product PRD §4) — the heart of the product. Pure Kotlin, headless, offline:
 * plan → zones → door → verdicts → score → defects, with no UI and no network.
 *
 * The pipeline: rotate all geometry to true-North about the footprint area centroid, lay the
 * square 81-pada grid on the resulting bounding rectangle, evaluate each room and the main door,
 * then score and detect defects. **Building orientation is never a scoring input** (§0.4) — it
 * enters only here, as a geometry rotation, and the rotation-invariance test proves it.
 */
class VastuEngine(private val ruleSet: RuleSet = RuleSetLoader.loadDefault()) {

    fun ruleSetVersion(): String = ruleSet.version

    fun analyze(plan: Plan): Analysis {
        // Only the default 81-pada school is implemented; the angular geometries are a different
        // computation gated on the M-11 ruling (§4.7). Refuse rather than silently mis-score.
        require(plan.schoolProfile == SchoolProfile.TRADITIONAL_8) {
            "Only TRADITIONAL_8 is implemented. ${plan.schoolProfile} is a separate angular geometry " +
                "gated on the M-11 expert ruling (§4.7) — it is not a reinterpretation of the 81-pada grid."
        }
        val level = plan.levels.firstOrNull { it.index == 0 }
            ?: error("Plan ${plan.id} has no ground floor (a Level with index 0).")
        val config = ruleSet.config
        val angle = plan.northOffsetDegrees.toDouble()

        // 1. Rotate every point to true-North alignment about the footprint area centroid (§4.0, §4.2).
        val origin = Geometry.centroid(level.outline)
        val rotatedOutline = Geometry.rotatePoly(level.outline, angle, origin)

        // 2. Analysis rectangle + the cardinally-aligned 81-pada grid.
        val analysisRect = Geometry.bbox(rotatedOutline)
        val grid = PadaGrid(analysisRect, config.gridSize)
        val assigner = ZoneAssigner(config)

        // 3. Rooms → verdicts. Keep the rotated polygons (adjacency, X-10) and the full list of
        //    prohibited zones each room violates (§4.6 — one defect per violated zone).
        val evaluator = RoomEvaluator(ruleSet, grid, assigner, analysisRect.area)
        val roomResults = ArrayList<RoomResult>(level.rooms.size)
        val roomPolys = HashMap<String, List<Point>>(level.rooms.size)
        val roomDefectZones = HashMap<String, List<Zone>>(level.rooms.size)
        for (room in level.rooms) {
            val rotated = Geometry.rotatePoly(room.polygon, angle, origin)
            roomPolys[room.id] = rotated
            val (rr, zones) = evaluator.evaluate(room, rotated)
            roomResults += rr
            roomDefectZones[room.id] = zones
        }

        // 4. The main door → the 32-pada verdict (the highest-weighted element).
        val mainDoor = level.doors.firstOrNull { it.isMainEntrance }
        val resolver = DoorResolver(ruleSet, config)
        val (doorResult, doorBearing) = if (mainDoor != null) {
            val c = Geometry.rotate(mainDoor.centre, angle, origin)
            val ws = Geometry.rotate(mainDoor.wallStart, angle, origin)
            val we = Geometry.rotate(mainDoor.wallEnd, angle, origin)
            val rd = resolver.resolve(c, ws, we, analysisRect)
            DoorResult(
                doorId = mainDoor.id,
                pada = rd.pada,
                bearing = rd.bearing,
                verdict = rd.verdict,
                points = config.padaPoints[rd.verdict.name] ?: 0,
                weight = config.doorWeight,
                spansTwoPadas = rd.spansTwoPadas,
            ) to rd.bearing
        } else null to null

        // 5. Cuts / extensions vs the reference rectangle.
        val anomalies = AnomalyDetector(config.anomaly).detect(rotatedOutline, grid)

        // 6. Defects + not-assessed (fixtures/site rotated into true-North space).
        val rotatedFixtures: List<Pair<Fixture, Point>> =
            level.fixtures.map { it to Geometry.rotate(it.position, angle, origin) }
        val outcome = DefectDetector(ruleSet, grid)
            .detect(roomResults, roomDefectZones, roomPolys, doorBearing, anomalies, rotatedFixtures, plan.site)

        // 7. Score.
        val scored = Scorer(config).score(roomResults, doorResult, outcome.defects)

        // 8. Disputes relevant to THIS plan.
        val disputes = surfaceDisputes(plan, roomResults)

        return Analysis(
            planId = plan.id,
            intent = plan.intent,
            propertyType = plan.propertyType,
            northOffsetDegrees = plan.northOffsetDegrees,
            schoolProfile = plan.schoolProfile,
            score = scored.score,
            base = scored.base,
            defectPenalty = scored.defectPenalty,
            roomResults = roomResults,
            doorResult = doorResult,
            defects = outcome.defects.sortedWith(severityThenWeight(roomResults)),
            cuts = anomalies.cuts,
            extensions = anomalies.extensions,
            shapeIrregular = anomalies.shapeIrregular,
            notAssessed = outcome.notAssessed,
            disputes = disputes,
            ruleSetVersion = ruleSet.version,
        )
    }

    /** Sort defects by severity (MAJOR first), then by the offending element's weight (§5). */
    private fun severityThenWeight(rooms: List<RoomResult>): Comparator<Defect> {
        val weightOf: (Defect) -> Double = { d ->
            rooms.firstOrNull { it.roomId == d.roomId }?.weight ?: 0.0
        }
        val sev: (Severity) -> Int = { when (it) { Severity.MAJOR -> 0; Severity.MODERATE -> 1; Severity.MINOR -> 2 } }
        return compareBy<Defect> { sev(it.severity) }.thenByDescending { weightOf(it) }.thenBy { it.id }
    }

    private fun surfaceDisputes(plan: Plan, roomResults: List<RoomResult>): List<Dispute> {
        val out = LinkedHashSet<Dispute>()
        // Any room whose rule is itself disputed (e.g. POOJA → W-12).
        plan.levels.flatMap { it.rooms }.forEach { room ->
            ruleSet.ruleFor(room.type)?.disputeId?.let { id -> ruleSet.dispute(id)?.let(out::add) }
        }
        // Any dispute whose (room type [+ zone]) condition is met by this plan.
        for (dispute in ruleSet.disputes) {
            val type = dispute.appliesTo ?: continue
            val match = roomResults.any { rr ->
                rr.type == type && (dispute.appliesToZone == null || rr.zone == dispute.appliesToZone)
            }
            if (match) out += dispute
        }
        return out.sortedBy { it.id }
    }
}
