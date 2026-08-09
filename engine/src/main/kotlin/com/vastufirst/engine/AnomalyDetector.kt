package com.vastufirst.engine

import com.vastufirst.shared.AnomalyConfig
import com.vastufirst.shared.AnomalyKind
import com.vastufirst.shared.Point
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAnomaly
import kotlin.math.abs

internal data class AnomalyResult(
    val cuts: List<ZoneAnomaly>,
    val extensions: List<ZoneAnomaly>,
    val shapeIrregular: Boolean,
    val tiltDegrees: Double,     // the building's own principal-axis tilt vs true north (0..90)
    val aspectRatio: Double,     // long side / short side of the footprint, in its own frame
)

/**
 * Cuts and extensions against the building's own REFERENCE RECTANGLE (Product PRD §4.2.7,
 * refined by field research). Shape regularity is judged in the building's OWN principal-axis
 * frame — so a clean rectangle reads clean at ANY angle to true north (the common case in India,
 * where plots follow the street grid, not the compass) and never falls back to a scary
 * "irregular". The detected cut/extension regions are then attributed to CARDINAL zones via the
 * fixed true-North grid, because severity is direction-keyed (a missing North-East corner is the
 * worst defect; South-West/South-East/North-West cuts are lesser).
 *
 * `shapeIrregular` now means only "not a rectangle-family footprint" (triangle/round/wildly
 * non-orthogonal) — genuinely rare — and even then the engine still produces a full score; it
 * never surfaces as a dead-end to the user.
 */
internal class AnomalyDetector(private val cfg: AnomalyConfig) {

    fun detect(rotatedOutline: List<Point>, grid: PadaGrid): AnomalyResult {
        if (rotatedOutline.size < 3) return empty(0.0, 1.0, shapeIrregular = true)

        // 1. The building's own orientation, and its outline expressed in that frame.
        val tilt = Geometry.dominantOrientation(rotatedOutline)
        val origin = Geometry.centroid(rotatedOutline)
        val building = if (tilt == 0.0) rotatedOutline else Geometry.rotatePoly(rotatedOutline, -tilt, origin)
        val bboxB = Geometry.bbox(building)
        val aspect = aspectOf(bboxB)

        // 2. The reference rectangle in the building's own frame (modal edges of its own walls).
        val refB = referenceRect(building, bboxB)
            ?: return empty(tilt, aspect, shapeIrregular = true)   // non-rectangular footprint
        val refArea = refB.area
        if (refArea <= PadaGrid.AREA_EPS) return empty(tilt, aspect, shapeIrregular = false)

        // 3. Bring the reference rectangle back into true-North space (a convex, possibly tilted quad).
        val refPolyTN = if (tilt == 0.0) refB.asPolygon()
        else Geometry.rotatePoly(refB.asPolygon(), tilt, origin)
        val refAspect = aspectOf(refB)

        // 4. Attribute cut/extension area to CARDINAL zones (grid fixed to true North). Outer bands
        //    are extended so a tilted reference poking past the footprint bbox stays attributable.
        val cutByZone = HashMap<Zone, Double>()
        val extByZone = HashMap<Zone, Double>()
        for (zone in Zone.entries) {
            val zq = grid.zoneRectExtended(zone).asPolygon()
            val footInZone = Geometry.clipAreaByConvex(rotatedOutline, zq)
            val refInZone = Geometry.clipAreaByConvex(refPolyTN, zq)
            val footInRefInZone = Geometry.clipAreaByConvex(Geometry.clipByConvex(rotatedOutline, refPolyTN), zq)
            val cut = (refInZone - footInRefInZone).coerceAtLeast(0.0)   // inside reference, outside footprint
            val ext = (footInZone - footInRefInZone).coerceAtLeast(0.0)  // inside footprint, outside reference
            if (cut > refArea * MIN_ZONE_SHARE) cutByZone[zone] = cut
            if (ext > refArea * MIN_ZONE_SHARE) extByZone[zone] = ext
        }

        return AnomalyResult(
            cuts = toAnomalies(AnomalyKind.CUT, cutByZone, refArea),
            extensions = toAnomalies(AnomalyKind.EXTENSION, extByZone, refArea),
            shapeIrregular = false,
            tiltDegrees = tilt,
            aspectRatio = refAspect,
        )
    }

    private fun empty(tilt: Double, aspect: Double, shapeIrregular: Boolean) =
        AnomalyResult(emptyList(), emptyList(), shapeIrregular, tilt, aspect)

    /**
     * Long side over short side.
     *
     * ⚠ QUANTISED, and it has to be. This is compared with a bare `>` against the 1:2 hard flag, so a
     * home whose proportions are EXACTLY 1:2 — an extremely ordinary builder's rectangle — was
     * decided by the last bits of a division. `PlanConversionRoundTripTest` caught the same two-room
     * home being called "long and narrow" in one corner of the canvas and not in another, purely
     * because its width and height are derived from the footprint's absolute coordinates.
     *
     * It had been latent for as long as the check has existed: with the old all-or-nothing
     * encroachment those plans scored below zero and clamped to 0 either way, so the one-point
     * difference could never surface. Partial credit lifted them off the floor and it did.
     *
     * Four places is finer than any proportion a reader could perceive and coarse enough that
     * position cannot decide the answer (§0.4). At exactly 1:2 the ratio now lands on 2.0 and the
     * `>` is false — which is what the spec means by "ideal WITHIN 1:1–1:2".
     */
    private fun aspectOf(r: Rect): Double {
        val lo = minOf(r.width, r.height)
        val hi = maxOf(r.width, r.height)
        if (lo <= PadaGrid.AREA_EPS) return 1.0
        return Math.round((hi / lo) * 10_000.0) / 10_000.0
    }

    private fun toAnomalies(kind: AnomalyKind, byZone: Map<Zone, Double>, refArea: Double): List<ZoneAnomaly> {
        val total = byZone.values.sum()
        val totalShare = total / refArea
        if (totalShare < cfg.minorAreaFraction) return emptyList()   // below 5% ⇒ ignored
        val severity = if (totalShare >= cfg.majorAreaFraction) Severity.MAJOR else Severity.MINOR
        return byZone.entries
            .sortedByDescending { it.value }
            .map { ZoneAnomaly(kind, it.key, it.value / refArea, severity) }
    }

    /**
     * The largest axis-aligned rectangle implied by the modal edge of each side (§4.2.7), computed
     * in whatever frame [outline] is given (here, the building's own frame). Null ⇒ the footprint
     * has no dominant rectangular edges (a genuinely non-rectangular shape).
     */
    private fun referenceRect(outline: List<Point>, bbox: Rect): Rect? {
        val span = bbox.width + bbox.height
        if (span <= PadaGrid.AREA_EPS) return null
        val edgeEps = span * REL_EDGE          // scale-free "is this edge axis-aligned"
        val gran = span * REL_GRAN             // scale-free offset-grouping granularity
        val horiz = HashMap<Double, Double>()  // y-level -> total horizontal length
        val vert = HashMap<Double, Double>()   // x-level -> total vertical length
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[(i + 1) % outline.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            if (abs(dy) < edgeEps && abs(dx) > edgeEps) horiz.merge(snap(a.y, gran), abs(dx)) { x, y -> x + y }
            if (abs(dx) < edgeEps && abs(dy) > edgeEps) vert.merge(snap(a.x, gran), abs(dy)) { x, y -> x + y }
        }
        // Relative tolerance so a footprint whose wing sits exactly on the 60% threshold resolves
        // deterministically (borderline ⇒ qualifies) rather than flipping on floating-point noise.
        val hThreshold = cfg.modalEdgeFraction * bbox.width - bbox.width * MODAL_EPS
        val vThreshold = cfg.modalEdgeFraction * bbox.height - bbox.height * MODAL_EPS
        val north = horiz.filterValues { it >= hThreshold }.keys.maxOrNull() ?: return null
        val south = horiz.filterValues { it >= hThreshold }.keys.minOrNull() ?: return null
        val east = vert.filterValues { it >= vThreshold }.keys.maxOrNull() ?: return null
        val west = vert.filterValues { it >= vThreshold }.keys.minOrNull() ?: return null
        if (north <= south || east <= west) return null
        return Rect(west, south, east, north)
    }

    private fun snap(v: Double, gran: Double): Double = Math.round(v / gran) * gran

    companion object {
        private const val REL_EDGE = 1e-7         // edge counts as axis-aligned within span*this
        private const val REL_GRAN = 1e-6         // group edge offsets within span*this
        private const val MIN_ZONE_SHARE = 0.01   // ignore per-zone slivers below 1% of reference
        private const val MODAL_EPS = 1e-9        // relative slack so an exact-threshold edge is stable
    }
}
