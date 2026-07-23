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
    val remedies: List<Remedy> = emptyList(),
)

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
    val shapeIrregular: Boolean,                 // cut/extension skipped (§4.2.7)
    val notAssessed: List<String>,               // rules skipped for missing input
    val disputes: List<Dispute>,                 // relevant to THIS plan only
    val ruleSetVersion: String,                  // e.g. "2026.07.19-1" — mandatory (§5)
)
