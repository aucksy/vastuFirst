package com.vastufirst.app.ui.marknorth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.DegreeStepper
import com.vastufirst.designsystem.components.NorthDial
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.theme.VastuTheme
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
    val colors = VastuTheme.colors
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val model = buildZoneMapModel(vm.rooms, analysis, vm.north)
    val score = analysis?.score

    Column(
        modifier = Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Step 2 of 3")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Which way is North?", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("Drag the N dial, or use the slider. Everything else follows from this.", style = VastuTheme.type.body, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        NorthDial(
            model = model,
            onNorthChange = { vm.updateNorth(it) },
            contentDescription = "Floor plan compass. North at ${vm.north} degrees. Score ${score ?: 0} of 100. Drag to set North.",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Legend()
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        com.vastufirst.designsystem.components.VastuSlider(
            value = vm.north.toFloat(),
            onValueChange = { vm.updateNorth(it.roundToInt()) },
            valueRange = 0f..359f,
            contentDescription = "North bearing, ${vm.north} degrees",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        // Live readout.
        Column(
            modifier = Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VText("North is at", style = VastuTheme.type.body, color = colors.textTertiary)
                VText("${vm.north}°", style = VastuTheme.type.mono, color = colors.textPrimary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VText("Live score", style = VastuTheme.type.body, color = colors.textTertiary)
                VText(if (score != null) "$score/100" else "…", style = VastuTheme.type.mono, color = if (score != null) scoreBandColor(score) else colors.textTertiary)
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        // Quick chips.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
                Box(Modifier.weight(1f)) {
                    VastuChip(text = label, selected = vm.north == deg, onClick = { vm.updateNorth(deg) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        DegreeStepper(degrees = vm.north, onChange = { vm.updateNorth(it) })

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            VastuButtonInline("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY)
            VastuButton("Read my home", onClick = onRead, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@Composable
private fun Legend() {
    val c = VastuTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
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
