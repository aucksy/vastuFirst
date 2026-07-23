package com.vastufirst.engine

import com.vastufirst.rules.RuleSet
import com.vastufirst.shared.AnomalyKind
import com.vastufirst.shared.Defect
import com.vastufirst.shared.DefectDefinition
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.FixtureType
import com.vastufirst.shared.Point
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.Severity
import com.vastufirst.shared.Site
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneAnomaly

internal data class DefectOutcome(val defects: List<Defect>, val notAssessed: List<String>)

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
        doorBearingDeg: Double?,
        anomalies: AnomalyResult,
        rotatedFixtures: List<Pair<Fixture, Point>>,   // fixture + rotated position
        site: Site?,
    ): DefectOutcome {
        val defects = mutableListOf<Defect>()

        // 1. Room-derived structural defects: every DEFECT-verdict room maps to an id (or X-GEN).
        for (rr in roomResults) {
            if (rr.verdict != Verdict.DEFECT) continue
            val def = ruleSet.defects.firstOrNull { d ->
                d.appliesTo.any { it.roomType == rr.type && it.zone == rr.zone }
            } ?: genDef
            defects += toDefect(def, rr.zone, roomId = rr.roomId, sourceId = rr.rule?.sourceId ?: def.id)
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
        anomalies.extensions.forEach { a -> defects += anomalyDefect(a, "X-05") }

        // 4. Fixture defects (Tier C) — evaluated only when the relevant fixture is present.
        detectFixtureDefects(rotatedFixtures, defects)

        // 5. notAssessed: Tier C/D defects whose input is absent (§4.6).
        val notAssessed = mutableListOf<String>()
        for (def in ruleSet.defects) {
            if (def.requiresFixtureTypes.isNotEmpty()) {
                val present = rotatedFixtures.any { it.first.type in def.requiresFixtureTypes }
                if (!present) notAssessed += def.id
            }
            if (def.requiresSite && site == null) notAssessed += def.id
        }

        return DefectOutcome(defects, notAssessed.distinct().sorted())
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
        remedies = ruleSet.remediesFor(def),
    )
}
