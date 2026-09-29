package com.vastufirst.app.engine

import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.newplan.frontDoorFromEntrance
import com.vastufirst.app.ui.scan.scanRoomId
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.scan.RoomDimensions
import com.vastufirst.shared.scan.ScanDraft
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertTrue

/**
 * ⭐⭐ WHAT THE DIRECTION TRIAL WOULD COST THE REPORT — measured on real readings, for free, in CI.
 *
 * The owner, 29 Sep 2026, before any cheaper reader is tried: *"The engine scores some rules by AREA. A
 * room with any part over a forbidden zone is flagged, with partial credit for how much of it is over
 * the line. A room reduced to one point can lose those findings, and the project rule says a change
 * never deletes a finding from the report. So measure this for free first. Take the recorded reader
 * replies under tools/scan-eval, reduce each room to its direction, score both ways, and count the
 * rooms whose zone changes, the findings that vanish and the scores that move ... One idea to test:
 * the reader already reads each room's printed size, and a size plus a point can rebuild a footprint."*
 *
 * So every committed reply from TODAY's reader (`openai/gpt-5.6-luna`, prompt v6) goes through the app's
 * own path — mapper → grid → engine — and is scored three ways, at four Norths each:
 *
 *   · TODAY       — exactly what the app scores now.
 *   · POINT       — each room shrunk to the one pada its middle falls in: wholly inside ONE zone, the
 *                   room reduced to its direction. Big enough that the engine still flags it when that
 *                   zone is forbidden, so nothing vanishes just for being small.
 *   · SIZE+POINT  — each room rebuilt at the size its plan PRINTS, around the same middle; a room that
 *                   prints no size falls back to POINT, because a point-only reader would have nothing
 *                   else to build it from.
 *
 * Then, on the five sheets whose rooms were marked BY HAND (`truth-rooms.json`), the question under
 * the whole idea: how often is a room's DIRECTION right — today, as a point, and as a PERFECT point.
 *
 * ⚠ EVERY ZONE HERE IS THE ENGINE'S. The only geometry this file does is to BUILD an input — put a room
 * in one pada, or at a printed size — which is then scored by the real `VastuEngine`. The frame it
 * builds against mirrors the engine's own steps 1–2 (rotate about the outline's centroid, 9 × 9 padas
 * on the rotated bounding box); a room that lands in more than one zone anyway is COUNTED and printed
 * ("leaked"), so a drift between this mirror and the engine cannot hide.
 *
 * Measures and prints; asserts only that the corpus was genuinely read — the same honest order as
 * [RealPlanScoreTest]: measure first, set a bar on evidence later.
 */
class DirectionTrialMeasureTest {

    private fun say(line: String) = println("SCORE| $line")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val engine = VastuEngine()
    private val norths = listOf(0, 90, 180, 270)

    // ---------------------------------------------------------------- the corpus, as committed

    private class Recording(val id: String, val draft: ScanDraft, val imageW: Double, val imageH: Double)

    /** tools/scan-eval, found from the module directory CI runs the tests in (`app/`). */
    private fun scanEval(): File =
        listOf("../tools/scan-eval", "tools/scan-eval").map(::File).firstOrNull { it.isDirectory }
            ?: error("tools/scan-eval is not reachable from ${File(".").absolutePath}")

    private fun recordings(): List<Recording> =
        File(scanEval(), "out/live")
            .listFiles { f -> f.name.endsWith(".openai_gpt-5.6-luna.v6.json") }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { f ->
                val root = json.parseToJsonElement(f.readText()).jsonObject
                val reply = root["reply"] ?: return@mapNotNull null
                val size = root["imageSize"]?.jsonArray?.map { it.jsonPrimitive.double } ?: return@mapNotNull null
                Recording(
                    id = f.name.substringBefore(".openai_gpt-5.6-luna.v6.json"),
                    draft = json.decodeFromJsonElement(ScanDraft.serializer(), reply),
                    imageW = size[0],
                    imageH = size[1],
                )
            }

    // ---------------------------------------------------------------- the engine's frame, mirrored

    /** Polygon area centroid — the engine's rotation origin (Product PRD §4.0). */
    private fun centroid(poly: List<Point>): Point {
        var a = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            val cross = p.x * q.y - q.x * p.y
            a += cross
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        if (abs(a) < 1e-12) return Point(poly.sumOf { it.x } / poly.size, poly.sumOf { it.y } / poly.size)
        return Point(cx / (3.0 * a), cy / (3.0 * a))
    }

    private fun area(poly: List<Point>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            s += p.x * q.y - q.x * p.y
        }
        return abs(s) / 2.0
    }

    /** VastuEngine.runCore, steps 1–2: rotate by North about the outline's centroid; 9 × 9 padas on the result's box. */
    private class EngineFrame(outline: List<Point>, north: Int, origin: Point) {
        private val o = origin
        private val c = cos(Math.toRadians(north.toDouble()))
        private val s = sin(Math.toRadians(north.toDouble()))
        private fun rot(p: Point): Point {
            val dx = p.x - o.x
            val dy = p.y - o.y
            return Point(o.x + dx * c - dy * s, o.y + dx * s + dy * c)
        }
        private fun unrot(p: Point): Point {
            val dx = p.x - o.x
            val dy = p.y - o.y
            return Point(o.x + dx * c + dy * s, o.y - dx * s + dy * c)
        }
        private val r = outline.map(::rot)
        private val minX = r.minOf { it.x }
        private val maxX = r.maxOf { it.x }
        private val minY = r.minOf { it.y }
        private val maxY = r.maxOf { it.y }
        private val pw = (maxX - minX) / 9.0
        private val ph = (maxY - minY) / 9.0

        /** The pada the engine will put [p] in, as a polygon in the plan's OWN frame, a hair inside its edges. */
        fun padaAround(p: Point): List<Point> {
            val q = rot(p)
            val col = ((q.x - minX) / pw).toInt().coerceIn(0, 8)
            val row = ((maxY - q.y) / ph).toInt().coerceIn(0, 8)
            val x0 = minX + col * pw
            val yHigh = maxY - row * ph
            val e = 1e-6 * min(pw, ph)
            return listOf(
                Point(x0 + e, yHigh - ph + e), Point(x0 + pw - e, yHigh - ph + e),
                Point(x0 + pw - e, yHigh - e), Point(x0 + e, yHigh - e),
            ).map(::unrot)
        }
    }

    private fun Plan.level(): Level = levels.first()

    private fun Plan.withRooms(rooms: List<Room>): Plan = copy(levels = listOf(level().copy(rooms = rooms)))

    /** Every room in the one pada its middle falls in: the room reduced to its direction. */
    private fun asPoints(plan: Plan): Plan {
        val outline = plan.level().outline
        val frame = EngineFrame(outline, plan.northOffsetDegrees, centroid(outline))
        return plan.withRooms(plan.level().rooms.map { it.copy(polygon = frame.padaAround(centroid(it.polygon))) })
    }

    /**
     * Every room at the size its plan prints, around its own middle. A room with no printed size goes
     * in the one pada its middle falls in — or, with [keepUnsizedBox], keeps the rectangle it has today:
     * the "keep them another way" variant, where a reader points at every room and draws a rough box
     * only for a room whose plan prints no size. The plan's scale comes from its own sized rooms, so
     * the rebuilt rooms are drawn in the same units as everything else on it.
     */
    private fun asSizedPoints(
        plan: Plan,
        printed: Map<String, com.vastufirst.shared.scan.PrintedSize>,
        keepUnsizedBox: Boolean = false,
    ): Plan {
        val rooms = plan.level().rooms
        val sized = rooms.filter { printed[it.id] != null }
        val printedArea = sized.sumOf { printed.getValue(it.id).let { p -> p.widthMm * p.depthMm } }
        val unitsPerMm = if (printedArea > 0.0) sqrt(sized.sumOf { area(it.polygon) } / printedArea) else null
        val outline = plan.level().outline
        val frame = EngineFrame(outline, plan.northOffsetDegrees, centroid(outline))
        return plan.withRooms(
            rooms.map { room ->
                val mid = centroid(room.polygon)
                val p = printed[room.id]
                if (p == null || unitsPerMm == null) {
                    if (keepUnsizedBox) room else room.copy(polygon = frame.padaAround(mid))
                } else {
                    val hw = p.widthMm * unitsPerMm / 2.0
                    val hh = p.depthMm * unitsPerMm / 2.0
                    room.copy(
                        polygon = listOf(
                            Point(mid.x - hw, mid.y - hh), Point(mid.x + hw, mid.y - hh),
                            Point(mid.x + hw, mid.y + hh), Point(mid.x - hw, mid.y + hh),
                        ),
                    )
                }
            },
        )
    }

    private fun printedOf(room: ScannedRoom): com.vastufirst.shared.scan.PrintedSize? =
        if (room.readInParts.size > 1) null
        else RoomDimensions.parse(room.printedSize.ifBlank { room.label })

    // ---------------------------------------------------------------- the comparison

    /** One way of scoring measured against TODAY, summed over every plan and every North. */
    private class Tally {
        var rooms = 0
        var zoneChanged = 0
        var verdictChanged = 0
        var findingsToday = 0
        var vanished = 0
        var appeared = 0
        var plans = 0
        var scoreMoved = 0
        var scoreUp = 0
        var scoreDown = 0
        var sumAbsDelta = 0
        var maxAbsDelta = 0
        var leaked = 0
        val vanishedByRule = sortedMapOf<String, Int>()
        val vanishedByRoom = sortedMapOf<String, Int>()
        val appearedByRule = sortedMapOf<String, Int>()

        fun add(today: Analysis, other: Analysis) {
            plans++
            val t = today.roomResults.associateBy { it.roomId }
            for (o in other.roomResults) {
                val r = t[o.roomId] ?: continue
                rooms++
                if (r.zone != o.zone) zoneChanged++
                if (r.verdict != o.verdict) verdictChanged++
                if (o.verdict == Verdict.DEFECT && o.encroachedShare > 1e-6 && o.encroachedShare < 1 - 1e-6) leaked++
            }
            fun keys(a: Analysis) = a.defects.map { "${it.id}|${it.roomId}|${it.zone}" }.toSet()
            val kt = keys(today)
            val ko = keys(other)
            findingsToday += kt.size
            val gone = kt - ko
            vanished += gone.size
            val came = ko - kt
            appeared += came.size
            for (c in came) {
                val rule = c.substringBefore('|')
                appearedByRule[rule] = (appearedByRule[rule] ?: 0) + 1
            }
            for (g in gone) {
                val rule = g.substringBefore('|')
                vanishedByRule[rule] = (vanishedByRule[rule] ?: 0) + 1
                val roomType = today.roomResults.firstOrNull { it.roomId == g.split('|')[1] }?.type?.name ?: "no room"
                vanishedByRoom[roomType] = (vanishedByRoom[roomType] ?: 0) + 1
            }
            val d = other.score - today.score
            if (d != 0) scoreMoved++
            if (d > 0) scoreUp++
            if (d < 0) scoreDown++
            sumAbsDelta += abs(d)
            maxAbsDelta = max(maxAbsDelta, abs(d))
        }

        fun pct(n: Int, of: Int) = if (of == 0) "-" else "${(1000L * n / of) / 10.0}%"
    }

    @Test
    fun `a room reduced to its direction, scored against today, on every recorded reading`() {
        val recs = recordings()
        val point = Tally()
        val sized = Tally()
        val hybrid = Tally()
        var placed = 0
        var roomsSeen = 0
        var roomsUnsized = 0
        var partialToday = 0
        val perPlan = mutableListOf<String>()

        for (rec in recs) {
            val outcome = ScanMapper.map(rec.draft, imageAspect = rec.imageW / rec.imageH) as? ScanOutcome.Placed ?: continue
            placed++
            val grid = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
            val door = frontDoorFromEntrance(grid)
            val printed = outcome.rooms.withIndex()
                .mapNotNull { (i, r) -> printedOf(r)?.let { scanRoomId(i) to it } }.toMap()
            roomsSeen += outcome.rooms.size
            roomsUnsized += outcome.rooms.size - printed.size
            for (north in norths) {
                val plan = buildEnginePlan(grid, door, Intent.BUILDING, PropertyType.FLAT, north, rec.id) ?: continue
                val today = engine.analyze(plan)
                val asPoint = engine.analyze(asPoints(plan))
                val asSized = engine.analyze(asSizedPoints(plan, printed))
                val asHybrid = engine.analyze(asSizedPoints(plan, printed, keepUnsizedBox = true))
                point.add(today, asPoint)
                sized.add(today, asSized)
                hybrid.add(today, asHybrid)
                partialToday += today.roomResults.count { it.verdict == Verdict.DEFECT && it.encroachedShare < 1 - 1e-6 }
                if (north == 0) {
                    perPlan += rec.id.take(26).padEnd(27) +
                        "${today.score / 10.0}".padEnd(7) + "${asPoint.score / 10.0}".padEnd(7) + "${asSized.score / 10.0}".padEnd(7) +
                        "${today.defects.size}".padEnd(5) + "${asPoint.defects.size}".padEnd(5) + "${asSized.defects.size}"
                }
            }
        }

        say("")
        say("DIRECTION TRIAL — a room reduced to its direction, scored against today")
        say("today's reader (gpt-5.6-luna, prompt v6): $placed of ${recs.size} committed readings place their rooms; scored at Norths $norths")
        say("rooms: $roomsSeen, of which $roomsUnsized print no size of their own (they fall back to a point under SIZE+POINT)")
        say("rooms flagged today only PARTLY over a forbidden zone (the ones partial credit exists for): $partialToday room-readings")
        val cols = listOf(point, sized, hybrid)
        fun row(label: String, cell: (Tally) -> String) = say(label.padEnd(26) + cols.joinToString("") { cell(it).padEnd(24) })
        fun mean(t: Tally) = if (t.plans > 0) (t.sumAbsDelta * 10L / t.plans) / 100.0 else 0.0
        say("")
        row("") { t -> when (t) { point -> "as a POINT"; sized -> "SIZE + POINT"; else -> "SIZE + POINT, box kept" } }
        row("") { t -> when (t) { point -> "(the room's middle)"; sized -> "(no size: a point)"; else -> "(no size: today's box)" } }
        row("rooms whose zone changes") { "${it.zoneChanged} of ${it.rooms} (${it.pct(it.zoneChanged, it.rooms)})" }
        row("rooms whose verdict moves") { "${it.verdictChanged} (${it.pct(it.verdictChanged, it.rooms)})" }
        row("findings today") { "${it.findingsToday}" }
        row("findings that VANISH") { "${it.vanished} (${it.pct(it.vanished, it.findingsToday)})" }
        row("findings that APPEAR") { "${it.appeared}" }
        row("scores that move") { "${it.scoreMoved} of ${it.plans}" }
        row("  up / down") { "${it.scoreUp} / ${it.scoreDown}" }
        row("  mean / largest (of 10)") { "${mean(it)} / ${it.maxAbsDelta / 10.0}" }
        say("self-check, rooms that leaked past one zone (must be 0): point ${point.leaked}")
        say("findings that vanish as a POINT, by rule: ${point.vanishedByRule}")
        say("findings that vanish as a POINT, by room: ${point.vanishedByRoom}")
        say("findings that appear as a POINT, by rule: ${point.appearedByRule}")
        say("findings that vanish as SIZE+POINT, by rule: ${sized.vanishedByRule}")
        say("findings that vanish as SIZE+POINT, by room: ${sized.vanishedByRoom}")
        say("findings that appear as SIZE+POINT, by rule: ${sized.appearedByRule}")
        say("findings that vanish as SIZE+POINT with the box kept, by rule: ${hybrid.vanishedByRule}")
        say("findings that vanish as SIZE+POINT with the box kept, by room: ${hybrid.vanishedByRoom}")
        say("findings that appear as SIZE+POINT with the box kept, by rule: ${hybrid.appearedByRule}")
        say("")
        say("North 0 per plan        today  point  sized  finds today/point/sized")
        perPlan.forEach { say(it) }

        // ⚠ Floors, not targets: they fail only if the corpus silently stopped being read.
        assertTrue(placed >= 10, "only $placed recorded readings placed their rooms — the corpus is not being read")
        assertTrue(point.rooms > 100, "only ${point.rooms} rooms compared — the measurement is too thin to mean anything")
    }

    // ---------------------------------------------------------------- the hand-marked rooms

    private class TruthRoom(val label: String, val x: Double, val y: Double, val w: Double, val h: Double)

    private fun truthSheets(): Map<String, List<TruthRoom>> {
        val root = json.parseToJsonElement(File(scanEval(), "truth-rooms.json").readText()).jsonObject
        return root.filterKeys { !it.startsWith("_") }.mapValues { (_, v) ->
            v.jsonArray.map {
                val o = it.jsonObject
                TruthRoom(
                    label = o.getValue("label").jsonPrimitive.content,
                    x = o.getValue("x").jsonPrimitive.double,
                    y = o.getValue("y").jsonPrimitive.double,
                    w = o.getValue("w").jsonPrimitive.double,
                    h = o.getValue("h").jsonPrimitive.double,
                )
            }
        }
    }

    /** Every way a room's direction is worked out, compared against the hand-marked room's. */
    private val V_TODAY = "today's reader, as the app scores it now:"
    private val V_POINT = "the same reading, each room reduced to one point:"
    private val V_SIZED = "the same reading, printed size around each room's middle:"
    private val V_HYBRID = "…the same, but a room with no printed size keeps today's box:"
    private val V_PERFECT = "a PERFECT point (the true room's own middle):"
    private val V_PERFECT_SIZED = "a perfect point + the size the plan prints:"
    private val V_PERFECT_HYBRID = "…the same, but a room with no printed size keeps its true box:"
    private val V_PAGE_BOX = "the reader's boxes as drawn on the page (no grid, no reshape):"
    private val V_PAGE_POINT = "…each reduced to one point, the middle of its box:"
    private val V_PAGE_HYBRID = "…printed size around each box's middle, box kept if no size:"
    private val variants = listOf(
        V_TODAY, V_POINT, V_SIZED, V_HYBRID,
        V_PAGE_BOX, V_PAGE_POINT, V_PAGE_HYBRID,
        V_PERFECT, V_PERFECT_SIZED, V_PERFECT_HYBRID,
    )
    private val short = mapOf(
        V_TODAY to "today", V_POINT to "point", V_SIZED to "size+point", V_HYBRID to "size+point|box",
        V_PAGE_BOX to "page box", V_PAGE_POINT to "page point", V_PAGE_HYBRID to "page size+point|box",
        V_PERFECT to "perfect point", V_PERFECT_SIZED to "perfect size+point", V_PERFECT_HYBRID to "perfect size+point|box",
    )

    private fun norm(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()

    private fun iou(ax: Double, ay: Double, aw: Double, ah: Double, bx: Double, by: Double, bw: Double, bh: Double): Double {
        val ix = max(0.0, min(ax + aw, bx + bw) - max(ax, bx))
        val iy = max(0.0, min(ay + ah, by + bh) - max(ay, by))
        val inter = ix * iy
        val union = aw * ah + bw * bh - inter
        return if (union <= 0.0) 0.0 else inter / union
    }

    @Test
    fun `is the direction right — today, as a point, and as a perfect point, against the hand-marked rooms`() {
        val recs = recordings().associateBy { it.id }
        val sheets = truthSheets()
        var compared = 0
        val right = variants.associateWith { 0 }.toMutableMap()
        var missed = 0
        val lines = mutableListOf<String>()

        for ((stem, truth) in sheets) {
            val rec = recs[stem]
            val outcome = rec?.let { ScanMapper.map(it.draft, imageAspect = it.imageW / it.imageH) as? ScanOutcome.Placed }
            if (rec == null || outcome == null) {
                lines += "${stem.take(26).padEnd(27)}not placed by today's reader — nothing to compare"
                continue
            }
            // Match each hand-marked room to the reader's room with the same caption, on the reader's
            // OWN rectangle when a caption repeats — the rule truth-rooms.json was marked under.
            val taken = mutableSetOf<Int>()
            val pairs = mutableListOf<Pair<TruthRoom, Int>>()
            val candidates = truth.flatMap { t ->
                outcome.rooms.withIndex().filter { (_, r) ->
                    val a = norm(r.label)
                    val b = norm(t.label)
                    a.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
                }.map { (i, r) ->
                    val s = r.source
                    Triple(t, i, if (s == null) 0.0 else iou(t.x, t.y, t.w, t.h, s.x, s.y, s.w, s.h))
                }
            }.sortedByDescending { it.third }
            val placedTruth = mutableSetOf<TruthRoom>()
            for ((t, i, _) in candidates) {
                if (t in placedTruth || i in taken) continue
                pairs += t to i
                placedTruth += t
                taken += i
            }
            missed += truth.size - pairs.size
            if (pairs.isEmpty()) continue

            // THE TRUE HOME, in the sheet's own pixels with North up: the hand-marked rectangles as
            // rooms, the box around all of them as its outline. Scored by the same engine.
            val w = rec.imageW
            val h = rec.imageH
            fun poly(t: TruthRoom) = listOf(
                Point(t.x * w, (1 - t.y - t.h) * h), Point((t.x + t.w) * w, (1 - t.y - t.h) * h),
                Point((t.x + t.w) * w, (1 - t.y) * h), Point(t.x * w, (1 - t.y) * h),
            )
            val all = truth.flatMap(::poly)
            val outline = listOf(
                Point(all.minOf { it.x }, all.minOf { it.y }), Point(all.maxOf { it.x }, all.minOf { it.y }),
                Point(all.maxOf { it.x }, all.maxOf { it.y }), Point(all.minOf { it.x }, all.maxOf { it.y }),
            )
            val trueRooms = pairs.map { (t, i) -> Room(id = scanRoomId(i), type = outcome.rooms[i].type, polygon = poly(t)) }
            val printed = pairs.mapNotNull { (_, i) -> printedOf(outcome.rooms[i])?.let { scanRoomId(i) to it } }.toMap()
            val grid = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
            val door = frontDoorFromEntrance(grid)

            // The app's reading may carry rooms no hand-marked room matched; they still share the home.
            val printedAll = outcome.rooms.withIndex()
                .mapNotNull { (i, r) -> printedOf(r)?.let { scanRoomId(i) to it } }.toMap()

            // THE READER'S OWN HOME, in the SAME pixels as the true one: every room it placed, as the
            // box it drew on the page (`source`), and the box around all of them as the outline. No
            // grid and no reshaping — so it compares with the true home frame for frame, and it is
            // the very frame a point-only reader's answer would be rebuilt in (a point on the page,
            // the size the plan prints).
            fun pagePoly(b: com.vastufirst.shared.scan.ScanBox) = listOf(
                Point(b.x * w, (1 - b.y - b.h) * h), Point((b.x + b.w) * w, (1 - b.y - b.h) * h),
                Point((b.x + b.w) * w, (1 - b.y) * h), Point(b.x * w, (1 - b.y) * h),
            )
            val readerRooms = outcome.rooms.withIndex().mapNotNull { (i, r) ->
                r.source?.let { Room(id = scanRoomId(i), type = r.type, polygon = pagePoly(it)) }
            }
            val rAll = readerRooms.flatMap { it.polygon }
            val readerOutline = if (rAll.isEmpty()) outline else listOf(
                Point(rAll.minOf { it.x }, rAll.minOf { it.y }), Point(rAll.maxOf { it.x }, rAll.minOf { it.y }),
                Point(rAll.maxOf { it.x }, rAll.maxOf { it.y }), Point(rAll.minOf { it.x }, rAll.maxOf { it.y }),
            )
            val sheet = variants.associateWith { 0 }.toMutableMap()
            var sheetN = 0
            for (north in norths) {
                val truePlan = Plan(
                    id = "truth-$stem",
                    propertyType = PropertyType.FLAT,
                    intent = Intent.BUILDING,
                    levels = listOf(Level(index = 0, outline = outline, rooms = trueRooms)),
                    northOffsetDegrees = north,
                )
                val app = buildEnginePlan(grid, door, Intent.BUILDING, PropertyType.FLAT, north, stem) ?: continue
                val pagePlan = truePlan.copy(
                    id = "reader-page-$stem",
                    levels = listOf(Level(index = 0, outline = readerOutline, rooms = readerRooms)),
                )
                fun zones(p: Plan) = engine.analyze(p).roomResults.associate { it.roomId to it.zone }
                val trueZones = zones(truePlan)
                val zonesBy = mapOf(
                    V_PAGE_BOX to zones(pagePlan),
                    V_PAGE_POINT to zones(asPoints(pagePlan)),
                    V_PAGE_HYBRID to zones(asSizedPoints(pagePlan, printedAll, keepUnsizedBox = true)),
                    V_TODAY to zones(app),
                    V_POINT to zones(asPoints(app)),
                    V_SIZED to zones(asSizedPoints(app, printedAll)),
                    V_HYBRID to zones(asSizedPoints(app, printedAll, keepUnsizedBox = true)),
                    V_PERFECT to zones(asPoints(truePlan)),
                    V_PERFECT_SIZED to zones(asSizedPoints(truePlan, printed)),
                    V_PERFECT_HYBRID to zones(asSizedPoints(truePlan, printed, keepUnsizedBox = true)),
                )
                for ((_, i) in pairs) {
                    val id = scanRoomId(i)
                    val truthZone = trueZones[id] ?: continue
                    sheetN++
                    for ((v, z) in zonesBy) if (z[id] == truthZone) sheet[v] = sheet.getValue(v) + 1
                }
            }
            compared += sheetN
            for (v in variants) right[v] = right.getValue(v) + sheet.getValue(v)
            lines += stem.take(26).padEnd(27) + "${pairs.size}/${truth.size} rooms matched  " +
                variants.joinToString("  ") { "${short.getValue(it)} ${sheet.getValue(it)}/$sheetN" }
        }

        fun pct(n: Int) = if (compared == 0) "-" else "${(1000L * n / compared) / 10.0}%"
        say("")
        say("DIRECTION TRIAL — is a room's DIRECTION right? Against the rooms marked by hand (truth-rooms.json), Norths $norths")
        for (v in variants) say("${v.padEnd(62)}${right.getValue(v)} of $compared (${pct(right.getValue(v))})")
        say("hand-marked rooms the reader never matched: $missed")
        lines.forEach { say(it) }

        assertTrue(compared >= 40, "only $compared room-readings compared against the hand-marked rooms — too few to mean anything")
    }
}
