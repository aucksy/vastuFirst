package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.shared.DoorLocationMethod
import com.vastufirst.shared.DoorPada
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Point
import com.vastufirst.shared.RulesetConfig
import kotlin.math.abs
import kotlin.math.atan2

internal data class ResolvedDoor(
    val pada: DoorPada,
    val bearing: Double,
    val verdict: PadaVerdict,
    val spansTwoPadas: Boolean,
)

/**
 * The 32-pada door analysis (Product PRD §4.3). The entrance is the highest-weighted element,
 * computed angularly (not on the grid) — the M-07 hybrid. **The engine never scores the
 * building's facing direction, only the main door's pada** (§0.4, §4.3).
 *
 * Inputs are already rotated to true-North by the caller.
 */
internal class DoorResolver(private val ruleSet: RuleSet, private val config: RulesetConfig) {

    fun resolve(centre: Point, wallStart: Point, wallEnd: Point, rect: Rect): ResolvedDoor {
        val bearing = when (config.doorLocationMethod) {
            DoorLocationMethod.BEARING_FROM_CENTRE -> bearingFromCentre(centre, rect)
            DoorLocationMethod.PROPORTION_ALONG_WALL ->
                proportionAlongWall(centre, wallStart, wallEnd, rect)
                    ?: bearingFromCentre(centre, rect)   // re-entrant wall → always-defined fallback
        }
        val primary = ruleSet.padaAtBearing(bearing)

        // §4.3.3: within tolerance of a pada boundary ⇒ spans both, take the less auspicious.
        val offset = norm(bearing - primary.startBearing).let { if (it >= RuleSet.PADA_SPAN) it - 360.0 else it }
        val distLower = offset
        val distUpper = RuleSet.PADA_SPAN - offset
        var effective = primary
        var spans = false
        val tol = config.padaBoundaryToleranceDeg
        if (distLower in 0.0..tol) {
            val neighbour = ruleSet.padaAtBearing(norm(primary.startBearing - tol - 0.5))
            effective = lessAuspicious(primary, neighbour); spans = true
        } else if (distUpper in 0.0..tol) {
            val neighbour = ruleSet.padaAtBearing(norm(primary.startBearing + RuleSet.PADA_SPAN + 0.5))
            effective = lessAuspicious(primary, neighbour); spans = true
        }

        // The reported pada id is always the one the bearing actually falls in (primary);
        // only the verdict is downgraded when it straddles a boundary (§4.3.3, worked example).
        return ResolvedDoor(primary, bearing, effective.verdict, spans)
    }

    /** bearing = atan2(dx, dy) clockwise from North, from the analysis-rectangle centre. */
    private fun bearingFromCentre(centre: Point, rect: Rect): Double {
        val c = rect.centre
        return norm(Math.toDegrees(atan2(centre.x - c.x, centre.y - c.y)))
    }

    /**
     * Determine which side the door's wall belongs to, divide it into 8 equal linear segments
     * walking clockwise (N→E→S→W), and take the segment's pada. Returns null for a re-entrant
     * wall touching no side of the analysis rectangle (caller falls back to bearing).
     */
    private fun proportionAlongWall(centre: Point, wallStart: Point, wallEnd: Point, rect: Rect): Double? {
        val tol = (rect.width + rect.height) * 0.02
        val side: Char
        val frac: Double
        val onNorth = abs(wallStart.y - rect.maxY) < tol && abs(wallEnd.y - rect.maxY) < tol
        val onSouth = abs(wallStart.y - rect.minY) < tol && abs(wallEnd.y - rect.minY) < tol
        val onEast = abs(wallStart.x - rect.maxX) < tol && abs(wallEnd.x - rect.maxX) < tol
        val onWest = abs(wallStart.x - rect.minX) < tol && abs(wallEnd.x - rect.minX) < tol
        when {
            onNorth -> { side = 'N'; frac = (centre.x - rect.minX) / rect.width }              // NW→NE, +x
            onEast -> { side = 'E'; frac = (rect.maxY - centre.y) / rect.height }               // NE→SE, −y
            onSouth -> { side = 'S'; frac = (rect.maxX - centre.x) / rect.width }               // SE→SW, −x
            onWest -> { side = 'W'; frac = (centre.y - rect.minY) / rect.height }               // SW→NW, +y
            else -> return null
        }
        val seg = (frac * 8.0).toInt().coerceIn(0, 7) + 1     // segment index → pada suffix 1..8
        val pada = ruleSet.doorPadas.first { it.id == "$side$seg" }
        // Return the pada's mid-bearing so downstream resolution lands squarely inside it.
        return norm(pada.startBearing + RuleSet.PADA_SPAN / 2.0)
    }

    private fun lessAuspicious(a: DoorPada, b: DoorPada): DoorPada =
        if (rank(a.verdict) <= rank(b.verdict)) a else b

    private fun rank(v: PadaVerdict): Int = when (v) {
        PadaVerdict.INAUSPICIOUS -> 0
        PadaVerdict.MIXED -> 1
        PadaVerdict.MODERATE -> 2
        PadaVerdict.AUSPICIOUS -> 3
    }

    private fun norm(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}
