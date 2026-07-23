package com.vastufirst.engine

import com.vastufirst.shared.Point
import com.vastufirst.shared.RulesetConfig
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAssignmentStrategy

/**
 * Resolves a room's zone(s) under the configured strategies (Product PRD §4.2.6).
 * Two independent questions are answered:
 *  - which single zone gets the *credit* (positiveStrategy, default LARGEST_OVERLAP), and
 *  - which prohibited zones are *encroached* (defectStrategy, default ANY_ENCROACHMENT with
 *    the mandatory dual threshold — a shared boundary line must not trigger a MAJOR defect).
 */
internal class ZoneAssigner(private val config: RulesetConfig) {

    /** The single zone that earns the positive verdict. */
    fun positiveZone(
        strategy: ZoneAssignmentStrategy,
        overlaps: PadaOverlaps,
        centroidZone: Zone,
    ): Zone = when (strategy) {
        ZoneAssignmentStrategy.CENTROID -> centroidZone
        ZoneAssignmentStrategy.LARGEST_OVERLAP -> largestOverlap(overlaps, centroidZone)
        // ANY_ENCROACHMENT is a defect resolver; for a positive verdict fall back to largest.
        ZoneAssignmentStrategy.ANY_ENCROACHMENT -> largestOverlap(overlaps, centroidZone)
    }

    /** §4.2.6 tie-breaking, deterministic and iteration-order independent. */
    private fun largestOverlap(overlaps: PadaOverlaps, centroidZone: Zone): Zone {
        val perZone = overlaps.perZone
        if (perZone.isEmpty()) return centroidZone
        val max = perZone.values.max()
        // (1) larger area wins; gather everything within 1% of the max as tied.
        val within = perZone.filter { it.value >= max * (1.0 - config.tieBreak.withinFraction) }.keys
        if (within.size == 1) return within.first()
        // (2) the zone containing the room's centroid wins, if it is among the tied.
        if (centroidZone in within) return centroidZone
        // (3) fixed precedence — most sensitive first.
        return config.tieBreak.zonePrecedence.firstOrNull { it in within } ?: within.first()
    }

    /**
     * Prohibited zones the room encroaches, under ANY_ENCROACHMENT with the dual threshold
     * (§4.2.6): overlap ≥ roomAreaFraction of the room's own area AND ≥ analysisRectAreaFraction
     * of the analysis-rectangle area. Both are mandatory — either alone lets a 0.1% clip or a
     * measure-zero shared edge raise a MAJOR defect.
     */
    fun encroachedProhibited(
        prohibited: Set<Zone>,
        overlaps: PadaOverlaps,
        roomArea: Double,
        analysisRectArea: Double,
    ): List<Zone> {
        if (prohibited.isEmpty()) return emptyList()
        val roomThreshold = roomArea * config.encroachment.roomAreaFraction
        val rectThreshold = analysisRectArea * config.encroachment.analysisRectAreaFraction
        return prohibited
            .mapNotNull { z -> overlaps.perZone[z]?.let { z to it } }
            .filter { (_, ov) -> ov >= roomThreshold && ov >= rectThreshold }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    companion object {
        fun roomArea(poly: List<Point>): Double = Geometry.area(poly)
    }
}
