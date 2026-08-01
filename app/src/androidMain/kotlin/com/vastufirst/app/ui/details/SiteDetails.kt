package com.vastufirst.app.ui.details

import com.vastufirst.shared.Fixture
import com.vastufirst.shared.FixtureType
import com.vastufirst.shared.Point
import com.vastufirst.shared.Road
import com.vastufirst.shared.Site
import com.vastufirst.shared.Zone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ⭐ THE THINGS THE FREE SCORE NEVER LOOKED AT (docs/SCORE-ACCURACY-CAVEATS.md #4).
 *
 * The score has only ever been computed from rooms, the front door and the home's shape. Water
 * storage, heavy trees and the road outside all have real rules in the engine — and all of them sat
 * permanently in "couldn't check these yet", because nothing ever collected the inputs. So the
 * headline number read as a complete verdict while being, in fact, a ceiling: a home with its water
 * tank in the worst possible corner scored exactly the same as one with it in the best.
 *
 * ⚠ WHY DIRECTIONS AND NOT POSITIONS. Every rule that uses these inputs asks the same question of
 * them — which of the nine zones is it in. Nothing asks for a coordinate. So making the user drag a
 * marker onto a plan would demand far more precision than any rule can use, and would be the hardest
 * thing in the app for the person it is built for. A direction is exactly as precise as the rule,
 * and it is a question anybody can answer standing in their own home.
 */

/** One thing outside the rooms that the rules can judge, once we know where it is. */
enum class SiteItem(
    val question: String,
    val help: String,
    val fixture: FixtureType?,
) {
    OVERHEAD_TANK(
        question = "Water tank on the roof",
        help = "The overhead tank. Best in the south-west or west; the north-east is the one to avoid.",
        fixture = FixtureType.OVERHEAD_TANK,
    ),
    UNDERGROUND_WATER(
        question = "Underground tank, sump or borewell",
        help = "Water kept below ground. Best in the north-east; the south-west is the one to avoid.",
        fixture = FixtureType.UNDERGROUND_WATER,
    ),
    HEAVY_TREE(
        question = "A big, heavy tree",
        help = "A large tree close to the home. The north-east and the centre are the places it weighs on.",
        fixture = FixtureType.HEAVY_TREE,
    ),
    ROAD(
        question = "A road pointing straight at your home",
        help = "A road that ends at your home rather than passing it — a T-junction or a dead end.",
        fixture = null,
    ),
}

/** The user's answers. A direction of null means "there isn't one" — which is a real answer. */
data class SiteAnswers(
    val answers: Map<SiteItem, Zone> = emptyMap(),
    /** Items the user has said "no, there isn't one" to, as opposed to simply not having reached. */
    val declined: Set<SiteItem> = emptySet(),
) {
    fun isAnswered(item: SiteItem) = item in answers || item in declined
    val answeredCount: Int get() = SiteItem.entries.count { isAnswered(it) }
    val isEmpty: Boolean get() = answers.isEmpty() && declined.isEmpty()
}

/** The eight directions a person can point at, in compass order. The centre is never offered — nothing
 *  here can sensibly be "in the middle" of a home except a tree, and that is a different question. */
val ANSWERABLE_ZONES: List<Zone> = listOf(
    Zone.N, Zone.NE, Zone.E, Zone.SE, Zone.S, Zone.SW, Zone.W, Zone.NW,
)

/**
 * Where a thing the user placed "in the north-east" actually goes in PLAN coordinates.
 *
 * ⚠ The user names a TRUE-NORTH direction; the engine is handed PLAN coordinates and rotates them
 * itself. So the point has to be computed where the engine will look for it and then rotated BACK,
 * or a home drawn at an angle would file every fixture in the wrong zone — silently, and only for
 * the users whose homes are not square to the compass, which in India is most of them.
 *
 * The steps mirror the engine's own exactly: rotate the outline to true north about its AREA
 * centroid, take the bounding rectangle, take the centre of the requested zone's third-band, then
 * undo the rotation. A test scores real plans at several angles and reads back where the engine put
 * each fixture, so this cannot drift from the engine's own arithmetic without failing.
 */
fun fixturePoint(outline: List<Point>, north: Int, zone: Zone): Point {
    if (outline.size < 3) return Point(0.0, 0.0)
    val origin = areaCentroid(outline)
    val rotated = outline.map { rotate(it, north.toDouble(), origin) }
    val minX = rotated.minOf { it.x }; val maxX = rotated.maxOf { it.x }
    val minY = rotated.minOf { it.y }; val maxY = rotated.maxOf { it.y }
    // Thirds: the middle of the western band is one sixth in, the middle of the eastern band five
    // sixths in. Same split the engine's pada grid makes.
    fun band(lo: Double, hi: Double, third: Int) = lo + (hi - lo) * (third * 2 + 1) / 6.0
    val col = when (zone) {
        Zone.NW, Zone.W, Zone.SW -> 0
        Zone.N, Zone.BRAHMASTHAN, Zone.S -> 1
        else -> 2
    }
    // row 0 is the NORTH band, i.e. the HIGH-y end, so the row index counts down from maxY.
    val row = when (zone) {
        Zone.NW, Zone.N, Zone.NE -> 0
        Zone.W, Zone.BRAHMASTHAN, Zone.E -> 1
        else -> 2
    }
    val target = Point(band(minX, maxX, col), band(minY, maxY, 2 - row))
    return rotate(target, -north.toDouble(), origin)
}

/** Every fixture the answers imply, positioned for the given outline and North. */
fun fixturesFor(answers: SiteAnswers, outline: List<Point>, north: Int): List<Fixture> =
    answers.answers.entries
        .mapNotNull { (item, zone) ->
            val type = item.fixture ?: return@mapNotNull null
            Fixture(id = "fx-${item.name.lowercase()}", type = type, position = fixturePoint(outline, north, zone))
        }

/**
 * The site, or null when nothing about it was answered. The plot outline is the home's own outline —
 * the app does not collect a separate plot, and nothing in the engine reads it today; the roads are
 * what carry the rule.
 */
fun siteFor(answers: SiteAnswers, outline: List<Point>): Site? {
    val roadZone = answers.answers[SiteItem.ROAD]
    val declinedRoad = SiteItem.ROAD in answers.declined
    if (roadZone == null && !declinedRoad) return null
    return Site(
        plotOutline = outline,
        roads = roadZone?.let { listOf(Road(id = "road-1", bearingDegrees = bearingOf(it), pointsAtPlot = true)) }
            ?: emptyList(),
    )
}

/**
 * Which zone a plan-space point falls in, once North is applied — the exact inverse of
 * [fixturePoint], and the reason a reopened home does not quietly LOSE the extra details it was
 * scored with. Without this, opening a saved home and touching anything would rebuild its plan from
 * an empty answer sheet and drop every fixture it had.
 */
fun zoneOfPoint(outline: List<Point>, north: Int, p: Point): Zone {
    if (outline.size < 3) return Zone.BRAHMASTHAN
    val origin = areaCentroid(outline)
    val rotated = outline.map { rotate(it, north.toDouble(), origin) }
    val q = rotate(p, north.toDouble(), origin)
    val minX = rotated.minOf { it.x }; val maxX = rotated.maxOf { it.x }
    val minY = rotated.minOf { it.y }; val maxY = rotated.maxOf { it.y }
    fun band(v: Double, lo: Double, hi: Double): Int {
        if (hi - lo <= 0.0) return 1
        val f = (v - lo) / (hi - lo)
        return when {
            f < 1.0 / 3.0 -> 0
            f < 2.0 / 3.0 -> 1
            else -> 2
        }
    }
    val col = band(q.x, minX, maxX)
    val rowFromSouth = band(q.y, minY, maxY)
    val row = 2 - rowFromSouth        // row 0 is the NORTH band
    return ZONE_GRID[row][col]
}

private val ZONE_GRID = arrayOf(
    arrayOf(Zone.NW, Zone.N, Zone.NE),
    arrayOf(Zone.W, Zone.BRAHMASTHAN, Zone.E),
    arrayOf(Zone.SW, Zone.S, Zone.SE),
)

/** Rebuild the answers from a saved plan, so reopening a home never drops what it was scored with. */
fun siteAnswersFromPlan(
    outline: List<Point>,
    north: Int,
    fixtures: List<Fixture>,
    site: Site?,
): SiteAnswers {
    val byType = SiteItem.entries.mapNotNull { item -> item.fixture?.let { it to item } }.toMap()
    val answers = LinkedHashMap<SiteItem, Zone>()
    for (fx in fixtures) {
        val item = byType[fx.type] ?: continue
        answers[item] = zoneOfPoint(outline, north, fx.position)
    }
    val declined = LinkedHashSet<SiteItem>()
    if (site != null) {
        val road = site.roads.firstOrNull { it.pointsAtPlot }
        // ⚠ A road bearing is stored as a bearing, not a zone, so it comes back through the same 45°
        // sectors the engine judges it by. That round-trips exactly for the eight answers we offer.
        if (road != null) answers[SiteItem.ROAD] = zoneOfBearing(road.bearingDegrees)
        else declined += SiteItem.ROAD
    }
    // ⚠ KNOWN LIMIT, stated rather than hidden: "there isn't one" for a FIXTURE cannot be told apart
    // from "never asked" once saved — a plan has somewhere to record a tank, but nowhere to record
    // the absence of one. So a reopened home shows those questions as unanswered. It costs the user
    // one tap to say so again, and it can never lose a real answer, which is the half that matters.
    return SiteAnswers(answers = answers, declined = declined)
}

/** The eight-point zone a compass bearing falls in — the same sectors the engine's road rule uses. */
fun zoneOfBearing(bearingDeg: Int): Zone {
    val b = ((bearingDeg % 360) + 360) % 360
    val sectors = arrayOf(Zone.N, Zone.NE, Zone.E, Zone.SE, Zone.S, Zone.SW, Zone.W, Zone.NW)
    return sectors[(((b + 22.5) / 45.0).toInt()) % 8]
}

/** The compass bearing at the centre of a zone's 45° sector — the inverse of the engine's own mapping. */
fun bearingOf(zone: Zone): Int = when (zone) {
    Zone.N -> 0; Zone.NE -> 45; Zone.E -> 90; Zone.SE -> 135
    Zone.S -> 180; Zone.SW -> 225; Zone.W -> 270; Zone.NW -> 315
    Zone.BRAHMASTHAN -> 0
}

// --- the two bits of geometry, mirrored from the engine (whose own copy is internal) ---

/** Area centroid of a simple polygon — the engine's rotation origin, and NOT the bbox centre. */
fun areaCentroid(poly: List<Point>): Point {
    var twiceArea = 0.0
    for (i in poly.indices) {
        val p = poly[i]
        val q = poly[(i + 1) % poly.size]
        twiceArea += p.x * q.y - q.x * p.y
    }
    val a = twiceArea / 2.0
    if (abs(a) < 1e-12) {
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

private fun rotate(p: Point, degrees: Double, origin: Point): Point {
    if (degrees == 0.0) return p
    val t = Math.toRadians(degrees)
    val c = cos(t)
    val s = sin(t)
    val dx = p.x - origin.x
    val dy = p.y - origin.y
    return Point(origin.x + dx * c - dy * s, origin.y + dx * s + dy * c)
}
