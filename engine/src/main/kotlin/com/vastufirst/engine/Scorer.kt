package com.vastufirst.engine

import com.vastufirst.shared.Defect
import com.vastufirst.shared.DoorResult
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

        val rawPenalty = defects.sumOf { config.penalties[it.severity.name] ?: 0 }
        val penalty = minOf(rawPenalty, config.penaltyCap)

        val score = Math.round(base - penalty).toInt().coerceIn(0, 100)
        return ScoreResult(score, base, penalty)
    }
}
