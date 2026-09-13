package com.vastufirst.enginejs

import com.vastufirst.shared.Analysis
import kotlinx.serialization.Serializable

/**
 * What the browser gets back from a scoring run.
 *
 * The engine's own result types are deliberately NOT serialisable — one of them keys a map on a
 * pair of ints, which has no JSON form — so this is a flat, honest projection of the parts the
 * admin panel shows. It adds nothing the engine did not produce: every field here is copied
 * straight off [Analysis].
 */
@Serializable
data class AnalysisDto(
    val planId: String,
    val score: Int,
    val base: Double,
    val defectPenalty: Int,
    val quality: String,
    val ruleSetVersion: String,
    val shapeIrregular: Boolean,
    val footprintTiltDegrees: Double,
    val aspectRatio: Double,
    val rooms: List<RoomDto>,
    val door: DoorDto? = null,
    val defects: List<DefectDto> = emptyList(),
    val cuts: List<AnomalyDto> = emptyList(),
    val extensions: List<AnomalyDto> = emptyList(),
    val disputes: List<DisputeDto> = emptyList(),
    val notAssessed: List<String> = emptyList(),
    val notChecked: List<NotCheckedDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
)

@Serializable
data class RoomDto(
    val roomId: String,
    val type: String,
    val zone: String,
    val verdict: String,
    val points: Int,
    val weight: Double,
    val encroachedShare: Double,
)

@Serializable
data class DoorDto(
    val doorId: String,
    val padaId: String,
    val padaName: String? = null,
    val padaDomain: String? = null,
    val bearing: Double,
    val verdict: String,
    val points: Int,
    val weight: Double,
    val spansTwoPadas: Boolean,
)

@Serializable
data class DefectDto(
    val id: String,
    val severity: String,
    val zone: String,
    val roomId: String? = null,
    val provenance: String,
    val explanation: String,
    val belongsToBuilding: Boolean,
    /** True when this finding costs no points — a disputed reading is shown, never charged. */
    val scored: Boolean,
)

@Serializable
data class AnomalyDto(val kind: String, val zone: String, val areaShare: Double, val severity: String)

@Serializable
data class DisputeDto(val id: String, val title: String, val howWeScore: String? = null)

@Serializable
data class NotCheckedDto(val id: String, val label: String, val how: String? = null)

@Serializable
data class NoteDto(val code: String, val message: String, val level: String)

/** Flatten an [Analysis] into the shape above. Nothing is computed here; everything is copied. */
internal fun Analysis.toDto(): AnalysisDto = AnalysisDto(
    planId = planId,
    score = score,
    base = base,
    defectPenalty = defectPenalty,
    quality = quality.name,
    ruleSetVersion = ruleSetVersion,
    shapeIrregular = shapeIrregular,
    footprintTiltDegrees = footprintTiltDegrees,
    aspectRatio = aspectRatio,
    rooms = roomResults.map {
        RoomDto(
            roomId = it.roomId,
            type = it.type.name,
            zone = it.zone.name,
            verdict = it.verdict.name,
            points = it.points,
            weight = it.weight,
            encroachedShare = it.encroachedShare,
        )
    },
    door = doorResult?.let {
        DoorDto(
            doorId = it.doorId,
            padaId = it.pada.id,
            padaName = it.pada.name,
            padaDomain = it.pada.domain,
            bearing = it.bearing,
            verdict = it.verdict.name,
            points = it.points,
            weight = it.weight,
            spansTwoPadas = it.spansTwoPadas,
        )
    },
    defects = defects.map {
        DefectDto(
            id = it.id,
            severity = it.severity.name,
            zone = it.zone.name,
            roomId = it.roomId,
            provenance = it.provenance.name,
            explanation = it.explanation,
            belongsToBuilding = it.belongsToBuilding,
            // The scorer skips a DISP finding entirely — it is reported and never charged.
            scored = it.provenance != com.vastufirst.shared.Provenance.DISP,
        )
    },
    cuts = cuts.map { AnomalyDto(it.kind.name, it.zone.name, it.areaShare, it.severity.name) },
    extensions = extensions.map { AnomalyDto(it.kind.name, it.zone.name, it.areaShare, it.severity.name) },
    disputes = disputes.map { DisputeDto(it.id, it.title, it.howWeScore) },
    notAssessed = notAssessed,
    notChecked = notChecked.map { NotCheckedDto(it.id, it.label, it.how) },
    notes = notes.map { NoteDto(it.code, it.message, it.level.name) },
)
