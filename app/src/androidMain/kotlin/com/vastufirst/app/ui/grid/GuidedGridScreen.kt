package com.vastufirst.app.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.app.ui.common.screenRoot
import kotlin.math.min

private val PALETTE = listOf(
    RoomType.LIVING, RoomType.KITCHEN, RoomType.MASTER_BEDROOM, RoomType.BEDROOM,
    RoomType.POOJA, RoomType.TOILET, RoomType.STAIRCASE, RoomType.STUDY, RoomType.DINING,
)

/**
 * Guided grid editor (§6.2a · design system screen 3) — the primary, always-available, offline
 * path. Pick a room type, tap the grid to place it, tap a room to resize or remove it. A second
 * mode places the front door on an outer wall — the highest-weighted element the engine scores,
 * which the mock omits but the score needs. One-handed; never fails.
 */
@Composable
fun GuidedGridScreen(
    vm: NewPlanViewModel,
    onNext: () -> Unit,
) {
    val colors = VastuTheme.colors
    val gLine = colors.borderDefault
    // Captured here because a DrawScope cannot read a @Composable theme value. A bare `1f` inside
    // drawBehind is ONE PHYSICAL PIXEL — 0.33dp on a 3x phone, i.e. an invisible grid (UI-POLISH §5).
    val gridStroke = VastuTheme.borders.regular
    var activeType by remember { mutableStateOf(RoomType.LIVING) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var doorMode by remember { mutableStateOf(false) }

    val rooms = vm.rooms
    val door = vm.door

    fun roomAt(col: Int, row: Int): GridRoom? =
        rooms.firstOrNull { col >= it.col && col < it.col + it.w && row >= it.row && row < it.row + it.h }

    fun placeOrSelect(col: Int, row: Int) {
        val hit = roomAt(col, row)
        if (hit != null) { selectedId = hit.id; return }
        val w = min(2, GRID - col).coerceAtLeast(1)
        val h = min(2, GRID - row).coerceAtLeast(1)
        val id = "r${rooms.size}-${col}_$row"
        vm.updateRooms(rooms + GridRoom(id, activeType, col, row, w, h))
        selectedId = id
    }

    fun placeDoor(col: Int, row: Int) {
        // Nearest outer wall to the tapped cell decides the side; the parallel coord is the position.
        val distN = row; val distS = GRID - 1 - row; val distW = col; val distE = GRID - 1 - col
        val minDist = minOf(distN, distS, distW, distE)
        val d = when (minDist) {
            distN -> GridDoor(DoorSide.N, col)
            distS -> GridDoor(DoorSide.S, col)
            distW -> GridDoor(DoorSide.W, row)
            else -> GridDoor(DoorSide.E, row)
        }
        vm.updateDoor(d)
    }

    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        VText(if (doorMode) "Mark your front door" else "Place your rooms", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            if (doorMode) "Tap the outer wall where your main entrance is."
            else "Pick a room, then tap the grid. Tap a room to resize or remove it.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // The grid.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // MANDATORY. The children are placed with Modifier.offset, which does NOT contribute
                // to the parent's measured size — so without an explicit height this Box measures to
                // the tallest child, and to ZERO when no room has been placed yet. That is the state
                // the user lands in every time: an invisible, untappable grid (UI-POLISH §3.C).
                .aspectRatio(1f)
                .clip(VastuTheme.shapes.md)
                .background(colors.surface)
                .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
                .drawBehind {
                    val step = size.width / GRID
                    val stroke = gridStroke.toPx()
                    for (i in 1 until GRID) {
                        drawLine(gLine, Offset(step * i, 0f), Offset(step * i, size.height), strokeWidth = stroke)
                        drawLine(gLine, Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = stroke)
                    }
                }
                .pointerInput(doorMode, rooms, activeType) {
                    detectTapGestures { pos ->
                        val step = size.width / GRID
                        val col = (pos.x / step).toInt().coerceIn(0, GRID - 1)
                        val row = (pos.y / step).toInt().coerceIn(0, GRID - 1)
                        if (doorMode) placeDoor(col, row) else placeOrSelect(col, row)
                    }
                },
        ) {
            val dim = maxWidth
            val cell = dim / GRID
            rooms.forEach { room ->
                val c = room.type.editorColor()
                val selected = room.id == selectedId
                Box(
                    modifier = Modifier
                        .offset(x = cell * room.col, y = cell * room.row)
                        .size(width = cell * room.w, height = cell * room.h)
                        .padding(VastuTheme.spacing.s1)
                        .clip(VastuTheme.shapes.sm)
                        .background(c.copy(alpha = 0.18f))
                        .border(if (selected) VastuTheme.borders.strong else VastuTheme.borders.regular, c, VastuTheme.shapes.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    VText(room.type.label(), style = VastuTheme.type.bodySm, color = colors.textPrimary)
                }
            }
            door?.let { DoorMarker(it, cell) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // Context toolbar.
        val sel = rooms.firstOrNull { it.id == selectedId }
        when {
            sel != null -> SelectedRoomTools(
                room = sel,
                onResize = { nw, nh -> vm.updateRooms(rooms.map { if (it.id == sel.id) it.copy(w = nw, h = nh) else it }) },
                onDelete = { vm.updateRooms(rooms.filterNot { it.id == sel.id }); selectedId = null },
                onDone = { selectedId = null },
            )
            doorMode -> VastuButton("Done placing door", onClick = { doorMode = false }, large = false)
            else -> Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                SectionLabel("Rooms")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
                    PALETTE.forEach { t ->
                        VastuChip(text = t.label(), selected = t == activeType, onClick = { activeType = t })
                    }
                }
                VastuButton(
                    text = if (door == null) "Set the front door" else "Move the front door",
                    onClick = { doorMode = true; selectedId = null },
                    style = com.vastufirst.designsystem.components.VastuButtonStyle.SECONDARY,
                    large = false,
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton("Next — mark North", onClick = onNext, enabled = rooms.isNotEmpty())
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.DoorMarker(door: GridDoor, cell: androidx.compose.ui.unit.Dp) {
    val colors = VastuTheme.colors
    val (col, row) = when (door.side) {
        DoorSide.N -> door.cell to 0
        DoorSide.S -> door.cell to (GRID - 1)
        DoorSide.W -> 0 to door.cell
        DoorSide.E -> (GRID - 1) to door.cell
    }
    Box(
        modifier = Modifier
            .offset(x = cell * col, y = cell * row)
            .size(cell)
            .padding(VastuTheme.spacing.s1),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(VastuTheme.sizes.iconSm).clip(CircleShape).background(colors.secondary),
            contentAlignment = Alignment.Center,
        ) {
            VText("D", style = VastuTheme.type.caption, color = colors.onPrimary)
        }
    }
}

@Composable
private fun SelectedRoomTools(
    room: GridRoom,
    onResize: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(colors.surface)
            .border(VastuTheme.borders.regular, colors.borderDefault, VastuTheme.shapes.md)
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        VText(room.type.label(), style = VastuTheme.type.h3, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            SizeChip("W −") { onResize((room.w - 1).coerceAtLeast(1), room.h) }
            SizeChip("W +") { onResize(min(room.w + 1, GRID - room.col), room.h) }
            SizeChip("H −") { onResize(room.w, (room.h - 1).coerceAtLeast(1)) }
            SizeChip("H +") { onResize(room.w, min(room.h + 1, GRID - room.row)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButton("Remove", onClick = onDelete, style = com.vastufirst.designsystem.components.VastuButtonStyle.SECONDARY, large = false, modifier = Modifier.weight(1f))
            VastuButton("Done", onClick = onDone, large = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SizeChip(text: String, onClick: () -> Unit) {
    val colors = VastuTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = VastuTheme.sizes.minTouch)
            .clip(VastuTheme.shapes.full)
            .background(colors.primary.copy(alpha = 0.14f))
            .clickableTap(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = VastuTheme.spacing.s4, vertical = VastuTheme.spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        VText(text, style = VastuTheme.type.bodySm, color = colors.primary)
    }
}
