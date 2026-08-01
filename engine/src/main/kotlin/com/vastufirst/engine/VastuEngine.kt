package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisNote
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Defect
import com.vastufirst.shared.DefectDefinition
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.NoteLevel
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.SchoolProfile
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import kotlin.math.roundToInt

/**
 * The Vastu engine (Product PRD §4) — the heart of the product. Pure Kotlin, headless, offline:
 * plan → zones → door → verdicts → score → defects, with no UI and no network.
 *
 * Production contract: `analyze` is TOTAL — it never throws and always returns a usable [Analysis].
 * Malformed input is sanitised and recovered (never a crash); a genuinely un-scoreable plan comes
 * back as [AnalysisQuality.INSUFFICIENT] with a friendly prompt, never a scary "error/irregular".
 *
 * Orientation: everything is rotated to true North about the footprint area centroid; the square
 * 81-pada grid is laid on the resulting bounding rectangle; rooms and the door are scored on that
 * cardinal grid (the grid is NEVER rotated to the walls — confirmed by field research). Building
 * SHAPE (cuts/extensions/elongation) is judged in the building's OWN frame so an angled home
 * scores normally. Orientation is never a scoring term (§0.4) — the rotation-invariance test proves it.
 */
class VastuEngine(private val ruleSet: RuleSet = RuleSetLoader.loadDefault()) {

    fun ruleSetVersion(): String = ruleSet.version

    fun analyze(plan: Plan): Analysis {
        return try {
            val san = PlanSanitizer.sanitize(plan)
            val level = san.level ?: return insufficient(plan, san.notes)
            val notes = san.notes.toMutableList()
            if (plan.schoolProfile != SchoolProfile.TRADITIONAL_8) {
                notes += AnalysisNote(
                    "school-default",
                    "Showing the classic 8-direction reading; other schools are coming soon.",
                    NoteLevel.INFO,
                )
            }
            runCore(plan, level, san.northOffset, san.quality, notes)
        } catch (t: Throwable) {
            // Absolute safety net — an unforeseen input must degrade, never crash the app.
            insufficient(
                plan,
                listOf(AnalysisNote("recovered", "We couldn't fully read this plan — add or adjust your layout to try again.", NoteLevel.WARNING)),
            )
        }
    }

    private fun runCore(
        plan: Plan,
        level: SanitizedLevel,
        north: Int,
        quality: AnalysisQuality,
        notes: MutableList<AnalysisNote>,
    ): Analysis {
        val config = ruleSet.config
        val angle = north.toDouble()

        // 1. Rotate every point to true-North about the footprint area centroid (§4.0, §4.2).
        val origin = Geometry.centroid(level.outline)
        val rotatedOutline = Geometry.rotatePoly(level.outline, angle, origin)

        // 2. Analysis rectangle + the cardinally-aligned 81-pada grid.
        val analysisRect = Geometry.bbox(rotatedOutline)
        val grid = PadaGrid(analysisRect, config.gridSize)
        val assigner = ZoneAssigner(config)

        // 3. Rooms → verdicts (+ rotated polygons for adjacency, + every violated prohibited zone).
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

        // 5. Cuts / extensions in the building's OWN frame, attributed to cardinal zones.
        val anomalies = AnomalyDetector(config.anomaly).detect(rotatedOutline, grid)
        if (anomalies.tiltDegrees >= TILT_NOTE_DEG) {
            notes += AnalysisNote(
                "tilted",
                "This home sits about ${anomalies.tiltDegrees.roundToInt()}° off the compass; the score accounts for that.",
                NoteLevel.INFO,
            )
        }
        if (anomalies.shapeIrregular) {
            // Honest: an irregular footprint is traditionally less ideal, and we could not run the
            // missing-corner / extension checks on it — so we must NOT imply the shape is fine.
            notes += AnalysisNote(
                "unusual-shape",
                "Your home isn't a regular rectangle. A squarer, more regular shape is traditionally preferred; we've scored the rooms and entrance, and the corner checks need a clearer outline.",
                NoteLevel.WARNING,
            )
        }

        // 6. Defects + not-assessed (fixtures/site rotated into true-North space).
        val rotatedFixtures: List<Pair<Fixture, Point>> =
            level.fixtures.map { it to Geometry.rotate(it.position, angle, origin) }
        val outcome = DefectDetector(ruleSet, grid)
            .detect(roomResults, roomDefectZones, roomPolys, doorBearing, anomalies, rotatedFixtures, plan.site)

        // 6b. Elongation (research: ideal within 1:1–1:2). Note past the soft flag, MINOR past the hard flag.
        val defects = outcome.defects.toMutableList()
        if (!anomalies.shapeIrregular) {
            val ar = anomalies.aspectRatio
            if (ar > config.aspectHardFlag) {
                ruleSet.defects.firstOrNull { it.id == "X-15" }?.let { defects += buildDefect(it, Zone.BRAHMASTHAN) }
            } else if (ar > config.aspectSoftFlag) {
                notes += AnalysisNote("elongated", "This home is a bit long and narrow; a squarer shape is considered more balanced.", NoteLevel.INFO)
            }
        }

        // 7. Score.
        val scored = Scorer(config).score(roomResults, doorResult, defects)

        // 8. Disputes relevant to THIS plan.
        val disputes = surfaceDisputes(plan, roomResults)

        return Analysis(
            planId = plan.id,
            intent = plan.intent,
            propertyType = plan.propertyType,
            northOffsetDegrees = north,
            // Report the school we ACTUALLY used (the 81-pada reading), not the one requested —
            // otherwise a 16-zone request would be mislabelled while showing 8-direction numbers.
            schoolProfile = SchoolProfile.TRADITIONAL_8,
            score = scored.score,
            base = scored.base,
            defectPenalty = scored.defectPenalty,
            roomResults = roomResults,
            doorResult = doorResult,
            defects = defects.sortedWith(severityThenWeight(roomResults)),
            cuts = anomalies.cuts,
            extensions = anomalies.extensions,
            shapeIrregular = anomalies.shapeIrregular,
            notAssessed = outcome.notAssessed,
            disputes = disputes,
            ruleSetVersion = ruleSet.version,
            quality = quality,
            notes = notes,
            notChecked = outcome.notChecked,
            zoneInfo = ruleSet.zones,
            footprintTiltDegrees = anomalies.tiltDegrees,
            aspectRatio = anomalies.aspectRatio,
        )
    }

    /** A usable-but-empty result the app can turn into onboarding guidance (never an error screen). */
    private fun insufficient(plan: Plan, notes: List<AnalysisNote>): Analysis = Analysis(
        planId = plan.id,
        intent = plan.intent,
        propertyType = plan.propertyType,
        northOffsetDegrees = ((plan.northOffsetDegrees % 360) + 360) % 360,
        schoolProfile = plan.schoolProfile,
        score = 0,
        base = 0.0,
        defectPenalty = 0,
        roomResults = emptyList(),
        doorResult = null,
        defects = emptyList(),
        cuts = emptyList(),
        extensions = emptyList(),
        shapeIrregular = false,
        notAssessed = emptyList(),
        disputes = emptyList(),
        ruleSetVersion = ruleSet.version,
        quality = AnalysisQuality.INSUFFICIENT,
        notes = notes,
        zoneInfo = ruleSet.zones,
    )

    private fun buildDefect(def: DefectDefinition, zone: Zone): Defect = Defect(
        id = def.id,
        severity = def.severity,
        zone = zone,
        roomId = null,
        fixtureId = null,
        ruleSourceId = def.id,
        provenance = def.provenance,
        explanation = def.explanation,
        layoutFix = def.layoutFix,
        remedyNote = def.remedyNote,
        remedies = ruleSet.remediesFor(def),
    )

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
        // Any room whose rule is itself disputed — i.e. one we decline to score at all. (The prayer
        // room was the last of these until the owner ruled W-12 on 1 Aug 2026; it now reaches the
        // reader through the loop below instead, which is what keeps both readings on the page.)
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

    private companion object {
        const val TILT_NOTE_DEG = 1.0   // note the user only when the tilt is actually noticeable
    }
}
