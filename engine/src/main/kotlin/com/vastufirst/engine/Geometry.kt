package com.vastufirst.engine

import com.vastufirst.shared.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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
     * Do two simple polygons share a wall — i.e. do any of their edges lie on the same line and
     * overlap over a non-trivial length? Used for the pooja-shares-a-wall-with-toilet defect
     * (X-10). Rotation-invariant, so it works in either plan or true-North space.
     */
    fun sharesWall(a: List<Point>, b: List<Point>): Boolean {
        // Scale-relative tolerances so the engine stays scale-free (a plan in [0,1] units must
        // detect adjacency just like one in metres). Derive a characteristic length from the two
        // polygons' combined extent.
        val scale = characteristicLength(a, b).coerceAtLeast(1e-12)
        val perpTol = scale * REL_COLLINEAR
        val overlapTol = scale * REL_OVERLAP
        for (i in a.indices) {
            val a1 = a[i]; val a2 = a[(i + 1) % a.size]
            for (j in b.indices) {
                val b1 = b[j]; val b2 = b[(j + 1) % b.size]
                if (segmentsOverlapCollinear(a1, a2, b1, b2, perpTol, overlapTol)) return true
            }
        }
        return false
    }

    private fun characteristicLength(a: List<Point>, b: List<Point>): Double {
        val all = a + b
        val w = all.maxOf { it.x } - all.minOf { it.x }
        val h = all.maxOf { it.y } - all.minOf { it.y }
        return maxOf(w, h)
    }

    private fun segmentsOverlapCollinear(
        p1: Point, p2: Point, q1: Point, q2: Point, perpTol: Double, overlapTol: Double,
    ): Boolean {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = hypot(dx, dy)
        if (len < overlapTol) return false
        // q1, q2 must sit on the (infinite) line through p1→p2.
        if (perpDistance(p1, dx, dy, len, q1) > perpTol) return false
        if (perpDistance(p1, dx, dy, len, q2) > perpTol) return false
        // Project everything onto the p1→p2 axis and test 1-D interval overlap length.
        val tq1 = ((q1.x - p1.x) * dx + (q1.y - p1.y) * dy) / (len * len)
        val tq2 = ((q2.x - p1.x) * dx + (q2.y - p1.y) * dy) / (len * len)
        val lo = maxOf(0.0, minOf(tq1, tq2))
        val hi = minOf(1.0, maxOf(tq1, tq2))
        return (hi - lo) * len > overlapTol
    }

    private fun perpDistance(p1: Point, dx: Double, dy: Double, len: Double, q: Point): Double =
        abs((q.x - p1.x) * dy - (q.y - p1.y) * dx) / len

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

    private const val REL_COLLINEAR = 1e-6   // perpendicular tolerance, relative to plan scale
    private const val REL_OVERLAP = 1e-5     // min shared-wall length, relative to plan scale
}
