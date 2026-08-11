package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.vastufirst.app.ui.grid.microLabel
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.designsystem.components.VastuProvenance
import com.vastufirst.designsystem.components.VastuRoomStatus
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

/**
 * ⭐ The engine's four verdicts, collapsed to the ONE WORD a room row carries (owner, 10 Aug 2026).
 *
 * ⚠ The collapse is on the WORD ONLY. "Not ideal" and "Defect" both read `REVIEW`; both still open
 * onto their own verdict, reason, provenance and remedies. No finding is removed, softened or merged
 * — that rule outranks every presentation change (Product PRD §4.5.2b).
 *
 * ⚠ `NOT_SCORED` gets its own third word rather than being folded into either. It means the tradition
 * does not place that room — about one room in eight — and calling it "Aligned" would be an approval
 * we never gave, while "Review" would invent a problem the report does not raise.
 */
fun Verdict.roomStatus(): VastuRoomStatus = when (this) {
    Verdict.IDEAL, Verdict.ACCEPTABLE -> VastuRoomStatus.ALIGNED
    Verdict.SUBOPTIMAL, Verdict.DEFECT -> VastuRoomStatus.REVIEW
    Verdict.NOT_SCORED -> VastuRoomStatus.NOT_RATED
}

/**
 * Reading order for the unified room list: what needs looking at, then what we could not rate, then
 * what is already right. Within a band the engine's own order is kept, so the ranking that "fix
 * first" used to carry survives — the list is sorted, not re-scored.
 */
fun VastuRoomStatus.readingOrder(): Int = when (this) {
    VastuRoomStatus.REVIEW -> 0
    VastuRoomStatus.NOT_RATED -> 1
    VastuRoomStatus.ALIGNED -> 2
}

/**
 * "Bedroom 2" — the room's kind, numbered only when the home has more than one of that kind.
 *
 * ⚠ The FALLBACK, not the first choice. A scanned plan prints its own names and those win
 * ([roomNamesById]); this is what a home drawn by hand has, and what a scanned room falls back to
 * when its caption came back blank.
 */
fun roomDisplayNames(types: List<RoomType>): List<String> {
    val total = types.groupingBy { it }.eachCount()
    val seen = mutableMapOf<RoomType, Int>()
    return types.map { t ->
        val n = (seen[t] ?: 0) + 1
        seen[t] = n
        if ((total[t] ?: 0) > 1) "${t.label()} $n" else t.label()
    }
}

/**
 * ⭐⭐ WHAT TO CALL EACH ROOM ON THE REPORT — the plan's own printed caption when there is one, and
 * the numbered kind when there is not. Keyed by room id, so it survives the report's re-sorting.
 *
 * ⚠ WHY IT IS A MAP AND NOT A LIST. The report shows its rooms worst first, so its rows are not in
 * plan order — but the numbering ("Bedroom 2") has to mean *the second bedroom on the plan*, not the
 * second one that happened to score badly. Working the names out over the plan-ordered rooms and
 * then looking them up by id keeps both true at once.
 *
 * ⚠ Numbering is applied to our OWN fallback names only. A sheet that captions two rooms identically
 * gets two identical rows, because that is what the sheet says and inventing a number would be us
 * writing words onto somebody's plan. The check-what-we-read screen has always behaved this way;
 * this is the same behaviour, on the report.
 */
fun roomNamesById(rooms: List<GridRoom>): Map<String, String> {
    val fallback = roomDisplayNames(rooms.map { it.type })
    return rooms.mapIndexed { i, r -> r.id to r.label.ifBlank { fallback[i] } }.toMap()
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

/**
 * The front door's wall, spoken as a word.
 *
 * ⚠ The door marker's screen-reader label was built from the enum name, so TalkBack announced
 * "Front door on the N wall" — a bare letter, in an app whose entire vocabulary is directions and
 * whose audience skews older and less phone-literate. Everything else already spells them out
 * ([Zone.short] gives "North-East"), so the door was the one place a raw enum reached a user.
 */
fun DoorSide.spoken(): String = when (this) {
    DoorSide.N -> "north"; DoorSide.E -> "east"; DoorSide.S -> "south"; DoorSide.W -> "west"
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

/**
 * A defect card title: "<Room> — <Zone>" when it belongs to a room, else "<Zone> — structure".
 *
 * ⚠ [names] is the plan's own printed captions by room id, so a finding is headed by the same words
 * as the row it belongs to. Without it the room list said "MASTER BEDROOM 1" and the finding above
 * it said "Master", about the same room, on the same screen.
 */
fun defectTitle(
    defect: com.vastufirst.shared.Defect,
    rooms: List<com.vastufirst.shared.RoomResult>,
    names: Map<String, String> = emptyMap(),
): String {
    val label = defect.roomId?.let { id ->
        names[id]?.takeIf { it.isNotBlank() } ?: rooms.firstOrNull { it.roomId == id }?.type?.label()
    }
    return if (label != null) "$label — ${defect.zone.short()}" else "${defect.zone.short()} — structure"
}

private fun VastuVerdict.glyph(): String = when (this) {
    VastuVerdict.IDEAL, VastuVerdict.ACCEPTABLE -> "✓"
    VastuVerdict.SUBOPTIMAL -> "△"
    VastuVerdict.DEFECT -> "✕"
    VastuVerdict.NOT_ASSESSED -> ""
}

/**
 * ⭐ **Every** kind of room the app can hold — and the SINGLE list behind both places a person
 * chooses one: "Add a room" in the editor, and "Change room type" on a room already placed.
 *
 * ⚠ **These used to be two lists, and that was the bug.** The palette offered eleven kinds and the
 * change-type control offered nineteen, so the same app answered "what kinds of room exist?" two
 * different ways depending on which control you were standing in front of. Owner, plainly: *"Draw
 * room on grid does not have same options as when you replace the room… should be consistent,
 * corridor should be there too."* Both controls now read this list, so they cannot drift apart
 * again — the only way to add a kind to one is to add it to both.
 *
 * The eight that the palette used to lack — Entrance, Corridor, Utility, Bathroom, Guest bedroom,
 * Courtyard, Garage, Basement — are all kinds the plan reader can produce off a real floor plan, so
 * before this a scanned room could be deleted and never put back by hand.
 *
 * Order is a DECISION, not the enum's: the eleven commonest first (unchanged, so anyone who has
 * used the app finds them where they were), then the rest, commonest first. Spelled out rather than
 * derived from `RoomType.entries` so adding a kind to the engine fails a test until someone decides
 * where it belongs here.
 */
val ALL_ROOM_TYPES: List<RoomType> = listOf(
    RoomType.LIVING, RoomType.KITCHEN, RoomType.MASTER_BEDROOM, RoomType.BEDROOM,
    RoomType.POOJA, RoomType.TOILET, RoomType.STAIRCASE, RoomType.STUDY,
    RoomType.DINING, RoomType.STORE, RoomType.BALCONY,
    RoomType.ENTRANCE, RoomType.CORRIDOR, RoomType.UTILITY, RoomType.BATHROOM,
    RoomType.GUEST_BEDROOM, RoomType.COURTYARD, RoomType.GARAGE, RoomType.BASEMENT,
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
    /**
     * ⭐ The user's own scanned plan, shown in the dial instead of our redrawing of it. Supplied only
     * by the scan flow's North step; every other caller leaves it null and gets the rooms.
     */
    planImage: ImageBitmap? = null,
): ZoneMapModel {
    val c = VastuTheme.colors

    // Verdict colours are themselves @Composable (they read the theme), so they must be resolved HERE,
    // in composable context — not inside the remember{} below. Capturing them is enough: the `c` key
    // re-runs the memo on a theme change, and the re-run closes over these freshly-resolved values.
    val colIdeal = VastuVerdict.IDEAL.color()
    val colAcceptable = VastuVerdict.ACCEPTABLE.color()
    val colSuboptimal = VastuVerdict.SUBOPTIMAL.color()
    val colDefect = VastuVerdict.DEFECT.color()
    val colNotAssessed = VastuVerdict.NOT_ASSESSED.color()

    // The rooms + wedges are a pure function of the placed rooms, the analysis, the grid size and the
    // theme — they do NOT depend on `north`. Memoize them so dragging the North dial (which changes
    // only `north`, once per degree) reuses this list instead of re-mapping every room every degree
    // (C14). `c` is a stable staticCompositionLocalOf singleton; `gridRooms` is a value-equal list of
    // data-class rooms reassigned on every real edit — so an edit invalidates, a drag does not.
    val roomsAndWedges = remember(gridRooms, analysis, cols, rows, c) {
        val neutralStroke = c.borderStrong
        val neutralFill = c.surface
        val neutralInk = c.textTertiary
        fun colorOf(v: VastuVerdict): Color = when (v) {
            VastuVerdict.IDEAL -> colIdeal
            VastuVerdict.ACCEPTABLE -> colAcceptable
            VastuVerdict.SUBOPTIMAL -> colSuboptimal
            VastuVerdict.DEFECT -> colDefect
            VastuVerdict.NOT_ASSESSED -> colNotAssessed
        }

        val verdictById: Map<String, VastuVerdict> =
            analysis?.roomResults?.associate { it.roomId to it.verdict.toVastu() } ?: emptyMap()
        val zoneById: Map<String, Zone> =
            analysis?.roomResults?.associate { it.roomId to it.zone } ?: emptyMap()

        val rooms = gridRooms.map { r ->
            val v = verdictById[r.id]
            val stroke = v?.let { colorOf(it) } ?: neutralStroke
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
                zoneTextColor = v?.let { colorOf(it) } ?: neutralInk,
                microLabel = r.type.microLabel(),
                verdictGlyph = v?.glyph() ?: "",
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
        planImage = planImage,
    )
}
