package com.vastufirst.engine

import com.vastufirst.shared.AnomalyConfig
import com.vastufirst.shared.AnomalyKind
import com.vastufirst.shared.Point
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAnomaly
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class AnomalyResult(
    val cuts: List<ZoneAnomaly>,
    val extensions: List<ZoneAnomaly>,
    val shapeIrregular: Boolean,
)

/**
 * Cuts and extensions against the REFERENCE RECTANGLE (Product PRD §4.2.7). A cut or extension
 * is only meaningful against an idealised rectangle, not the analysis rectangle (nothing can
 * protrude beyond its own bounding box). Where no modal edge exists, the engine reports
 * shapeIrregular = true and skips this analysis entirely rather than guessing.
 *
 * Both the cut region (inside reference, outside footprint) and the extension region (inside
 * footprint, outside reference) lie within the footprint's bounding box, so they are split by
 * the analysis-rectangle zone grid.
 */
internal class AnomalyDetector(private val cfg: AnomalyConfig) {

    fun detect(rotatedOutline: List<Point>, grid: PadaGrid): AnomalyResult {
        val bbox = Geometry.bbox(rotatedOutline)
        val reference = referenceRect(rotatedOutline, bbox)
            ?: return AnomalyResult(emptyList(), emptyList(), shapeIrregular = true)

        val refArea = reference.area
        if (refArea <= PadaGrid.AREA_EPS) return AnomalyResult(emptyList(), emptyList(), false)

        val cutByZone = HashMap<Zone, Double>()
        val extByZone = HashMap<Zone, Double>()
        for (zone in Zone.entries) {
            val z = grid.zoneRect(zone)
            // A zone can lie ENTIRELY outside the reference rectangle — that is exactly where a
            // deep extension protrudes (an outer band beyond the modal edge). Must NOT skip it:
            // its whole footprint area is then extension (footInRefZone = 0). Dropping it silently
            // loses X-05 (SW extension) for deep protrusions.
            val refZ = intersect(reference, z)
            val footInZone = Geometry.clipArea(rotatedOutline, z)
            val footInRefZone = if (refZ != null) Geometry.clipArea(rotatedOutline, refZ) else 0.0
            val cut = (refZ?.area ?: 0.0) - footInRefZone       // inside reference, outside footprint
            val ext = footInZone - footInRefZone                // inside footprint, outside reference
            if (cut > refArea * MIN_ZONE_SHARE) cutByZone[zone] = cut
            if (ext > refArea * MIN_ZONE_SHARE) extByZone[zone] = ext
        }

        return AnomalyResult(
            cuts = toAnomalies(AnomalyKind.CUT, cutByZone, refArea),
            extensions = toAnomalies(AnomalyKind.EXTENSION, extByZone, refArea),
            shapeIrregular = false,
        )
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
     * The largest cardinally-oriented rectangle implied by the modal edge of each side (§4.2.7).
     * For each side, the offset carrying ≥ modalEdgeFraction of that dimension's length is the
     * reference edge (the extreme-most qualifying offset). Null ⇒ no modal edge ⇒ irregular.
     */
    private fun referenceRect(outline: List<Point>, bbox: Rect): Rect? {
        val horiz = HashMap<Double, Double>()   // y-level -> total horizontal length
        val vert = HashMap<Double, Double>()     // x-level -> total vertical length
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[(i + 1) % outline.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            if (abs(dy) < EDGE_EPS && abs(dx) > EDGE_EPS) horiz.merge(round(a.y), abs(dx)) { x, y -> x + y }
            if (abs(dx) < EDGE_EPS && abs(dy) > EDGE_EPS) vert.merge(round(a.x), abs(dy)) { x, y -> x + y }
        }
        // A relative tolerance on the modal-edge threshold: a footprint whose wing is *exactly*
        // 40% of the side (edge exactly at the 60% threshold) must resolve deterministically, not
        // flip between "cut" and "irregular" on floating-point noise. Borderline ⇒ qualifies.
        val hThreshold = cfg.modalEdgeFraction * bbox.width - bbox.width * MODAL_EPS
        val vThreshold = cfg.modalEdgeFraction * bbox.height - bbox.height * MODAL_EPS
        val north = horiz.filterValues { it >= hThreshold }.keys.maxOrNull() ?: return null
        val south = horiz.filterValues { it >= hThreshold }.keys.minOrNull() ?: return null
        val east = vert.filterValues { it >= vThreshold }.keys.maxOrNull() ?: return null
        val west = vert.filterValues { it >= vThreshold }.keys.minOrNull() ?: return null
        if (north <= south || east <= west) return null
        return Rect(west, south, east, north)
    }

    private fun intersect(a: Rect, b: Rect): Rect? {
        val x0 = max(a.minX, b.minX)
        val y0 = max(a.minY, b.minY)
        val x1 = min(a.maxX, b.maxX)
        val y1 = min(a.maxY, b.maxY)
        return if (x1 > x0 && y1 > y0) Rect(x0, y0, x1, y1) else null
    }

    private fun round(v: Double): Double = Math.round(v * 1e6) / 1e6

    companion object {
        private const val EDGE_EPS = 1e-6
        private const val MIN_ZONE_SHARE = 0.01   // ignore per-zone slivers below 1% of reference
        private const val MODAL_EPS = 1e-9        // relative slack so an exact-threshold edge is stable
    }
}
