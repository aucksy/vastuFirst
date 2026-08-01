package com.vastufirst.shared

/**
 * Engine result types (Product PRD §5). The [Analysis] is the whole output of Phase 1.
 *
 * Note there is deliberately no `facingDirection` field anywhere below — §0.4, §15.
 * A test asserts this by reflection so it cannot regress.
 */

data class Defect(
    val id: String,                             // "X-01" or "X-GEN"
    val severity: Severity,
    val zone: Zone,
    val roomId: String? = null,
    val fixtureId: String? = null,
    val ruleSourceId: String,
    val provenance: Provenance,
    val explanation: String,
    val layoutFix: String? = null,             // null when nothing can move
    /** Said out loud when the texts record no cure that leaves the element where it is. */
    val remedyNote: String? = null,
    val remedies: List<Remedy> = emptyList(),
)

/**
 * A rule we could not run, in the reader's words rather than as a code.
 *
 * ⚠ The report used to print the raw rule id — "X-09" — to a paying customer. [label] is the plain
 * sentence and [how] is what they can do about it; the [id] is kept only so tests and logs can still
 * name the rule.
 */
data class NotChecked(val id: String, val label: String, val how: String? = null)

data class RoomResult(
    val roomId: String,
    val type: RoomType,
    val zone: Zone,
    val verdict: Verdict,
    val points: Int,
    val weight: Double,
    val rule: RoomRule?,
    val padaOverlap: Map<Pair<Int, Int>, Double> = emptyMap(),
)

data class DoorResult(
    val doorId: String,
    val pada: DoorPada,
    val bearing: Double,
    val verdict: PadaVerdict,
    val points: Int,
    val weight: Double,
    val spansTwoPadas: Boolean,
)

data class ZoneAnomaly(
    val kind: AnomalyKind,
    val zone: Zone,
    val areaShare: Double,                       // fraction of the reference rectangle area
    val severity: Severity,
)

data class Analysis(
    val planId: String,
    val intent: Intent,                          // report branches on this (§2)
    val propertyType: PropertyType,
    val northOffsetDegrees: Int,                 // echo of input geometry, not a scoring term
    val schoolProfile: SchoolProfile,
    val score: Int,
    val base: Double,                            // pre-penalty base (§4.5), for transparency
    val defectPenalty: Int,
    val roomResults: List<RoomResult>,
    val doorResult: DoorResult?,                 // null when no main door flagged
    val defects: List<Defect>,                   // sorted severity, then weight
    val cuts: List<ZoneAnomaly>,
    val extensions: List<ZoneAnomaly>,
    val shapeIrregular: Boolean,                 // footprint is not rectangle-family (rare)
    val notAssessed: List<String>,               // rules skipped for missing input (ids — for tests/logs)
    val disputes: List<Dispute>,                 // relevant to THIS plan only
    val ruleSetVersion: String,                  // e.g. "2026.07.19-1" — mandatory (§5)
    // --- production robustness / transparency ---
    val quality: AnalysisQuality = AnalysisQuality.OK,
    val notes: List<AnalysisNote> = emptyList(),
    /** The same rules as [notAssessed], written for a reader instead of for a log. */
    val notChecked: List<NotChecked> = emptyList(),
    /**
     * What each direction is, in the tradition's own terms — Sanskrit name, presiding deity, element
     * and the domain it governs. Carried on the result so the report can explain a placement without
     * needing the rule dataset; it is reference material, never a scoring term.
     */
    val zoneInfo: List<ZoneInfo> = emptyList(),
    val footprintTiltDegrees: Double = 0.0,      // building's own tilt vs true north (0..90)
    val aspectRatio: Double = 1.0,               // long side / short side of the footprint
)
