package com.vastufirst.app.engine

import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.newplan.frontDoorFromEntrance
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.engine.VastuEngine
import com.vastufirst.rules.RuleSet
import com.vastufirst.rules.RuleSetLoader
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GRID = 9

private val ZONE_BANDS = arrayOf(
    arrayOf(Zone.NW, Zone.N, Zone.NE),
    arrayOf(Zone.W, Zone.BRAHMASTHAN, Zone.E),
    arrayOf(Zone.SW, Zone.S, Zone.SE),
)

/** Today: the middle third of each side — nine of the eighty-one squares (§4.2 step 4). */
private val CENTRE_3X3: (Int, Int) -> Zone = { row, col -> ZONE_BANDS[row / 3][col / 3] }

/**
 * The single middle square. The other eight squares of the middle block lean to whichever direction
 * they sit toward, which is why this can MOVE a finding rather than remove it — a toilet clipping
 * the middle may come back as a toilet clipping the north-east.
 */
private val CENTRE_1X1: (Int, Int) -> Zone = { row, col ->
    if (row in 3..5 && col in 3..5) ZONE_BANDS[row - 3][col - 3] else ZONE_BANDS[row / 3][col / 3]
}

/**
 * ⭐⭐ MEASURING BENCH FOR THE CENTRE-OF-THE-HOME RULE — numbers before opinions.
 *
 * **Why this exists.** The middle ninth of a plan is forbidden to six of the thirteen scored room
 * types, and in a real flat something almost always sits over the middle. Measured before v0.9.0,
 * more than half of every problem raised across the real recorded plans was a room touching the
 * centre. Partial credit (§4.5.2b) softened the arithmetic but not the detection, and the owner
 * asked for the remaining candidates to be MEASURED across all eight real plans and the §15
 * acceptance fixture before any of them is built.
 *
 * **How it works, and why it is allowed to be believed.** Every plan is scored ONCE by the real
 * engine. The bench then re-derives the score for each candidate from the engine's OWN per-pada
 * overlap areas, using the §4.5 arithmetic and the real ruleset values — so a candidate costs no
 * extra cloud round trip. It is a twin, and a twin is worthless unless it is checked: the first
 * candidate is `today`, and the run FAILS unless the twin reproduces the engine's real score
 * exactly, plan by plan. If the twin cannot reproduce today, nothing it prints about tomorrow may
 * be used. That check is the whole reason this file is trustworthy (see also §4.5 — "the engine's
 * own printed numbers are the authority").
 *
 * ⚠ **It changes nothing about the app.** No shipped default moves, no new config value is read,
 * no screen and no user step is touched. It only prints. Every line carries the `SCORE|` marker so
 * CI lifts it out of the test-results XML into the build log, where it can actually be read —
 * Gradle otherwise swallows test stdout and the measurement would sit in a file nobody opens.
 */
class CentreRuleWhatIfTest {

    // ───────────────────────── the candidates ─────────────────────────

    /**
     * One hypothesis about the centre rule. Everything defaults to today's behaviour, so each
     * candidate names only what it changes.
     *
     * ⚠ [roomShareFloor] and [centreCoverageFloor] only ever REMOVE a flag, and removing a flag
     * removes a finding from the reader's report. That cost is counted and printed for every
     * candidate (the `finds` and `lost` columns) because it is the one way this work can cost real
     * accuracy — see the standing rule that a scoring change must never delete a finding.
     */
    private data class Candidate(
        val label: String,
        /** Which zone each of the 81 squares belongs to. Changing this changes the centre's SIZE. */
        val zoneAt: (Int, Int) -> Zone = CENTRE_3X3,
        /** A flag needs at least this share of the ROOM over the line. Null = the ruleset's 2 %. */
        val roomShareFloor: Double? = null,
        /** Apply [roomShareFloor] to the centre alone, or to every forbidden direction. */
        val centreOnly: Boolean = true,
        /** A centre flag also needs the room to cover this share OF THE CENTRE. 0 = off. */
        val centreCoverageFloor: Double = 0.0,
        /** Share of a room over the line at which its fine reaches full strength. Null = 0.5. */
        val fullPenaltyShare: Double? = null,
        /** Points for a room the tradition is silent about. Null = the ruleset's 45. */
        val suboptimalPoints: Int? = null,
        /** Leave a silent-placement room out of the score entirely, rather than scoring it. */
        val suboptimalNotScored: Boolean = false,
        /** Points for a front door on an unfavourable position. Null = the ruleset's 15. */
        val unfavourableDoorPoints: Int? = null,
    )

    private val candidates: List<Candidate> = listOf(
        Candidate("today (baseline — must match the engine)"),

        // (a) Demand a real bite of the ROOM before the centre counts — centre only.
        Candidate("centre needs 1/20 of the room", roomShareFloor = 0.05),
        Candidate("centre needs 1/10 of the room", roomShareFloor = 0.10),
        Candidate("centre needs 1/5 of the room", roomShareFloor = 0.20),

        // (a') The same, applied to EVERY forbidden direction — the side effect worth seeing: this
        // also stops flagging a toilet clipping the sacred north-east corner.
        Candidate("every direction needs 1/10 of the room", roomShareFloor = 0.10, centreOnly = false),
        Candidate("every direction needs 1/5 of the room", roomShareFloor = 0.20, centreOnly = false),

        // (e) Judge the centre by how much of the CENTRE is covered. "Keep the middle open" is a
        // statement about the middle, so a room over 3 % of it and one over 60 % should not read alike.
        Candidate("centre needs 1/4 of the centre covered", centreCoverageFloor = 0.25),
        Candidate("centre needs 1/2 of the centre covered", centreCoverageFloor = 0.50),

        // (b) Shrink the centre to the single middle square. NOTE: this is not one of the three
        // readings §13 M-05 says a reviewer might endorse, and it can MOVE a flag rather than remove
        // it (the eight other middle squares lean to the direction they sit toward), so a room may
        // gain a north-east or south-west finding it did not have.
        Candidate("centre is the single middle square", zoneAt = CENTRE_1X1),

        // (f) Pure arithmetic: let the fine ramp all the way instead of maxing out at halfway.
        Candidate("fine ramps to full only when wholly over", fullPenaltyShare = 1.0),

        // The two extras the owner asked for in the same run — free, no extra round trip.
        Candidate("silent placement scores 6.0 not 4.5", suboptimalPoints = 60),
        Candidate("silent placement scores 7.0 not 4.5", suboptimalPoints = 70),
        Candidate("silent placement not scored at all", suboptimalNotScored = true),
        Candidate("unfavourable door scores 4.0 not 1.5", unfavourableDoorPoints = 40),
    )

    // ───────────────────────── the run ─────────────────────────

    @Test
    fun `every candidate for the centre rule is measured against the real plans`() {
        val ruleSet = RuleSetLoader.loadDefault()
        val cases = buildCases()
        val real = cases.filter { it.isReal }

        say("")
        say("═══ CENTRE-OF-THE-HOME RULE — what each candidate does to real homes ═══")
        say("legend  " + cases.joinToString("  ") { "${it.short}=${it.id}" })
        say("        med = middle of the eight real homes · finds = problems raised across those")
        say("        eight · centre = how many of those are centre problems · lost = problems the")
        say("        reader stops being told about, against today")
        say("")

        val baseline = measure(ruleSet, cases, candidates.first())
        reportBaselineEvidence(baseline, real)

        val header = "candidate".padEnd(40) +
            cases.joinToString("") { it.short.padEnd(7) } +
            "med".padEnd(6) + "finds".padEnd(7) + "centre".padEnd(8) + "lost"
        say(header)
        say("-".repeat(header.length))

        val rows = candidates.map { it to measure(ruleSet, cases, it) }
        for ((candidate, result) in rows) {
            val realScores = real.map { result.scoreOf.getValue(it.id) }
            say(
                candidate.label.padEnd(40) +
                    cases.joinToString("") { outOfTen(result.scoreOf.getValue(it.id)).padEnd(7) } +
                    outOfTen(realScores.sorted()[realScores.size / 2]).padEnd(6) +
                    result.findings.toString().padEnd(7) +
                    result.centreFindings.toString().padEnd(8) +
                    (baseline.findings - result.findings).toString(),
            )
        }
        say("")

        // ⭐ The twin has no authority of its own. If it cannot reproduce today's real numbers plan
        // by plan, every other row above is noise and this run must go red rather than be read.
        val mismatches = cases.filter { baseline.scoreOf.getValue(it.id) != it.analysis.score }
        mismatches.forEach {
            say("TWIN DISAGREES  ${it.id}: bench says ${baseline.scoreOf.getValue(it.id)}, engine says ${it.analysis.score}")
        }
        say(
            if (mismatches.isEmpty()) {
                "twin check: reproduced the engine exactly on all ${cases.size} plans — the rows above are usable"
            } else {
                "twin check: FAILED on ${mismatches.size} plan(s) — do not use the rows above"
            },
        )
        say("")

        assertTrue(real.size >= 8, "only ${real.size} real plans scored — the corpus is not being read")
        assertEquals(
            31,
            cases.first { it.id == "sample-01" }.analysis.score,
            "the §15 worked example must stay at exactly 31",
        )
        assertTrue(mismatches.isEmpty(), "the what-if bench does not reproduce the engine: ${mismatches.map { it.id }}")
    }

    /** Today's picture, in the terms the decision actually turns on. */
    private fun reportBaselineEvidence(baseline: Measured, real: List<Case>) {
        say("today, per home — how much of each problem list is the centre:")
        for (case in real) {
            val centre = baseline.centreDetail.getValue(case.id)
            val all = baseline.findingsOf.getValue(case.id)
            val shares = centre.joinToString(" ") { "${it.type.name.lowercase()}@${pct(it.share)}" }
            say("  ${case.short.padEnd(7)}${centre.size} of $all problems are the centre   $shares")
        }
        val totalCentre = baseline.centreFindings
        say("  → ${totalCentre} of ${baseline.findings} problems across the eight homes are the centre")
        say("")
    }

    // ───────────────────────── the twin ─────────────────────────

    private data class CentreHit(val roomId: String, val type: RoomType, val share: Double)

    private data class Measured(
        val scoreOf: Map<String, Int>,
        val findingsOf: Map<String, Int>,
        /** Problems raised across the eight REAL homes only — the fixtures are not a reader. */
        val findings: Int,
        val centreFindings: Int,
        val centreDetail: Map<String, List<CentreHit>>,
    )

    private fun measure(ruleSet: RuleSet, cases: List<Case>, candidate: Candidate): Measured {
        val scoreOf = HashMap<String, Int>()
        val findingsOf = HashMap<String, Int>()
        val centreDetail = HashMap<String, List<CentreHit>>()
        var findings = 0
        var centreFindings = 0

        for (case in cases) {
            val rooms = case.analysis.roomResults.map { evaluate(ruleSet, candidate, case, it) }
            val defects = rebuildDefects(ruleSet, case, rooms)
            scoreOf[case.id] = scoreOf(ruleSet, candidate, rooms, case, defects)
            findingsOf[case.id] = defects.size
            centreDetail[case.id] = rooms.filter { Zone.BRAHMASTHAN in it.flagged }
                .map { CentreHit(it.id, it.type, it.share) }
            if (case.isReal) {
                findings += defects.size
                centreFindings += defects.count { it.zone == Zone.BRAHMASTHAN && it.roomId != null }
            }
        }
        return Measured(scoreOf, findingsOf, findings, centreFindings, centreDetail)
    }

    /** One room under one candidate. Mirrors RoomEvaluator, reading the engine's own overlaps. */
    private data class TwinRoom(
        val id: String,
        val type: RoomType,
        val notScored: Boolean,
        val points: Int,
        val weight: Double,
        val flagged: List<Zone>,
        val share: Double,
    )

    private fun evaluate(ruleSet: RuleSet, c: Candidate, case: Case, rr: RoomResult): TwinRoom {
        val rule = rr.rule
        if (rule == null || rule.disputeId != null || rule.excludeFromScore) {
            return TwinRoom(rr.roomId, rr.type, notScored = true, 0, rr.weight, emptyList(), 1.0)
        }

        val perZone = HashMap<Zone, Double>()
        for ((cell, area) in rr.padaOverlap) {
            val zone = c.zoneAt(cell.first, cell.second)
            perZone[zone] = (perZone[zone] ?: 0.0) + area
        }
        // The engine measures a room by its own polygon, not by the sum of its square overlaps.
        // Using the polygon keeps the threshold and the share on exactly the engine's arithmetic;
        // the overlap sum is only the fallback for a room the plan no longer carries.
        val roomArea = case.roomArea[rr.roomId] ?: rr.padaOverlap.values.sum()
        val centroidZone = case.centroidCell[rr.roomId]?.let { c.zoneAt(it.first, it.second) } ?: rr.zone
        val positiveZone = largestOverlap(ruleSet, perZone, centroidZone)

        // Mirrors RoomEvaluator: the threshold-passing prohibited zones, plus the credited zone
        // itself when that is prohibited (a room sitting IN the centre is flagged whatever the
        // threshold — no candidate here silences that, and none should).
        val flagged = LinkedHashSet<Zone>()
        flagged += encroached(ruleSet, c, case, rule.prohibited, perZone, roomArea)
        if (positiveZone in rule.prohibited) flagged += positiveZone

        val baseVerdict = when (positiveZone) {
            in rule.ideal -> Verdict.IDEAL
            in rule.acceptable -> Verdict.ACCEPTABLE
            in rule.prohibited -> Verdict.DEFECT
            else -> Verdict.SUBOPTIMAL
        }
        val verdict = if (flagged.isEmpty()) baseVerdict else Verdict.DEFECT

        // A room the tradition is silent about, raising no problem at all, can be left out of the
        // score entirely rather than scored at 45 — one of the two extras being measured.
        if (c.suboptimalNotScored && flagged.isEmpty() && baseVerdict == Verdict.SUBOPTIMAL) {
            return TwinRoom(rr.roomId, rr.type, notScored = true, 0, rule.weight, emptyList(), 1.0)
        }

        val share = if (flagged.isEmpty() || roomArea <= 0.0) {
            1.0
        } else {
            val bad = flagged.sumOf { perZone[it] ?: 0.0 }
            quantise(bad / roomArea)
        }
        return TwinRoom(
            rr.roomId, rr.type, notScored = false,
            points = pointsFor(ruleSet, c, verdict, baseVerdict, flagged.isNotEmpty(), share),
            weight = rule.weight, flagged = flagged.toList(), share = share,
        )
    }

    private fun encroached(
        ruleSet: RuleSet,
        c: Candidate,
        case: Case,
        prohibited: Set<Zone>,
        perZone: Map<Zone, Double>,
        roomArea: Double,
    ): List<Zone> {
        if (prohibited.isEmpty()) return emptyList()
        val cfg = ruleSet.config.encroachment
        val rectFloor = case.analysisRectArea * cfg.analysisRectAreaFraction
        val centreArea = case.padaArea * centreSquares(c)
        return prohibited
            .mapNotNull { z -> perZone[z]?.let { z to it } }
            .filter { (zone, overlap) ->
                val isCentre = zone == Zone.BRAHMASTHAN
                val floor = when {
                    c.roomShareFloor == null -> cfg.roomAreaFraction
                    c.centreOnly && !isCentre -> cfg.roomAreaFraction
                    else -> c.roomShareFloor
                }
                val coverageOk = !isCentre || c.centreCoverageFloor <= 0.0 ||
                    (centreArea > 0.0 && overlap >= centreArea * c.centreCoverageFloor)
                overlap >= roomArea * floor && overlap >= rectFloor && coverageOk
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun largestOverlap(ruleSet: RuleSet, perZone: Map<Zone, Double>, centroidZone: Zone): Zone {
        if (perZone.isEmpty()) return centroidZone
        val max = perZone.values.max()
        val within = perZone.filter { it.value >= max * (1.0 - ruleSet.config.tieBreak.withinFraction) }.keys
        if (within.size == 1) return within.first()
        if (centroidZone in within) return centroidZone
        return ruleSet.config.tieBreak.zonePrecedence.firstOrNull { it in within } ?: within.first()
    }

    private fun pointsFor(
        ruleSet: RuleSet,
        c: Candidate,
        verdict: Verdict,
        baseVerdict: Verdict,
        flagged: Boolean,
        share: Double,
    ): Int {
        val floor = scorePoints(ruleSet, c, Verdict.DEFECT)
        val plain = scorePoints(ruleSet, c, verdict)
        if (!flagged || !ruleSet.config.encroachment.proportionalCredit) return plain
        val clean = scorePoints(ruleSet, c, baseVerdict).coerceAtLeast(floor)
        return Math.round(floor + (clean - floor) * (1.0 - share)).toInt()
    }

    private fun scorePoints(ruleSet: RuleSet, c: Candidate, verdict: Verdict): Int =
        if (verdict == Verdict.SUBOPTIMAL && c.suboptimalPoints != null) c.suboptimalPoints
        else ruleSet.config.scorePoints[verdict.name] ?: 0

    /**
     * The problem list under a candidate: everything the engine raised that was NOT a room-in-a-
     * forbidden-direction finding is carried across untouched (the front door, a cut corner, an
     * extension, elongation, a prayer room beside a toilet), and the room findings are rebuilt from
     * this candidate's flags. Mirrors DefectDetector step 1 plus its de-duplication.
     */
    private fun rebuildDefects(ruleSet: RuleSet, case: Case, rooms: List<TwinRoom>): List<Defect> {
        val generic = ruleSet.defects.first { it.id == "X-GEN" }
        val rebuilt = rooms.flatMap { room ->
            room.flagged.map { zone ->
                val def = ruleSet.defects.firstOrNull { d ->
                    d.appliesTo.any { it.roomType == room.type && it.zone == zone }
                } ?: generic
                Defect(
                    id = def.id, severity = def.severity, zone = zone, roomId = room.id,
                    ruleSourceId = def.id, provenance = def.provenance, explanation = def.explanation,
                )
            }
        }
        val seen = HashSet<String>()
        return (case.carriedDefects + rebuilt).filter { seen.add("${it.id}|${it.zone}|${it.roomId}|${it.fixtureId}") }
    }

    /** §4.5 exactly, with the candidate's own values. */
    private fun scoreOf(
        ruleSet: RuleSet,
        c: Candidate,
        rooms: List<TwinRoom>,
        case: Case,
        defects: List<Defect>,
    ): Int {
        val cfg = ruleSet.config
        var weightedPoints = 0.0
        var weightSum = 0.0
        for (room in rooms) {
            if (room.notScored) continue
            weightedPoints += room.points * room.weight
            weightSum += room.weight
        }
        case.analysis.doorResult?.let { door ->
            val points = if (door.verdict == PadaVerdict.INAUSPICIOUS && c.unfavourableDoorPoints != null) {
                c.unfavourableDoorPoints
            } else {
                door.points
            }
            weightedPoints += points * door.weight
            weightSum += door.weight
        }
        val base = if (weightSum > 0.0) weightedPoints / weightSum else 0.0

        val shareOf = rooms.associate { it.id to it.share }
        val full = c.fullPenaltyShare ?: cfg.encroachment.fullPenaltyShare
        val rawPenalty = defects
            .filter { it.provenance != Provenance.DISP }
            .sumOf { d ->
                val severity = cfg.penalties[d.severity.name] ?: 0
                val share = d.roomId?.let(shareOf::get)
                val factor = when {
                    !cfg.encroachment.proportionalCredit || share == null -> 1.0
                    full <= 0.0 -> 1.0
                    else -> (share / full).coerceIn(0.0, 1.0)
                }
                Math.round(severity * factor).toInt()
            }
        val penalty = minOf(rawPenalty, cfg.penaltyCap)
        return Math.round(base - penalty).toInt().coerceIn(0, 100)
    }

    // ───────────────────────── the plans ─────────────────────────

    /**
     * One plan, scored once by the real engine, with the few geometric facts the twin needs that
     * the result does not carry: the analysis-rectangle area, one square's area, and each room's
     * centroid square (the tie-break consults it).
     */
    private class Case(
        val id: String,
        val short: String,
        val isReal: Boolean,
        val analysis: Analysis,
        val analysisRectArea: Double,
        val padaArea: Double,
        val centroidCell: Map<String, Pair<Int, Int>>,
        val roomArea: Map<String, Double>,
        val carriedDefects: List<Defect>,
    )

    private fun buildCases(): List<Case> {
        val real = listOfNotNull(
            realCase(RecordedScans.CLEAN, "p01"),
            realCase(RecordedScans.COMPRESSED, "jpg"),
            realCase(RecordedScans.PHOTO, "pho"),
            realCase(RecordedScans.OWNER_FLAT, "own"),
            realCase(RecordedScans.PLAN_020, "020"),
            realCase(RecordedScans.GREENCOURT, "g526"),
            realCase(RecordedScans.GREENCOURT_336_CLEAN, "g336c"),
            realCase(RecordedScans.GREENCOURT_336_BRANDED, "g336b"),
        )
        return real + listOf(
            case("sample-01", "s01", isReal = false, plan = sample01()),
            case("sample-02", "s02", isReal = false, plan = sample02()),
        )
    }

    private fun realCase(id: String, short: String): Case? {
        val reply = RecordedScans.load(id)?.reply ?: return null
        val placed = ScanMapper.map(reply, imageAspect = 1.0) as? ScanOutcome.Placed ?: return null
        val grid = toGridRooms(placed.rooms, placed.cols, placed.rows)
        val plan = buildEnginePlan(
            rooms = grid,
            door = frontDoorFromEntrance(grid),
            intent = Intent.BUILDING,
            propertyType = PropertyType.FLAT,
            north = 0,
            planId = id,
        ) ?: return null
        return case(id, short, isReal = true, plan = plan)
    }

    private fun case(id: String, short: String, isReal: Boolean, plan: Plan): Case {
        val analysis = VastuEngine().analyze(plan)
        val level = plan.levels.first { it.index == 0 }
        val bounds = bbox(level.outline)
        val rectArea = (bounds[2] - bounds[0]) * (bounds[3] - bounds[1])
        val centroids = level.rooms.associate { it.id to padaOf(centroid(it.polygon), bounds) }
        val areas = level.rooms.associate { it.id to area(it.polygon) }

        // Which findings are room-in-a-forbidden-direction findings, and which are everything else.
        // Established from the engine's OWN result under today's rule, so nothing is guessed: a
        // finding the engine attached to a (room, direction) pair the rule prohibits is rebuilt per
        // candidate; the rest — the door, cuts, extensions, elongation, adjacency — ride along.
        val roomZonePairs = analysis.roomResults.flatMap { rr ->
            val prohibited = rr.rule?.prohibited ?: emptySet()
            prohibited.map { rr.roomId to it }
        }.toSet()
        val carried = analysis.defects.filterNot { (it.roomId to it.zone) in roomZonePairs }

        return Case(
            id, short, isReal, analysis, rectArea, rectArea / (GRID * GRID), centroids, areas, carried,
        )
    }

    /** Plan `sample-01` — the §15 acceptance fixture. Must score exactly 31; asserted above. */
    private fun sample01(): Plan {
        val rooms = listOf(
            Room("pooja", RoomType.POOJA, rect(6.0, 74.0, 28.0, 20.0)),
            Room("bed2", RoomType.BEDROOM, rect(38.0, 70.0, 26.0, 24.0)),
            Room("toilet", RoomType.TOILET, rect(68.0, 74.0, 26.0, 20.0)),
            Room("kitchen", RoomType.KITCHEN, rect(6.0, 40.0, 28.0, 26.0)),
            Room("stairs", RoomType.STAIRCASE, rect(42.0, 41.0, 17.0, 18.0)),
            Room("living", RoomType.LIVING, rect(66.0, 38.0, 28.0, 30.0)),
            Room("master", RoomType.MASTER_BEDROOM, rect(6.0, 6.0, 34.0, 28.0)),
        )
        val door = Door("main", Point(80.0, 6.0), Point(60.0, 0.0), Point(100.0, 0.0), isMainEntrance = true)
        return Plan(
            id = "sample-01",
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(Level(0, rect(0.0, 0.0, 100.0, 100.0), rooms, listOf(door))),
            site = null,
            northOffsetDegrees = 0,
        )
    }

    /** `sample-02` — a well-placed home, kept as the control: a candidate must not flatten it. */
    private fun sample02(): Plan {
        val rooms = listOf(
            Room("living", RoomType.LIVING, rect(66.0, 66.0, 30.0, 30.0)),
            Room("kitchen", RoomType.KITCHEN, rect(66.0, 4.0, 30.0, 28.0)),
            Room("master", RoomType.MASTER_BEDROOM, rect(4.0, 4.0, 34.0, 30.0)),
            Room("bed2", RoomType.BEDROOM, rect(4.0, 66.0, 28.0, 30.0)),
            Room("store", RoomType.STORE, rect(4.0, 40.0, 24.0, 20.0)),
        )
        val door = Door("main", Point(66.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), isMainEntrance = true)
        return Plan(
            id = "sample-02",
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(Level(0, rect(0.0, 0.0, 100.0, 100.0), rooms, listOf(door))),
            site = null,
            northOffsetDegrees = 0,
        )
    }

    // ───────────────────────── small geometry, local on purpose ─────────────────────────
    // The engine's own geometry helpers are internal to that module, so the few lines the bench
    // needs are written here rather than widening the engine's surface for a measurement.

    private fun rect(x: Double, y: Double, w: Double, h: Double): List<Point> =
        listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))

    /** minX, minY, maxX, maxY. */
    private fun bbox(poly: List<Point>): DoubleArray = doubleArrayOf(
        poly.minOf { it.x }, poly.minOf { it.y }, poly.maxOf { it.x }, poly.maxOf { it.y },
    )

    /** Polygon area (shoelace, unsigned) — the same measure the engine takes of a room. */
    private fun area(poly: List<Point>): Double {
        var twice = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            twice += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(twice) / 2.0
    }

    /** Polygon AREA centroid (shoelace), falling back to the vertex mean on a degenerate ring. */
    private fun centroid(poly: List<Point>): Point {
        var twiceArea = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val cross = a.x * b.y - b.x * a.y
            twiceArea += cross
            cx += (a.x + b.x) * cross
            cy += (a.y + b.y) * cross
        }
        if (kotlin.math.abs(twiceArea) < 1e-12) {
            return Point(poly.sumOf { it.x } / poly.size, poly.sumOf { it.y } / poly.size)
        }
        return Point(cx / (3.0 * twiceArea), cy / (3.0 * twiceArea))
    }

    /** Which of the 81 squares a point falls in. Row 0 is the northern strip, as in the engine. */
    private fun padaOf(p: Point, bounds: DoubleArray): Pair<Int, Int> {
        val w = (bounds[2] - bounds[0]) / GRID
        val h = (bounds[3] - bounds[1]) / GRID
        if (w <= 0.0 || h <= 0.0) return 4 to 4
        val col = ((p.x - bounds[0]) / w).toInt().coerceIn(0, GRID - 1)
        val row = ((bounds[3] - p.y) / h).toInt().coerceIn(0, GRID - 1)
        return row to col
    }

    private fun centreSquares(c: Candidate): Int =
        (0 until GRID).sumOf { row -> (0 until GRID).count { col -> c.zoneAt(row, col) == Zone.BRAHMASTHAN } }

    /**
     * Four places, matching RoomEvaluator. The areas behind this are accumulated from square edges
     * derived from the analysis rectangle, so the last bits move with where the home sits on the
     * sheet — and position must never be a scoring term (§0.4).
     */
    private fun quantise(raw: Double): Double =
        Math.round(raw.coerceIn(0.0, 1.0) * 10_000.0) / 10_000.0

    private fun outOfTen(score: Int): String = (score / 10.0).toString()

    private fun pct(share: Double): String = "${Math.round(share * 100.0)}%"

    /** One line of the report, marked so CI can lift it out of the test-results XML into the log. */
    private fun say(line: String) = println("SCORE| $line")
}
