package com.vastufirst.engine

import com.vastufirst.shared.Defect
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.RulesetConfig
import com.vastufirst.shared.Verdict

internal data class ScoreResult(val score: Int, val base: Double, val defectPenalty: Int)

/**
 * Scoring (Product PRD §4.5) — a product invention, labelled as such; no classical text ranks
 * rooms numerically.
 *
 *   base   = Σ(points × weight) / Σ(weight)   over all SCORED elements (rooms + the door)
 *   score  = clamp(round(base − defectPenalty), 0, 100)
 *
 * NOT_SCORED elements are excluded from BOTH sums (§4.4.1). The door contributes at the
 * ENTRANCE weight (§4.5.1). Structural defects add a capped penalty (§4.5.2) so a plan with a
 * missing corner does not score identically to an intact one.
 */
internal class Scorer(private val config: RulesetConfig) {

    fun score(roomResults: List<RoomResult>, doorResult: DoorResult?, defects: List<Defect>): ScoreResult {
        var weightedPoints = 0.0
        var weightSum = 0.0

        for (rr in roomResults) {
            if (rr.verdict == Verdict.NOT_SCORED) continue
            weightedPoints += rr.points * rr.weight
            weightSum += rr.weight
        }
        if (doorResult != null) {
            weightedPoints += doorResult.points * doorResult.weight
            weightSum += doorResult.weight
        }

        val base = if (weightSum > 0.0) weightedPoints / weightSum else 0.0

        // Disputed (DISP) defects are surfaced in the report with both readings but must NOT commit
        // the score to one side of a genuine dispute — mirroring how disputed room rules are
        // NOT_SCORED (§4.4.1). They are shown, not counted against the user, until a ruling lands.
        //
        // ⭐ The penalty ramps with how much of the room is really over the line (§4.5.2b). Without
        // this the corner is charged TWICE — once by dropping the room's own points and again in
        // full here — so a room three per cent into a forbidden zone still cost a whole MAJOR
        // penalty. The defect itself is untouched: it is still raised, listed and remedied.
        val shareOf = roomResults.associate { it.roomId to it.encroachedShare }
        val rawPenalty = defects
            .filter { it.provenance != Provenance.DISP }
            .sumOf { d -> (config.penalties[d.severity.name] ?: 0) * penaltyFactor(d.roomId?.let(shareOf::get)) }
        val penalty = minOf(rawPenalty, config.penaltyCap.toDouble())

        val score = Math.round(base - penalty).toInt().coerceIn(0, 100)
        return ScoreResult(score, base, Math.round(penalty).toInt())
    }

    /**
     * How much of a defect's penalty actually applies, 0.0–1.0.
     *
     * Full for anything that is not a room — a cut corner, the site, a fixture — because "how much
     * of the room is over the line" is meaningless for those, and for every defect when the credit
     * is off. For a room it ramps linearly to full at [EncroachmentConfig.fullPenaltyShare]: at the
     * default half, a room a tenth over the line pays a fifth of the penalty, and one half over
     * pays all of it.
     */
    private fun penaltyFactor(share: Double?): Double {
        if (!config.encroachment.proportionalCredit || share == null) return 1.0
        val full = config.encroachment.fullPenaltyShare
        if (full <= 0.0) return 1.0
        return (share / full).coerceIn(0.0, 1.0)
    }
}
