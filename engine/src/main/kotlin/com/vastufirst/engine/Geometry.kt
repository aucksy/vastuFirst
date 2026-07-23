package com.vastufirst.engine

import com.vastufirst.shared.Point
import kotlin.math.cos
import kotlin.math.sin

/** An axis-aligned rectangle in plan space (x east, y north). */
internal data class Rect(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val area: Double get() = width * height
    val centre: Point get() = Point((minX + maxX) / 2.0, (minY + maxY) / 2.0)

    fun asPolygon(): List<Point> =
        listOf(Point(minX, minY), Point(maxX, minY), Point(maxX, maxY), Point(minX, maxY))
}

/** Pure geometry helpers. No android.*, no framework types (§3.1). */
internal object Geometry {

    /** Signed area of a simple polygon (shoelace). Positive = counter-clockwise. */
    fun signedArea(poly: List<Point>): Double {
        var sum = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2.0
    }

    fun area(poly: List<Point>): Double = kotlin.math.abs(signedArea(poly))

    /**
     * Area centroid of a simple polygon (Product PRD §4.0 — the rotation origin).
     * Explicitly NOT the bounding-box centre or the vertex mean; they differ on the
     * L-shaped footprints §4.1 requires. Degenerate (zero-area) polygons fall back to
     * the vertex mean.
     */
    fun centroid(poly: List<Point>): Point {
        val a = signedArea(poly)
        if (kotlin.math.abs(a) < 1e-12) {
            val n = poly.size.toDouble()
            return Point(poly.sumOf { it.x } / n, poly.sumOf { it.y } / n)
        }
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val cross = p.x * q.y - q.x * p.y
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        val f = 1.0 / (6.0 * a)
        return Point(cx * f, cy * f)
    }

    /** Axis-aligned bounding box of a set of points. */
    fun bbox(poly: List<Point>): Rect {
        val minX = poly.minOf { it.x }
        val minY = poly.minOf { it.y }
        val maxX = poly.maxOf { it.x }
        val maxY = poly.maxOf { it.y }
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Rotate a point about [origin] to bring the plan into true-North alignment
     * (Product PRD §4.0): x' = x·cosθ − y·sinθ, y' = x·sinθ + y·cosθ, θ = northOffset radians,
     * applied to the point measured relative to the origin.
     */
    fun rotate(p: Point, degrees: Double, origin: Point): Point {
        if (degrees == 0.0) return p
        val theta = Math.toRadians(degrees)
        val c = cos(theta)
        val s = sin(theta)
        val dx = p.x - origin.x
        val dy = p.y - origin.y
        return Point(origin.x + dx * c - dy * s, origin.y + dx * s + dy * c)
    }

    fun rotatePoly(poly: List<Point>, degrees: Double, origin: Point): List<Point> =
        poly.map { rotate(it, degrees, origin) }

    /**
     * Area of the intersection of an arbitrary simple [subject] polygon with an axis-aligned
     * rectangle, via Sutherland–Hodgman clipping. The clip window is convex, so the clipped
     * polygon's shoelace area is exact even for concave (L-shaped) subjects.
     */
    fun clipArea(subject: List<Point>, rect: Rect): Double {
        if (subject.size < 3) return 0.0
        var output = subject
        // Clip against each of the four half-planes of the rectangle.
        output = clipHalfPlane(output) { it.x >= rect.minX } // keep x >= minX
        output = clipHalfPlane(output) { it.x <= rect.maxX }
        output = clipHalfPlane(output) { it.y >= rect.minY }
        output = clipHalfPlane(output) { it.y <= rect.maxY }
        if (output.size < 3) return 0.0
        return area(output)
    }

    private fun clipHalfPlane(poly: List<Point>, inside: (Point) -> Boolean): List<Point> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<Point>(poly.size + 4)
        for (i in poly.indices) {
            val cur = poly[i]
            val prev = poly[(i + poly.size - 1) % poly.size]
            val curIn = inside(cur)
            val prevIn = inside(prev)
            if (curIn) {
                if (!prevIn) out += intersect(prev, cur, inside)
                out += cur
            } else if (prevIn) {
                out += intersect(prev, cur, inside)
            }
        }
        return out
    }

    /** Intersection of segment a→b with the clip boundary (found by bisection on the inside test). */
    private fun intersect(a: Point, b: Point, inside: (Point) -> Boolean): Point {
        // The boundaries are axis-aligned; solve directly by parameterising and binary-searching t.
        var lo = 0.0
        var hi = 1.0
        // a is expected outside/inside opposite to b; find the crossing t.
        repeat(60) {
            val mid = (lo + hi) / 2.0
            val p = Point(a.x + (b.x - a.x) * mid, a.y + (b.y - a.y) * mid)
            if (inside(p) == inside(a)) lo = mid else hi = mid
        }
        val t = (lo + hi) / 2.0
        return Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
}
