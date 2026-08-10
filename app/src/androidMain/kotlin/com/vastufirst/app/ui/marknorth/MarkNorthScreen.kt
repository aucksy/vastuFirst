package com.vastufirst.app.ui.marknorth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.ImageBitmap
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
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.app.ui.common.screenRoot
import kotlin.math.roundToInt

/**
 * Mark North (§6.3 · VastuCompass.dc.html) — the signature screen. Drag the dial, use the slider,
 * the N/E/S/W chips, or type the degree. The centre stays clean and there is NO "best angle"
 * affordance (§0.7).
 *
 * ⛔ TWO THINGS WERE REMOVED ON 10 AUG 2026 (owner) AND MUST NOT COME BACK.
 *
 *  · **The live score.** A number that moved as the dial turned turned this screen into a search for
 *    the best-scoring North, which is exactly the affordance §0.7 forbids. The engine still recomputes
 *    as the dial moves — that is what keeps the "check this before we score" card honest — the number
 *    is simply not shown.
 *  · **"Use my phone's compass."** ⚠ Removing it has a real cost and it is stated here rather than
 *    forgotten: it was by far the easiest route for someone who does not already know which way their
 *    home faces, and a wrong North silently moves every room in the report. Everyone now sets North by
 *    hand. The double-check card below the dial is what stands in its place, and it matters more now.
 */
@Composable
fun MarkNorthScreen(
    vm: NewPlanViewModel,
    onRead: () -> Unit,
    onBack: () -> Unit,
    /**
     * ⭐ The user's own scanned plan, when they arrived here from a scan (owner, 6 Aug 2026). It sits
     * in the dial in place of our redrawn rooms; North still turns the ring around it, exactly as it
     * always has, because the plan under the ring has never rotated.
     */
    planImage: ImageBitmap? = null,
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen renders headlessly from
    // fixture state (rooms + north + a live Analysis) in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    MarkNorthContent(
        rooms = vm.rooms,
        north = vm.north,
        analysis = analysis,
        onNorthChange = vm::updateNorth,
        onRead = onRead,
        onBack = onBack,
        cols = vm.gridCols,
        rows = vm.gridRows,
        planImage = planImage,
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
    planImage: ImageBitmap? = null,
) {
    val colors = VastuTheme.colors
    val model = buildZoneMapModel(rooms, analysis, north, cols, rows, planImage)

    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Step 2 of 3")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Which way is North?", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Drag the N dial, or use the slider. Everything else follows from this.", style = VastuTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // ⭐ The nudge runs until the reader first moves North, then never again in this session.
        var touchedNorth by rememberSaveable { mutableStateOf(false) }
        NorthDial(
            model = model,
            onNorthChange = { touchedNorth = true; onNorthChange(it) },
            hintPulse = !touchedNorth,
            contentDescription = "Floor plan compass. North at $north degrees. Drag to set North.",
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

        // ⛔ THE LIVE SCORE IS GONE (owner, 10 Aug 2026: "Dont show LIVE score") — do not put it back.
        // A number that moved as the dial turned invited the reader to hunt for the North that scored
        // best, which is precisely what Product PRD §0.7 forbids this screen from offering. The
        // bearing itself stays: it is what the reader is actually setting.
        Row(
            modifier = Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            VText("North is at", style = VastuTheme.type.body, color = colors.textTertiary)
            VText("$north°", style = VastuTheme.type.mono, color = colors.textPrimary)
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
