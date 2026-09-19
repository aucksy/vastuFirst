package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * ⭐⭐ THE ONE HEADING SHAPE EVERY SECTION IN THE APP USES.
 *
 * Owner, 19 September 2026: *"The app still looks too busy with too much to read and see. Please
 * optimize it. Decrease the copy and hide text behind i symbol wherever possible. Make it all
 * elegant and intuitive please"*.
 *
 * ⚠ THE BUSY-NESS WAS A PATTERN, NOT A SCREEN. Almost every section in the product printed an
 * eyebrow and then a sentence of instruction under it — *"Worst first. Tap one for where and
 * why."*, *"Both readings, no winner, and which your score follows."*, *"Neither passed nor
 * failed — we lacked details."*, *"Press a room and slide to move it."* Each one is useful the
 * first time and wallpaper every time after, and there were eleven of them between the report and
 * the checking screen. Read down a page, they are what "too much to read" is made of.
 *
 * So the sentence does not go away — it moves behind a small **i**, one tap from where it was.
 * That is the whole of the change, and it is why nothing in the product is now unreachable:
 * [CLAUDE.md §2h] forbids a change that leaves a reader with less than the report told them
 * before, and a note one tap away is not less.
 *
 * ⚠ ONE COMPONENT, NOT A PATTERN TO COPY (CLAUDE.md §2g — sweep before you fix). Eleven screens
 * hand-rolling "eyebrow, then grey sentence" is exactly how eleven of them drifted into slightly
 * different spacing, colours and tap behaviour. There is one of these, every section calls it,
 * and a change to the shape reaches all of them at once.
 */
@Composable
fun VastuSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    /**
     * How many things are in the section, drawn in the heading itself. The owner's standing rule
     * for a result screen: a supporting list sits *"under a highlighted heading that says what it
     * is and how many are in it"*.
     */
    count: Int? = null,
    /**
     * The sentence that used to sit under this heading, now behind the **i**. Null draws no
     * **i** at all — a heading with nothing to explain must not grow a control that opens nothing.
     */
    info: String? = null,
    color: Color = VastuTheme.colors.textTertiary,
    /** Names the heading's own tap target, so a test can open exactly this note. */
    tag: String? = null,
) {
    val revealAll = LocalVastuRevealAll.current
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val showing = open || revealAll

    Column(modifier.fillMaxWidth()) {
        // ⚠ THE WHOLE ROW IS THE TARGET, not the 20 dp badge inside it. A bordered "i" is smaller
        // than the 48 dp accessibility floor however it is drawn, and a second, separate target
        // squeezed into a heading row is the two-targets-in-one-row shape the accessibility pass
        // has already flagged twice in this app. A full-width row clears the floor on its own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (info == null) VastuTheme.spacing.s6 else VastuTheme.sizes.minTouch)
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .then(
                    if (info == null) Modifier
                    else Modifier.clickableTap(
                        role = Role.Button,
                        onClickLabel = if (showing) "hide what this section means" else "explain this section",
                    ) { open = !open }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = if (count == null) label else "$label ($count)",
                modifier = Modifier.weight(1f, fill = true),
                color = color,
            )
            if (info != null) InfoBadge(active = showing, tint = color)
        }

        // ⚠ A plain `if`, matching [VastuRoomRow] — not AnimatedVisibility. Two reasons, and
        // the second is the one that bites: the screenshot harness photographs a screen the
        // instant it settles, and a fading child can be caught half drawn, so every golden
        // carrying an open note would be a picture of a transition. The first is that
        // compose.animation is not a declared dependency of this module.
        if (showing && info != null) {
            Spacer(Modifier.height(VastuTheme.spacing.s1))
            InfoNote(info)
        }
    }
}

/**
 * A long supporting list, shut by default under a heading that says what it is and how many are
 * in it — the owner's standing rule for a result screen, made into a component so every list that
 * obeys it obeys it the same way.
 *
 * ⚠ SHUT IS THE DEFAULT AND OPEN IS ONE TAP. Nothing here is hidden from the reader; it is
 * *folded*, with its own count on the outside so they can see the size of what they are opening.
 * A report that opens onto four screens of prose is a report nobody scrolls to the end of.
 */
@Composable
fun VastuFoldSection(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    /** The sentence that used to sit under the heading. Drawn inside, above the list, when open. */
    info: String? = null,
    /** Opens on first draw. Used where the list IS the answer rather than support for it. */
    startOpen: Boolean = false,
    tag: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = VastuTheme.colors
    val revealAll = LocalVastuRevealAll.current
    var open by rememberSaveable(label) { mutableStateOf(startOpen) }
    val showing = open || revealAll

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(VastuTheme.shapes.sm)
                .background(colors.surface)
                .heightIn(min = VastuTheme.sizes.minTouch)
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = if (showing) "close this list" else "open this list",
                ) { open = !open }
                .padding(horizontal = VastuTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = "$label ($count)",
                modifier = Modifier.weight(1f, fill = true),
                color = colors.textSecondary,
            )
            // ⚠ A glyph from the same set the finding rows already use, so "this opens" looks the
            // same everywhere in the report rather than twice in two shapes.
            VText(
                text = if (showing) "⌃" else "⌄",
                style = VastuTheme.type.label,
                color = colors.textSecondary,
            )
        }

        if (showing) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            if (info != null) {
                InfoNote(info)
                Spacer(Modifier.height(VastuTheme.spacing.s3))
            }
            content()
        }
    }
}

/**
 * The **i** on its own, for a place with no section heading to hang it from — a screen title, or
 * a card that carries one explanatory sentence. Same note, same behaviour, same one component.
 */
@Composable
fun VastuInfoLine(
    label: String,
    info: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle? = null,
    color: Color = VastuTheme.colors.textSecondary,
    tag: String? = null,
) {
    val revealAll = LocalVastuRevealAll.current
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val showing = open || revealAll

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VastuTheme.sizes.minTouch)
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = if (showing) "hide this explanation" else "explain this",
                ) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VText(
                text = label,
                style = style ?: VastuTheme.type.bodySm,
                color = color,
                modifier = Modifier.weight(1f, fill = true),
            )
            InfoBadge(active = showing, tint = color)
        }

        if (showing) {
            Spacer(Modifier.height(VastuTheme.spacing.s1))
            InfoNote(info)
        }
    }
}

/**
 * The **i** badge itself.
 *
 * ⚠ NO FIXED SIZE, DELIBERATELY. A 20 dp circle holding a letter is a 20 dp circle with the letter
 * hanging out of it at 200 % font scale, which is a configuration this build photographs on every
 * screen. Padding round the glyph instead means the badge grows with the reader's own text.
 *
 * ⚠ NOT A TAP TARGET OF ITS OWN — the row around it is. Drawn only, so a screen reader hears one
 * control per heading instead of two.
 */
@Composable
private fun InfoBadge(active: Boolean, tint: Color) {
    val colors = VastuTheme.colors
    Box(
        modifier = Modifier
            .clip(VastuTheme.shapes.full)
            .background(if (active) colors.surface else colors.paper)
            .border(VastuTheme.borders.regular, tint, VastuTheme.shapes.full)
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
        contentAlignment = Alignment.Center,
    ) {
        VText(text = "i", style = VastuTheme.type.caption, color = tint)
    }
}

/** The note the **i** opens: the sentence that used to be printed on the page, unchanged. */
@Composable
private fun InfoNote(text: String) {
    val colors = VastuTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.sm)
            .background(colors.surface)
            .padding(VastuTheme.spacing.s3),
    ) {
        VText(text = text, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

/**
 * ⭐ THE PHOTOGRAPHY SEAM — with this on, every **i** note on the screen is open and every folded
 * list is unfolded.
 *
 * ⚠ IT IS NOT A CONVENIENCE. Shut, a note is not in the semantics tree at all, so a test that
 * pins what the report says would find nothing and pass for the wrong reason, and no golden would
 * ever contain a single word of it — text moved behind an **i** would become text no picture in
 * this repo has ever shown. Exactly the trap `expandAll` on the report already exists to avoid.
 */
val LocalVastuRevealAll: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Draws [content] with every **i** note open and every folded list unfolded. */
@Composable
fun VastuRevealAll(enabled: Boolean = true, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalVastuRevealAll provides enabled, content = content)
