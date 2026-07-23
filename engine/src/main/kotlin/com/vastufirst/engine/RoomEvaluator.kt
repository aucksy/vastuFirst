package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.shared.Point
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone

/**
 * Room evaluation → the five verdicts (Product PRD §4.4). A disputed rule (§4.4.1) and a
 * pending/excluded rule (§4.4.2, BASEMENT) resolve to NOT_SCORED and contribute nothing to
 * either sum. Silence (a zone in none of the three sets) is explicit SUBOPTIMAL, never a
 * middling 50 (§4.4.3 — the prototype's inflation bug).
 */
internal class RoomEvaluator(
    private val ruleSet: RuleSet,
    private val grid: PadaGrid,
    private val assigner: ZoneAssigner,
    private val analysisRectArea: Double,
) {
    /**
     * Evaluate a room whose polygon is already rotated to true-North. Returns the [RoomResult]
     * plus the FULL list of prohibited zones it violates — §4.6 requires every (RoomType,
     * prohibited Zone) pair to resolve to a defect, so a room straddling two prohibited zones
     * must produce two defects, not one. The list is empty for a non-DEFECT room.
     */
    fun evaluate(room: Room, rotatedPoly: List<Point>): Pair<RoomResult, List<Zone>> {
        val rule = ruleSet.ruleFor(room.type)
        val overlaps = grid.overlaps(rotatedPoly)
        val centroidZone = grid.zoneOf(Geometry.centroid(rotatedPoly))

        // No rule ⇒ unruled type (loader guarantees ruled-or-unruled) ⇒ NOT_SCORED.
        if (rule == null) {
            return RoomResult(room.id, room.type, centroidZone, Verdict.NOT_SCORED, 0, 0.0, null, overlaps.perPada) to emptyList()
        }

        val positiveZone = assigner.positiveZone(rule.positiveStrategy, overlaps, centroidZone)

        // Disputed (§4.4.1) or pending/scored-elsewhere (§4.4.2) ⇒ NOT_SCORED, excluded from both sums.
        if (rule.disputeId != null || rule.excludeFromScore) {
            return RoomResult(room.id, room.type, positiveZone, Verdict.NOT_SCORED, 0, rule.weight, rule, overlaps.perPada) to emptyList()
        }

        val roomArea = Geometry.area(rotatedPoly)

        // All prohibited zones this room actually violates: those flagged by the defect strategy,
        // plus the credited zone itself if it happens to be prohibited (largest-overlap case).
        val flagged = LinkedHashSet<Zone>()
        flagged += defectZones(rule, overlaps, positiveZone, centroidZone, roomArea)
        if (positiveZone in rule.prohibited) flagged += positiveZone

        val baseVerdict = when (positiveZone) {
            in rule.ideal -> Verdict.IDEAL
            in rule.acceptable -> Verdict.ACCEPTABLE
            in rule.prohibited -> Verdict.DEFECT
            else -> Verdict.SUBOPTIMAL
        }

        val verdict = if (flagged.isNotEmpty()) Verdict.DEFECT else baseVerdict
        val zone = if (flagged.isNotEmpty()) flagged.first() else positiveZone
        val points = ruleSet.config.scorePoints[verdict.name] ?: 0

        return RoomResult(room.id, room.type, zone, verdict, points, rule.weight, rule, overlaps.perPada) to flagged.toList()
    }

    private fun defectZones(
        rule: com.vastufirst.shared.RoomRule,
        overlaps: PadaOverlaps,
        positiveZone: Zone,
        centroidZone: Zone,
        roomArea: Double,
    ): List<Zone> = when (rule.defectStrategy) {
        com.vastufirst.shared.ZoneAssignmentStrategy.ANY_ENCROACHMENT ->
            assigner.encroachedProhibited(rule.prohibited, overlaps, roomArea, analysisRectArea)
        com.vastufirst.shared.ZoneAssignmentStrategy.CENTROID ->
            if (centroidZone in rule.prohibited) listOf(centroidZone) else emptyList()
        com.vastufirst.shared.ZoneAssignmentStrategy.LARGEST_OVERLAP ->
            if (positiveZone in rule.prohibited) listOf(positiveZone) else emptyList()
    }
}
