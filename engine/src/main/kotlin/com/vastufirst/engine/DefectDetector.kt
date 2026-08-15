package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.shared.AnomalyKind
import com.vastufirst.shared.Defect
import com.vastufirst.shared.DefectDefinition
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.FixtureType
import com.vastufirst.shared.NotChecked
import com.vastufirst.shared.Point
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Site
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAnomaly

internal data class DefectOutcome(
    val defects: List<Defect>,
    val notAssessed: List<String>,
    /** The same list written for a reader — never the raw ids (they used to reach the report). */
    val notChecked: List<NotChecked> = emptyList(),
)

/**
 * Defect detection over the §8.4 table (Product PRD §4.6). A false positive damages trust more
 * than a missed defect — so encroachment already used the dual threshold, the SW-corner arc is
 * the only door defect, and **Tier C/D defects are evaluated only when their input is present**;
 * when absent they are reported "not assessed", never "clear" (a missing septic tank has not
 * passed the septic-tank rule).
 */
internal class DefectDetector(private val ruleSet: RuleSet, private val grid: PadaGrid) {

    private val genDef: DefectDefinition = ruleSet.defects.first { it.id == "X-GEN" }

    fun detect(
        roomResults: List<RoomResult>,
        roomDefectZones: Map<String, List<Zone>>,      // roomId -> every prohibited zone it violates
        roomPolys: Map<String, List<Point>>,           // roomId -> rotated polygon (for adjacency)
        doorBearingDeg: Double?,
        anomalies: AnomalyResult,
        rotatedFixtures: List<Pair<Fixture, Point>>,   // fixture + rotated position
        site: Site?,
    ): DefectOutcome {
        val defects = mutableListOf<Defect>()

        // 1. Room-derived structural defects: one defect per violated prohibited zone (§4.6 —
        //    every (RoomType, prohibited Zone) pair must resolve to a defect id, or X-GEN).
        for (rr in roomResults) {
            if (rr.verdict != Verdict.DEFECT) continue
            val zones = roomDefectZones[rr.roomId]?.ifEmpty { listOf(rr.zone) } ?: listOf(rr.zone)
            for (z in zones) {
                val def = ruleSet.defects.firstOrNull { d ->
                    d.appliesTo.any { it.roomType == rr.type && it.zone == z }
                } ?: genDef
                defects += toDefect(def, z, roomId = rr.roomId, sourceId = rr.rule?.sourceId ?: def.id)
            }
        }

        // 2. Door: X-06 fires only in the near-universally condemned SW-corner arc (§4.3.3).
        if (doorBearingDeg != null) {
            val b = ((doorBearingDeg % 360.0) + 360.0) % 360.0
            if (b >= ruleSet.config.swCornerArcStartDeg && b <= ruleSet.config.swCornerArcEndDeg) {
                ruleSet.defects.firstOrNull { it.id == "X-06" }?.let { defects += toDefect(it, Zone.SW, roomId = null) }
            }
        }

        // 3. Cut/extension anomalies → X-04 (NE cut), X-05 (SW extension), else X-GEN.
        anomalies.cuts.forEach { a -> defects += anomalyDefect(a, "X-04") }
        // ⭐ …EXCEPT where the tradition WELCOMES the part that sticks out (§6 · L-05). Before this,
        // only the South-West extension had a rule of its own and every other one fell through to
        // the catch-all as a fault — so a North-East extension, the most auspicious plot feature
        // there is, cost the reader points. X-04's own explanation says "the same tradition welcomes
        // a North-East extension" one line above where the engine was penalising it.
        //
        // ⚠ The zones live in the rule data, not here, so a reviewer can move them without a code
        // change and the ruleset stamp moves with them — which is what tells a saved home its score
        // was re-read. See RulesetConfig.benignExtensionZones.
        anomalies.extensions.forEach { a ->
            if (a.zone in ruleSet.config.benignExtensionZones) return@forEach
            defects += anomalyDefect(a, "X-05")
        }

        // 4. Fixture defects (Tier C) — evaluated only when the relevant fixture is present.
        detectFixtureDefects(rotatedFixtures, defects)

        // 5. X-10 (Tier A): pooja sharing a wall with a toilet; store/toilet beneath a staircase.
        detectAdjacencyDefects(roomResults, roomPolys, rotatedFixtures, defects)

        // 6. X-11 (Tier D): Veedhi Shoola — a road thrusting at the plot from a harmful direction.
        if (site != null) detectRoadThrust(site, defects)

        // 7. notAssessed: Tier C/D defects whose input is absent (§4.6). Never report "clear".
        //    ⚠ Collected TWICE on purpose: the ids for tests and logs, and the reader's sentence for
        //    the report. The report used to print the id itself — "X-09" — to a paying customer.
        val notAssessed = mutableListOf<String>()
        val notChecked = mutableListOf<NotChecked>()
        for (def in ruleSet.defects) {
            val missingFixture = def.requiresFixtureTypes.isNotEmpty() &&
                rotatedFixtures.none { it.first.type in def.requiresFixtureTypes }
            val missingSite = def.requiresSite && site == null
            if (!missingFixture && !missingSite) continue
            notAssessed += def.id
            notChecked += NotChecked(
                id = def.id,
                // Falls back to the title, never to the id: a dataset that forgets the label still
                // shows a sentence rather than a code. The loader also refuses such a dataset.
                label = def.notCheckedLabel ?: def.title,
                how = def.notCheckedHow,
            )
        }

        return DefectOutcome(
            dedupe(defects),
            notAssessed.distinct().sorted(),
            notChecked.distinctBy { it.id }.sortedBy { it.id },
        )
    }

    /** Collapse identical structural defects (e.g. a staircase room AND a stair fixture in the
     *  Brahmasthan both mapping to X-03) so the penalty is not double-counted. */
    private fun dedupe(defects: List<Defect>): List<Defect> {
        val seen = HashSet<String>()
        return defects.filter { seen.add("${it.id}|${it.zone}|${it.roomId}|${it.fixtureId}") }
    }

    private fun detectAdjacencyDefects(
        roomResults: List<RoomResult>,
        roomPolys: Map<String, List<Point>>,
        rotatedFixtures: List<Pair<Fixture, Point>>,
        out: MutableList<Defect>,
    ) {
        val def = ruleSet.defects.firstOrNull { it.id == "X-10" } ?: return
        val poojas = roomResults.filter { it.type == RoomType.POOJA }
        val toilets = roomResults.filter { it.type == RoomType.TOILET }
        for (p in poojas) {
            val pPoly = roomPolys[p.roomId] ?: continue
            for (t in toilets) {
                val tPoly = roomPolys[t.roomId] ?: continue
                if (Geometry.sharesWall(pPoly, tPoly)) {
                    out += toDefect(def, p.zone, roomId = p.roomId); break
                }
            }
        }
        // Store/toilet beneath a staircase: a fixture flagged as sitting under a STAIRCASE room.
        val staircaseIds = roomResults.filter { it.type == RoomType.STAIRCASE }.map { it.roomId }.toSet()
        for ((fx, _) in rotatedFixtures) {
            if (fx.underRoomId != null && fx.underRoomId in staircaseIds) {
                out += toDefect(def, Zone.BRAHMASTHAN, roomId = fx.underRoomId, fixtureId = fx.id)
            }
        }
    }

    private fun detectRoadThrust(site: Site, out: MutableList<Defect>) {
        val def = ruleSet.defects.firstOrNull { it.id == "X-11" } ?: return
        for (road in site.roads) {
            if (!road.pointsAtPlot) continue
            val zone = bearingToZone(road.bearingDegrees.toDouble())
            // Reading A (directional, §8.5 W-10): harmful from S, SW, SE or NW. Provenance stays DISP.
            if (zone in setOf(Zone.S, Zone.SW, Zone.SE, Zone.NW)) {
                out += toDefect(def, zone, roomId = null)
            }
        }
    }

    /** Map a compass bearing (clockwise from North) to its 45°-wide sector zone. */
    private fun bearingToZone(bearingDeg: Double): Zone {
        val b = ((bearingDeg % 360.0) + 360.0) % 360.0
        val sectors = arrayOf(Zone.N, Zone.NE, Zone.E, Zone.SE, Zone.S, Zone.SW, Zone.W, Zone.NW)
        return sectors[((b + 22.5) / 45.0).toInt() % 8]
    }

    private fun detectFixtureDefects(rotatedFixtures: List<Pair<Fixture, Point>>, out: MutableList<Defect>) {
        for ((fx, pos) in rotatedFixtures) {
            val zone = grid.zoneOf(pos)
            when (fx.type) {
                FixtureType.OVERHEAD_TANK -> if (zone == Zone.NE) raise("X-09", zone, fx, out)
                FixtureType.UNDERGROUND_WATER, FixtureType.BOREWELL -> if (zone == Zone.SW) raise("X-09", zone, fx, out)
                FixtureType.HEAVY_TREE -> if (zone == Zone.NE || zone == Zone.BRAHMASTHAN) raise("X-12", zone, fx, out)
                FixtureType.MIRROR, FixtureType.CEILING_BEAM -> raise("X-13", zone, fx, out)
                FixtureType.STAIRCASE_RUN, FixtureType.KITCHEN_PLATFORM ->
                    if (zone == Zone.BRAHMASTHAN) raise("X-03", zone, fx, out)
                else -> Unit
            }
        }
    }

    private fun raise(id: String, zone: Zone, fx: Fixture, out: MutableList<Defect>) {
        val def = ruleSet.defects.firstOrNull { it.id == id } ?: return
        if (out.any { it.id == id && it.fixtureId == fx.id }) return
        out += toDefect(def, zone, roomId = null, fixtureId = fx.id)
    }

    private fun anomalyDefect(a: ZoneAnomaly, preferredId: String): Defect {
        val useSpecific = when {
            a.kind == AnomalyKind.CUT && a.zone == Zone.NE -> preferredId       // X-04
            a.kind == AnomalyKind.EXTENSION && a.zone == Zone.SW -> preferredId // X-05
            else -> "X-GEN"
        }
        val def = ruleSet.defects.firstOrNull { it.id == useSpecific } ?: genDef
        // Anomaly severity (area-derived, §4.2.7) wins over the definition's nominal severity.
        return toDefect(def, a.zone, roomId = null).copy(severity = a.severity)
    }

    private fun toDefect(
        def: DefectDefinition,
        zone: Zone,
        roomId: String?,
        fixtureId: String? = null,
        sourceId: String? = null,
    ): Defect = Defect(
        id = def.id,
        severity = def.severity,
        zone = zone,
        roomId = roomId,
        fixtureId = fixtureId,
        ruleSourceId = sourceId ?: def.id,
        provenance = def.provenance,
        explanation = def.explanation,
        layoutFix = def.layoutFix,
        remedyNote = def.remedyNote,
        remedies = ruleSet.remediesFor(def),
    )
}
