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

        // ⭐ PARTIAL CREDIT (§4.5.2b, owner decision 9 Aug 2026) — see [EncroachmentConfig].
        //
        // The verdict above is already decided and is NOT touched here: a room that crosses the
        // line is still a DEFECT, still raises its finding, still gets its reason and its remedies.
        // What changes is only how many points it forfeits. A room 3 % over the line used to score
        // exactly the same as one sitting wholly in the wrong zone, which is the single biggest
        // reason ordinary homes came out under 4 out of 10.
        val share = if (flagged.isEmpty()) 1.0 else encroachedShare(flagged, overlaps, roomArea)
        val points = scoreFor(verdict, baseVerdict, flagged.isNotEmpty(), share)

        return RoomResult(
            room.id, room.type, zone, verdict, points, rule.weight, rule, overlaps.perPada, share,
        ) to flagged.toList()
    }

    /**
     * The fraction of the room's own area lying in the prohibited zones it was flagged for.
     *
     * ⚠ Quantised to four places on the way out. The polygon areas behind it are accumulated from
     * pada rectangles whose edges are computed from the analysis rectangle, so the last couple of
     * bits move with the home's absolute position on the canvas. Four places is far finer than
     * anything a reader could perceive and coarse enough that the same home cannot score
     * differently for being drawn lower down the sheet (§0.4).
     */
    private fun encroachedShare(flagged: Set<Zone>, overlaps: PadaOverlaps, roomArea: Double): Double {
        if (roomArea <= 0.0) return 1.0
        val bad = flagged.sumOf { overlaps.perZone[it] ?: 0.0 }
        val raw = (bad / roomArea).coerceIn(0.0, 1.0)
        return Math.round(raw * 10_000.0) / 10_000.0
    }

    /**
     * Points for a room, interpolating between what it WOULD have scored on its main zone and the
     * defect floor, by how much of it is on forbidden ground.
     *
     * With the credit off — or for a room wholly over the line — this returns exactly the old
     * value, so nothing about the existing worked examples moves.
     */
    private fun scoreFor(verdict: Verdict, baseVerdict: Verdict, flagged: Boolean, share: Double): Int {
        val floor = ruleSet.config.scorePoints[Verdict.DEFECT.name] ?: 0
        val plain = ruleSet.config.scorePoints[verdict.name] ?: 0
        if (!flagged || !ruleSet.config.encroachment.proportionalCredit) return plain
        // The room's main zone may itself be prohibited — then `clean` IS the floor and no credit
        // is given, which is right: that room is not clipping anything, it is sitting in it.
        val clean = (ruleSet.config.scorePoints[baseVerdict.name] ?: 0).coerceAtLeast(floor)
        return Math.round(floor + (clean - floor) * (1.0 - share)).toInt()
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
