package com.vastufirst.app.ui.marknorth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.shared.Analysis
import com.vastufirst.designsystem.components.DegreeStepper
import com.vastufirst.designsystem.components.NorthDial
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.components.spokenScore
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot
import kotlin.math.roundToInt

/**
 * Mark North (§6.3 · VastuCompass.dc.html) — the signature screen. Drag the dial, use the slider,
 * the N/E/S/W chips, or type the degree; the score updates live (debounced ≤50 ms, off the main
 * thread — in the ViewModel). The centre stays clean and there is NO "best angle" affordance (§0.7).
 */
@Composable
fun MarkNorthScreen(
    vm: NewPlanViewModel,
    onRead: () -> Unit,
    onBack: () -> Unit,
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly from
    // fixture state (rooms + north + a live Analysis) in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    // The sensor runs ONLY while the helper is open, and is torn down the moment it closes or the
    // screen goes away. rememberSaveable so rotating the phone mid-reading doesn't shut it.
    var compassOpen by rememberSaveable { mutableStateOf(false) }
    val compass = rememberCompassState(live = compassOpen)
    MarkNorthContent(
        rooms = vm.rooms,
        north = vm.north,
        analysis = analysis,
        onNorthChange = vm::updateNorth,
        onRead = onRead,
        onBack = onBack,
        cols = vm.gridCols,
        rows = vm.gridRows,
        compass = compass,
        onCompassOpen = { compassOpen = true },
        onCompassClose = { compassOpen = false },
        onUseCompass = { heading ->
            vm.updateNorth(northOffsetForHeading(heading))
            compassOpen = false
        },
    )
}

/** Mark North as a pure function of its state — no ViewModel — so the render harness can draw it. */
@Composable
fun MarkNorthContent(
    rooms: List<GridRoom>,
    north: Int,
    analysis: Analysis?,
    onNorthChange: (Int) -> Unit,
    onRead: () -> Unit,
    onBack: () -> Unit,
    cols: Int = GRID,
    rows: Int = GRID,
    /** Passed in as plain data so this screen stays a pure function the render harness can draw. */
    compass: CompassState = CompassState(),
    onCompassOpen: () -> Unit = {},
    onCompassClose: () -> Unit = {},
    onUseCompass: (Float) -> Unit = {},
) {
    val colors = VastuTheme.colors
    val model = buildZoneMapModel(rooms, analysis, north, cols, rows)
    val score = analysis?.score
    val mark = LocalDecimalMark.current

    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Step 2 of 3")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Which way is North?", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Drag the N dial, or use the slider. Everything else follows from this.", style = VastuTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⭐ The phone's own compass. Offered FIRST, before the dial, because it is by far the easiest
        // way for someone who does not already know which way their home faces — and because a wrong
        // North silently moves every room in the report (docs/SCORE-ACCURACY-CAVEATS.md #3). Nothing
        // at all is shown on a phone without a compass: an entry point that cannot work is worse than
        // none.
        if (compass.supported) {
            CompassHelper(
                compass = compass,
                onOpen = onCompassOpen,
                onClose = onCompassClose,
                onUse = onUseCompass,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        NorthDial(
            model = model,
            onNorthChange = onNorthChange,
            contentDescription = "Floor plan compass. North at $north degrees. ${spokenScore(score ?: 0, mark)}. Drag to set North.",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Legend()
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        com.vastufirst.designsystem.components.VastuSlider(
            value = north.toFloat(),
            onValueChange = { onNorthChange(it.roundToInt()) },
            valueRange = 0f..359f,
            contentDescription = "North bearing, $north degrees",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // Live readout.
        Column(
            modifier = Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VText("North is at", style = VastuTheme.type.body, color = colors.textTertiary)
                VText("$north°", style = VastuTheme.type.mono, color = colors.textPrimary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VText("Live score", style = VastuTheme.type.body, color = colors.textTertiary)
                VText(
                    if (score != null) "${scoreOutOfTen(score, mark)}/10" else "…",
                    style = VastuTheme.type.mono,
                    color = if (score != null) scoreBandColor(score) else colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        // Quick chips.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
                Box(Modifier.weight(1f)) {
                    VastuChip(text = label, selected = north == deg, onClick = { onNorthChange(deg) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        DegreeStepper(degrees = north, onChange = onNorthChange)

        Spacer(Modifier.height(VastuTheme.spacing.s6))

        // ⭐ THE DOUBLE-CHECK. "Are you sure your North is right?" is a question nobody can answer.
        // Where your own kitchen is, is. So the card states what this North MEANS, in the report's own
        // words, and the button that continues is the one that agrees with it — no extra tap, and the
        // user cannot walk past it without having read the claim.
        val check = NorthCheck.sentence(analysis)
        if (check.isNotEmpty()) {
            VastuCard(accent = colors.primary) {
                VText("Check this before we score", style = VastuTheme.type.h3, color = colors.textPrimary)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VText(
                    "With North where you have put it, $check.",
                    style = VastuTheme.type.body, color = colors.textPrimary,
                )
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VText(
                    "Not right? Turn the dial until it is. " +
                        "Every direction in your report depends on this.",
                    style = VastuTheme.type.bodySm, color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButtonInline("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY)
            VastuButton(
                if (check.isNotEmpty()) "Yes — read my home" else "Read my home",
                onClick = onRead,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

/**
 * The phone's compass, as a helper rather than a replacement for the dial.
 *
 * ⚠ The instruction is the whole design. "Point the top of your phone the same way as the top of
 * your plan" is the one sentence that makes the reading mean anything, and [northOffsetForHeading]
 * is written against exactly that sentence. Changing the wording without changing the maths would
 * put confident, precise, WRONG directions on every room in the report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompassHelper(
    compass: CompassState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onUse: (Float) -> Unit,
) {
    val colors = VastuTheme.colors
    if (!compass.live) {
        VastuButton(
            "Use my phone's compass",
            onClick = onOpen,
            style = VastuButtonStyle.SECONDARY,
            large = false,
        )
        return
    }

    val heading = compass.headingDeg
    VastuCard(accent = colors.primary) {
        VText("Point your phone", style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Hold the phone flat, screen up. Stand in your home and turn until the TOP of the phone " +
                "points the same way as the TOP of your plan — the wall you drew along the top edge.",
            style = VastuTheme.type.body, color = colors.textPrimary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        when {
            heading == null -> VText(
                "Finding the compass…", style = VastuTheme.type.body, color = colors.textTertiary,
            )
            // ⚠ FlowRow, not a SpaceBetween Row. Two unweighted children in a SpaceBetween row are
            // laid out at their own widths, so at 200 % font the label took the whole line and the
            // READING — the only thing on this card that matters — measured ZERO WIDE. Caught by the
            // geometry gate before it shipped, and it is the same trap already fixed on the score and
            // report screens. FlowRow wraps the reading onto its own line instead.
            else -> FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
            ) {
                VText("Pointing", style = VastuTheme.type.body, color = colors.textTertiary)
                VText(
                    "${compassWord(heading)} · ${heading.roundToInt()}°",
                    style = VastuTheme.type.mono, color = colors.textPrimary,
                )
            }
        }

        if (compass.quality != CompassQuality.GOOD) {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                if (compass.quality == CompassQuality.UNRELIABLE)
                    "Your phone can't read the compass here — move away from anything metal or magnetic, " +
                        "or set North by hand with the dial below."
                else
                    "The compass needs settling. Wave the phone slowly in a figure of eight, then read it again.",
                style = VastuTheme.type.bodySm, color = colors.verdictSuboptimal,
            )
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton(
            "Set North from this",
            onClick = { heading?.let(onUse) },
            // ⭐ GOOD only (audit C13). While the warning above says the compass needs settling,
            // this button used to stay enabled — offering to rotate the entire score around a
            // reading the app had just said it distrusts. The words directly above the button say
            // what unlocks it (settle the compass, or use the dial by hand), so the disabled state
            // is never a silent grey mystery.
            enabled = heading != null && compass.quality == CompassQuality.GOOD,
            large = false,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VastuButton("Close the compass", onClick = onClose, style = VastuButtonStyle.SECONDARY, large = false)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // Said plainly rather than buried: this is magnetic north, and in India that is within about a
        // degree of true north — far inside the error of a hand holding a phone. Correcting it would
        // need the device's location, i.e. a permission, for a difference nobody could measure.
        VText(
            "This is your phone's magnetic compass. In India that is within about a degree of true North.",
            style = VastuTheme.type.caption, color = colors.textTertiary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = VastuTheme.colors
    // FlowRow, not Row (B7): at font scale 2.0 the four keys overrun the width and the last one
    // ("Defect") snapped mid-word to a second line ("Defec/t"). FlowRow wraps whole items instead.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        LegendItem("Ideal", c.verdictIdeal)
        LegendItem("Fine", c.verdictAcceptable)
        LegendItem("Not ideal", c.verdictSuboptimal)
        LegendItem("Defect", c.verdictDefect)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        Box(Modifier.size(VastuTheme.sizes.dot).clip(VastuTheme.shapes.sm).background(color))
        VText(label, style = VastuTheme.type.bodySm, color = VastuTheme.colors.textSecondary)
    }
}
