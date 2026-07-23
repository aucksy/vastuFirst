package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
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
): ZoneMapModel {
    val neutralStroke = VastuTheme.colors.borderStrong
    val neutralFill = VastuTheme.colors.surface
    val neutralInk = VastuTheme.colors.textTertiary

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
            x = r.col.toFloat() / GRID * 100f,
            y = r.row.toFloat() / GRID * 100f,
            w = r.w.toFloat() / GRID * 100f,
            h = r.h.toFloat() / GRID * 100f,
            label = r.type.label(),
            zoneText = zoneText,
            fill = fill,
            stroke = stroke,
            zoneTextColor = v?.color() ?: neutralInk,
        )
    }

    val c = VastuTheme.colors
    val wedges = listOf(
        ZoneWedge("N", c.zoneN), ZoneWedge("NE", c.zoneNE), ZoneWedge("E", c.zoneE), ZoneWedge("SE", c.zoneSE),
        ZoneWedge("S", c.zoneS), ZoneWedge("SW", c.zoneSW), ZoneWedge("W", c.zoneW), ZoneWedge("NW", c.zoneNW),
    )
    return ZoneMapModel(rooms = rooms, wedges = wedges, northDegrees = north.toFloat(), centreColor = c.zoneCentre)
}
