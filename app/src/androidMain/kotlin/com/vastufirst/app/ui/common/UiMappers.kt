package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.designsystem.components.VastuProvenance
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.components.ZoneMapModel
import com.vastufirst.designsystem.components.ZoneMapRoom
import com.vastufirst.designsystem.components.ZoneWedge
import com.vastufirst.designsystem.components.color
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone

/** Engine verdict → design-system verdict (one-to-one; DISPUTED/no-rule read as "not assessed"). */
fun Verdict.toVastu(): VastuVerdict = when (this) {
    Verdict.IDEAL -> VastuVerdict.IDEAL
    Verdict.ACCEPTABLE -> VastuVerdict.ACCEPTABLE
    Verdict.SUBOPTIMAL -> VastuVerdict.SUBOPTIMAL
    Verdict.DEFECT -> VastuVerdict.DEFECT
    Verdict.NOT_SCORED -> VastuVerdict.NOT_ASSESSED
}

fun Provenance.toVastu(): VastuProvenance = when (this) {
    Provenance.TEXT -> VastuProvenance.TEXT
    Provenance.DERIV -> VastuProvenance.DERIV
    Provenance.MOD -> VastuProvenance.MOD
    Provenance.DISP -> VastuProvenance.DISP
}

/** Plain-language zone names used throughout the report and score. */
fun Zone.short(): String = when (this) {
    Zone.N -> "North"; Zone.NE -> "North-East"; Zone.E -> "East"; Zone.SE -> "South-East"
    Zone.S -> "South"; Zone.SW -> "South-West"; Zone.W -> "West"; Zone.NW -> "North-West"
    Zone.BRAHMASTHAN -> "centre"
}

fun Zone.code(): String = when (this) {
    Zone.BRAHMASTHAN -> "C"; else -> name
}

fun RoomType.label(): String = when (this) {
    RoomType.ENTRANCE -> "Entrance"; RoomType.KITCHEN -> "Kitchen"
    RoomType.MASTER_BEDROOM -> "Master"; RoomType.BEDROOM -> "Bedroom"
    RoomType.POOJA -> "Pooja"; RoomType.TOILET -> "Toilet"; RoomType.BATHROOM -> "Bathroom"
    RoomType.LIVING -> "Living"; RoomType.DINING -> "Dining"; RoomType.STAIRCASE -> "Stairs"
    RoomType.STUDY -> "Study"; RoomType.STORE -> "Store"; RoomType.GUEST_BEDROOM -> "Guest"
    RoomType.GARAGE -> "Garage"; RoomType.BALCONY -> "Balcony"; RoomType.BASEMENT -> "Basement"
    RoomType.COURTYARD -> "Courtyard"; RoomType.UTILITY -> "Utility"; RoomType.CORRIDOR -> "Corridor"
}

/** A defect card title: "<Room> — <Zone>" when it belongs to a room, else "<Zone> — structure". */
fun defectTitle(defect: com.vastufirst.shared.Defect, rooms: List<com.vastufirst.shared.RoomResult>): String {
    val label = defect.roomId?.let { id -> rooms.firstOrNull { it.roomId == id }?.type?.label() }
    return if (label != null) "$label — ${defect.zone.short()}" else "${defect.zone.short()} — structure"
}

private fun VastuVerdict.glyph(): String = when (this) {
    VastuVerdict.IDEAL, VastuVerdict.ACCEPTABLE -> "✓"
    VastuVerdict.SUBOPTIMAL -> "△"
    VastuVerdict.DEFECT -> "✕"
    VastuVerdict.NOT_ASSESSED -> ""
}

/** The room types offered in the guided grid, in palette order. */
val GRID_ROOM_TYPES: List<RoomType> = listOf(
    RoomType.LIVING, RoomType.KITCHEN, RoomType.MASTER_BEDROOM, RoomType.BEDROOM,
    RoomType.POOJA, RoomType.TOILET, RoomType.STAIRCASE, RoomType.STUDY,
    RoomType.DINING, RoomType.STORE, RoomType.BALCONY,
)

/** A stable editor colour per room type (before scoring) — reuses the zone/verdict palette. */
@Composable
fun RoomType.editorColor(): Color = with(VastuTheme.colors) {
    when (this@editorColor) {
        RoomType.LIVING -> zoneN; RoomType.KITCHEN -> zoneSE; RoomType.MASTER_BEDROOM -> zoneSW
        RoomType.BEDROOM -> zoneW; RoomType.POOJA -> zoneNW; RoomType.TOILET -> verdictDefect
        RoomType.STAIRCASE -> zoneS; RoomType.STUDY -> zoneE; RoomType.DINING -> zoneNE
        RoomType.BALCONY -> zoneCentre; RoomType.GUEST_BEDROOM -> zoneW
        else -> textTertiary
    }
}

/**
 * Build the [ZoneMapModel] the ZoneMap/NorthDial render, from the placed grid rooms + the live
 * engine [analysis]. Rooms are coloured by their verdict when scored, neutral before. Runs in a
 * composable so it can read theme colours; the maths is trivial.
 */
@Composable
fun buildZoneMapModel(
    gridRooms: List<GridRoom>,
    analysis: Analysis?,
    north: Int,
    cols: Int = GRID,
    rows: Int = GRID,
): ZoneMapModel {
    val c = VastuTheme.colors

    // The rooms + wedges are a pure function of the placed rooms, the analysis, the grid size and the
    // theme — they do NOT depend on `north`. Memoize them so dragging the North dial (which changes
    // only `north`, once per degree) reuses this list instead of re-mapping every room every degree
    // (C14). `c` is a stable staticCompositionLocalOf singleton; `gridRooms` is a value-equal list of
    // data-class rooms reassigned on every real edit — so an edit invalidates, a drag does not.
    val roomsAndWedges = remember(gridRooms, analysis, cols, rows, c) {
        val neutralStroke = c.borderStrong
        val neutralFill = c.surface
        val neutralInk = c.textTertiary

        val verdictById: Map<String, VastuVerdict> =
            analysis?.roomResults?.associate { it.roomId to it.verdict.toVastu() } ?: emptyMap()
        val zoneById: Map<String, Zone> =
            analysis?.roomResults?.associate { it.roomId to it.zone } ?: emptyMap()

        val rooms = gridRooms.map { r ->
            val v = verdictById[r.id]
            val stroke = v?.color() ?: neutralStroke
            val fill = if (v != null) stroke.copy(alpha = 0.14f) else neutralFill
            val zone = zoneById[r.id]
            val zoneText = if (zone != null && v != null) "${zone.code()} ${v.glyph()}".trim() else ""
            ZoneMapRoom(
                x = r.col.toFloat() / cols * 100f,
                y = r.row.toFloat() / rows * 100f,
                w = r.w.toFloat() / cols * 100f,
                h = r.h.toFloat() / rows * 100f,
                label = r.type.label(),
                zoneText = zoneText,
                fill = fill,
                stroke = stroke,
                zoneTextColor = v?.color() ?: neutralInk,
            )
        }
        val wedges = listOf(
            ZoneWedge("N", c.zoneN), ZoneWedge("NE", c.zoneNE), ZoneWedge("E", c.zoneE), ZoneWedge("SE", c.zoneSE),
            ZoneWedge("S", c.zoneS), ZoneWedge("SW", c.zoneSW), ZoneWedge("W", c.zoneW), ZoneWedge("NW", c.zoneNW),
        )
        rooms to wedges
    }

    // Only `northDegrees` follows the dial — the cheap part, rebuilt per degree; the heavy part above
    // is reused.
    return ZoneMapModel(
        rooms = roomsAndWedges.first,
        wedges = roomsAndWedges.second,
        northDegrees = north.toFloat(),
        centreColor = c.zoneCentre,
    )
}
