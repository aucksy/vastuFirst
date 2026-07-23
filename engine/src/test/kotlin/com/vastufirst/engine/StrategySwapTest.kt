package com.vastufirst.engine

import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Point
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAssignmentStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Strategy-swap test (Impl PRD §Phase 1 gate): CENTROID and LARGEST_OVERLAP produce the
 * documented difference. The subject is a U-shaped room whose area centroid sits in the empty
 * central notch (BRAHMASTHAN) while its largest single-zone overlap is the corner NE — the
 * canonical case where the two strategies genuinely disagree.
 */
class StrategySwapTest {

    private val t1 = 100.0 / 3.0
    private val t2 = 200.0 / 3.0

    // U opening downward: filled everywhere except the central-bottom notch [t1,t2] × [0,t2].
    private val uShape = listOf(
        Point(0.0, 0.0), Point(t1, 0.0), Point(t1, t2), Point(t2, t2),
        Point(t2, 0.0), Point(100.0, 0.0), Point(100.0, 100.0), Point(0.0, 100.0),
    )

    @Test
    fun `CENTROID lands in the notch (BRAHMASTHAN); LARGEST_OVERLAP lands on the corner (NE)`() {
        val config = RuleSetLoader.loadDefault().config
        val grid = PadaGrid(Rect(0.0, 0.0, 100.0, 100.0), config.gridSize)
        val overlaps = grid.overlaps(uShape)
        val centroidZone = grid.zoneOf(Geometry.centroid(uShape))
        val assigner = ZoneAssigner(config)

        assertEquals(Zone.BRAHMASTHAN, assigner.positiveZone(ZoneAssignmentStrategy.CENTROID, overlaps, centroidZone))
        assertEquals(Zone.NE, assigner.positiveZone(ZoneAssignmentStrategy.LARGEST_OVERLAP, overlaps, centroidZone))
    }
}
