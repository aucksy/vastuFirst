package com.vastufirst.engine

import com.vastufirst.shared.Point
import com.vastufirst.shared.Zone

/**
 * The 81-pada square grid (Product PRD §4.2). Built on the ANALYSIS RECTANGLE — the smallest
 * cardinally-oriented bounding rectangle of the already-rotated outline. The grid is ALWAYS
 * cardinally aligned and NEVER tilted (Samarangana Sutradhara): a building sitting diagonally
 * on its plot sits diagonally inside a square, north-aligned grid.
 *
 * Zones are computed on this square grid, NOT 45° pie sectors (§0.5).
 */
internal class PadaGrid(
    val rect: Rect,
    val gridSize: Int,
) {
    private val padaW = rect.width / gridSize
    private val padaH = rect.height / gridSize
    private val band = gridSize / 3   // 3 for a 9×9 grid

    /** Pada rectangle at [row][col]. row 0 = North (top / max y), col 0 = West (min x). */
    fun padaRect(row: Int, col: Int): Rect {
        val x0 = rect.minX + col * padaW
        val yHigh = rect.maxY - row * padaH        // row 0 is the northern (top) strip
        val yLow = rect.maxY - (row + 1) * padaH
        return Rect(x0, yLow, x0 + padaW, yHigh)
    }

    /** Zone of a pada per §4.2 step 4 (CENTRAL_3×3 Brahmasthan). */
    fun zoneOf(row: Int, col: Int): Zone {
        val rb = bandOf(row)   // 0 = North, 1 = middle, 2 = South
        val cb = bandOf(col)   // 0 = West, 1 = middle, 2 = East
        return ZONE_MAP[rb][cb]
    }

    private fun bandOf(i: Int): Int = when {
        i < band -> 0
        i < 2 * band -> 1
        else -> 2
    }

    /** Which pada a point falls in, clamped to the grid. Used by the CENTROID strategy. */
    fun padaOf(p: Point): Pair<Int, Int> {
        val col = (((p.x - rect.minX) / padaW).toInt()).coerceIn(0, gridSize - 1)
        // row 0 is the northern strip, so row grows as y falls from maxY.
        val row = (((rect.maxY - p.y) / padaH).toInt()).coerceIn(0, gridSize - 1)
        return row to col
    }

    fun zoneOf(p: Point): Zone {
        val (r, c) = padaOf(p)
        return zoneOf(r, c)
    }

    /** The band rectangle a whole zone occupies (union of its 3×3 padas). Used for anomalies. */
    fun zoneRect(zone: Zone): Rect {
        val xw = rect.minX + band * padaW
        val xe = rect.minX + 2 * band * padaW
        val ys = rect.minY + band * padaH
        val yn = rect.maxY - band * padaH
        val (x0, x1) = when (zone) {
            Zone.NW, Zone.W, Zone.SW -> rect.minX to xw
            Zone.N, Zone.BRAHMASTHAN, Zone.S -> xw to xe
            Zone.NE, Zone.E, Zone.SE -> xe to rect.maxX
        }
        val (y0, y1) = when (zone) {
            Zone.NW, Zone.N, Zone.NE -> yn to rect.maxY
            Zone.W, Zone.BRAHMASTHAN, Zone.E -> ys to yn
            Zone.SW, Zone.S, Zone.SE -> rect.minY to ys
        }
        return Rect(x0, y0, x1, y1)
    }

    /**
     * A zone's band rectangle with its OUTER bands extended far beyond the analysis rectangle.
     * The three cardinal band-lines (fixed to the Brahmasthan) are what actually define a
     * direction; the finite grid is only cropped to the footprint's bounding box. When a
     * building's own reference rectangle is tilted and pokes past that box at the very corner
     * being assessed (e.g. a cut NE corner), the outward extension keeps that area attributable
     * to the correct cardinal zone instead of being lost off the edge of the grid.
     */
    fun zoneRectExtended(zone: Zone): Rect {
        val margin = 2.0 * (rect.width + rect.height) + 1.0
        val xw = rect.minX + band * padaW
        val xe = rect.minX + 2 * band * padaW
        val ys = rect.minY + band * padaH
        val yn = rect.maxY - band * padaH
        val (x0, x1) = when (zone) {
            Zone.NW, Zone.W, Zone.SW -> (rect.minX - margin) to xw
            Zone.N, Zone.BRAHMASTHAN, Zone.S -> xw to xe
            Zone.NE, Zone.E, Zone.SE -> xe to (rect.maxX + margin)
        }
        val (y0, y1) = when (zone) {
            Zone.NW, Zone.N, Zone.NE -> yn to (rect.maxY + margin)
            Zone.W, Zone.BRAHMASTHAN, Zone.E -> ys to yn
            Zone.SW, Zone.S, Zone.SE -> (rect.minY - margin) to ys
        }
        return Rect(x0, y0, x1, y1)
    }

    /** Overlap area of [poly] with every pada, and the aggregate by zone. */
    fun overlaps(poly: List<Point>): PadaOverlaps {
        val perPada = HashMap<Pair<Int, Int>, Double>()
        val perZone = HashMap<Zone, Double>()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val a = Geometry.clipArea(poly, padaRect(row, col))
                if (a > AREA_EPS) {
                    perPada[row to col] = a
                    val z = zoneOf(row, col)
                    perZone[z] = (perZone[z] ?: 0.0) + a
                }
            }
        }
        return PadaOverlaps(perPada, perZone)
    }

    companion object {
        const val AREA_EPS = 1e-9
        private val ZONE_MAP = arrayOf(
            arrayOf(Zone.NW, Zone.N, Zone.NE),
            arrayOf(Zone.W, Zone.BRAHMASTHAN, Zone.E),
            arrayOf(Zone.SW, Zone.S, Zone.SE),
        )
    }
}

internal data class PadaOverlaps(
    val perPada: Map<Pair<Int, Int>, Double>,
    val perZone: Map<Zone, Double>,
) {
    val totalArea: Double get() = perZone.values.sum()
}
