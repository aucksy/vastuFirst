package com.vastufirst.engine

import com.vastufirst.shared.Point
import kotlin.math.abs
import kotlin.math.atan2
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

    /**
     * Clip [subject] (may be concave) by a CONVEX clip polygon given CCW, returning the clipped
     * polygon. Used for the reference rectangle when it is rotated to the building's own axes
     * (a rotated rectangle is a convex quad, not axis-aligned). Shoelace area of the result is
     * exact for a convex clip window.
     */
    fun clipByConvex(subject: List<Point>, convex: List<Point>): List<Point> {
        if (subject.size < 3 || convex.size < 3) return emptyList()
        var output = subject
        for (i in convex.indices) {
            if (output.size < 3) return emptyList()
            val a = convex[i]
            val b = convex[(i + 1) % convex.size]
            // interior of a CCW convex polygon is on the LEFT of each directed edge a→b.
            output = clipHalfPlane(output) { p -> cross(a, b, p) >= -HALFPLANE_EPS }
        }
        return output
    }

    fun clipAreaByConvex(subject: List<Point>, convex: List<Point>): Double {
        val out = clipByConvex(subject, convex)
        return if (out.size < 3) 0.0 else area(out)
    }

    /** z-component of (b−a) × (p−a); positive ⇒ p is left of the directed edge a→b. */
    private fun cross(a: Point, b: Point, p: Point): Double =
        (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)

    /**
     * The footprint's OWN dominant orientation, in degrees within [0, 90) — the tilt of the
     * building's principal axis relative to true-North east/x. For a rectilinear footprint (walls
     * meeting at right angles — essentially every real building) all edges lie at this angle or
     * 90° from it, so shape analysis done in this frame is rotation-invariant: a clean rectangle
     * reads clean at any angle. Length-weighted so long walls dominate short jogs; computed with
     * the doubled-then-doubled angle trick to average correctly modulo 90°.
     */
    fun dominantOrientation(poly: List<Point>): Double {
        if (poly.size < 2) return 0.0
        var sumSin = 0.0
        var sumCos = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            if (len < 1e-12) continue
            val ang = atan2(dy, dx)          // edge angle, period 180° for an undirected edge
            // Map modulo 90° onto a full circle via ×4, average, divide back.
            sumSin += len * sin(4.0 * ang)
            sumCos += len * cos(4.0 * ang)
        }
        if (abs(sumSin) < 1e-12 && abs(sumCos) < 1e-12) return 0.0
        var deg = Math.toDegrees(atan2(sumSin, sumCos) / 4.0)
        deg %= 90.0
        if (deg < 0) deg += 90.0
        // Snap a near-cardinal footprint to exactly 0° so it takes the identity fast path and
        // stays bit-for-bit consistent (a building 0.0001° off is indistinguishable from aligned).
        if (deg < TILT_SNAP_DEG || deg > 90.0 - TILT_SNAP_DEG) return 0.0
        return deg
    }

    fun isFinite(p: Point): Boolean = p.x.isFinite() && p.y.isFinite()

    /** Drop non-finite points and collapse consecutive (and closing) near-duplicate vertices. */
    fun cleanRing(poly: List<Point>): List<Point> {
        val finite = poly.filter(::isFinite)
        if (finite.isEmpty()) return emptyList()
        val span = run {
            val xs = finite.map { it.x }; val ys = finite.map { it.y }
            (xs.max() - xs.min()) + (ys.max() - ys.min())
        }
        val tol = (span * 1e-9).coerceAtLeast(1e-12)
        val out = ArrayList<Point>(finite.size)
        for (p in finite) {
            val last = out.lastOrNull()
            if (last == null || hypot(p.x - last.x, p.y - last.y) > tol) out += p
        }
        while (out.size > 1 && hypot(out.first().x - out.last().x, out.first().y - out.last().y) <= tol) {
            out.removeAt(out.size - 1)
        }
        return out
    }

    /** Does a simple polygon self-intersect (any non-adjacent edge pair crossing)? O(n²). */
    fun isSelfIntersecting(poly: List<Point>): Boolean {
        val n = poly.size
        if (n < 4) return false
        for (i in 0 until n) {
            val a1 = poly[i]; val a2 = poly[(i + 1) % n]
            for (j in i + 1 until n) {
                // skip adjacent/shared-vertex edges
                if (j == i) continue
                if ((j + 1) % n == i || (i + 1) % n == j) continue
                val b1 = poly[j]; val b2 = poly[(j + 1) % n]
                if (segmentsProperlyIntersect(a1, a2, b1, b2)) return true
            }
        }
        return false
    }

    private fun segmentsProperlyIntersect(p1: Point, p2: Point, p3: Point, p4: Point): Boolean {
        val d1 = cross(p3, p4, p1)
        val d2 = cross(p3, p4, p2)
        val d3 = cross(p1, p2, p3)
        val d4 = cross(p1, p2, p4)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** Convex hull (Andrew's monotone chain), CCW. A safe fallback footprint for a messy trace. */
    fun convexHull(points: List<Point>): List<Point> {
        val pts = points.filter(::isFinite).distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        val lower = ArrayList<Point>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = ArrayList<Point>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper += p
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
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
    private const val HALFPLANE_EPS = 1e-9   // keep points exactly on a convex clip edge
    private const val TILT_SNAP_DEG = 1e-4   // snap a near-cardinal footprint to 0° (identity path)
}
