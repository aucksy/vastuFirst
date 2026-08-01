package com.vastufirst.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType

/**
 * "This is not a Corridor, it's the Living room" — the one control that lets a user overrule what the
 * app decided a room is.
 *
 * ⭐ **Why it is collapsed until asked for.** All nineteen kinds have to be reachable, and nineteen
 * chips is four or five wrapped lines — parking that permanently inside the selected-room panel would
 * push the move and size controls off a small screen for everyone, to serve a correction most rooms
 * never need. Closed it costs one button.
 *
 * This and the editor's "Add a room" palette read the SAME [ALL_ROOM_TYPES]. They used to read two
 * different lists — nineteen kinds here, eleven there — which is the inconsistency the owner
 * reported; the shared list is what stops them drifting again.
 *
 * **A wrapping [FlowRow], never a side-scrolling strip.** The kind a room is *now* is shown selected,
 * and in a scrolling strip that chip is off-screen for anything past about the sixth kind — so the
 * control would open looking as though nothing were chosen, on exactly the plans where the reading
 * was wrong. Wrapped, every kind including the current one is visible at once, at any font scale.
 *
 * Picking closes it, and picking the kind it already is closes it too, so there is always a way back
 * out without changing anything.
 *
 * The expanded flag is hoisted rather than remembered here: the after-scan list keeps one row open at
 * a time, and the editor closes it when a different room is selected. Neither is this control's call.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomTypePicker(
    current: RoomType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (RoomType) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        VastuButton(
            // Spelled as the ACTION, with the present kind inside it, so a screen reader announces
            // both what the button does and what it would be changing from.
            text = "Change room type",
            onClick = { onExpandedChange(true) },
            style = VastuButtonStyle.SECONDARY,
            large = false,
            modifier = modifier.semantics {
                this.contentDescription = "Change room type. Currently ${current.label()}."
            },
        )
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        ALL_ROOM_TYPES.forEach { type ->
            val isCurrent = type == current
            VastuChip(
                text = type.label(),
                selected = isCurrent,
                onClick = {
                    // Order matters only for readability: the pick is a no-op when it is already this
                    // kind (retypeRoom / withRoomType both return the same instance), and either way
                    // the list closes, so tapping the selected chip is the "leave it alone" exit.
                    if (!isCurrent) onPick(type)
                    onExpandedChange(false)
                },
                modifier = Modifier.semantics {
                    this.contentDescription =
                        if (isCurrent) "${type.label()}, the current room type"
                        else "Change to ${type.label()}"
                },
            )
        }
    }
}
