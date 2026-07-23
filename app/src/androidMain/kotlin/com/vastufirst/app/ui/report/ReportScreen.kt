package com.vastufirst.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.TagPill
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuSegmented
import com.vastufirst.designsystem.components.VerdictPill
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Intent
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.Verdict

/**
 * Full report (§6.5/§6.6) — branches on intent (§2). BUILDING/BUYING lead with layout changes
 * ("still free to make"); ALREADY LIVING leads with remedies and demotes layout to "if you ever
 * renovate". Every rule carries its provenance tag. Disputes show both readings, no winner.
 */
@Composable
fun ReportScreen(vm: NewPlanViewModel) {
    val colors = VastuTheme.colors
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val a = analysis
    val intent = vm.intent ?: Intent.BUILDING
    val living = intent == Intent.LIVING

    if (a == null) {
        Box(Modifier.fillMaxSize().background(colors.paper).padding(VastuTheme.spacing.s6), contentAlignment = Alignment.Center) {
            LoadingState("Preparing your report…")
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Full report")
            IntentBadge(intent)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(if (living) "What to do now" else "What to change", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            if (living) "Walls can't move — so remedies lead here. Layout changes are kept, but demoted to \"if you ever renovate\"."
            else "Ranked by how much each matters. Nothing is built yet — every layout change below is still free to make.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        if (a.notes.isNotEmpty()) {
            NotesStrip(a.notes)
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        VastuSegmented(options = listOf("Traditional 8-zone", "16-zone school"), selectedIndex = 0, onSelect = {})
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("The 16-zone school is a separate reading — coming in a later update.", style = VastuTheme.type.caption, color = colors.textTertiary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        val defects = a?.defects.orEmpty()
        if (defects.isEmpty()) {
            VText("No defects to rank — the placements read well.", style = VastuTheme.type.body, color = colors.textSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                defects.forEachIndexed { i, d -> DefectCard(i + 1, d, a?.roomResults.orEmpty(), living) }
            }
        }

        // Already right.
        val good = a?.roomResults.orEmpty().filter { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
        if (good.isNotEmpty()) {
            SectionHeader("Already right — leave alone")
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
                good.forEach { r ->
                    VastuCard(accent = colors.verdictIdeal) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                            VerdictPill(r.verdict.toVastu())
                            VText("${r.type.label()} — ${r.zone.short()}", style = VastuTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            r.rule?.provenance?.let { ProvenanceTag(it.toVastu()) }
                        }
                    }
                }
            }
        }

        // Where schools disagree.
        val disputes = a?.disputes.orEmpty()
        if (disputes.isNotEmpty()) {
            SectionHeader("Where the schools disagree")
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                disputes.forEach { disp ->
                    VastuCard(accent = colors.provenanceDisp) {
                        VText(disp.title, style = VastuTheme.type.h3, color = colors.textPrimary)
                        Spacer(Modifier.height(VastuTheme.spacing.s2))
                        ReadingRow(disp.readingA.label, disp.readingA.text)
                        Spacer(Modifier.height(VastuTheme.spacing.s2))
                        ReadingRow(disp.readingB.label, disp.readingB.text)
                    }
                }
            }
        }

        // Not assessed.
        val notAssessed = a?.notAssessed.orEmpty()
        if (notAssessed.isNotEmpty()) {
            SectionHeader("Couldn't check these yet")
            VText("We didn't have the input to check these — they are neither passed nor failed. Add the details later and they'll be included.", style = VastuTheme.type.bodySm, color = colors.textTertiary)
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
                notAssessed.forEach { VText("· $it", style = VastuTheme.type.bodySm, color = colors.textTertiary) }
            }
        }

        // Disclaimer.
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        Box(Modifier.fillMaxWidth().clip(VastuTheme.shapes.md).background(colors.surface).padding(VastuTheme.spacing.s4)) {
            VText(
                "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome.",
                style = VastuTheme.type.body, color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@Composable
private fun DefectCard(rank: Int, d: Defect, rooms: List<RoomResult>, living: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = if (living) colors.secondary else colors.verdictDefect) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            VerdictPill(VastuVerdict.DEFECT)
            VText("#$rank", style = VastuTheme.type.caption, color = colors.textTertiary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(defectTitle(d, rooms), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        ProvenanceTag(d.provenance.toVastu())
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        val remedies = d.remedies.map { it.text }
        if (living) {
            RemedyBlock(remedies)
            d.layoutFix?.let { Spacer(Modifier.height(VastuTheme.spacing.s2)); RenovateBlock(it) }
        } else {
            d.layoutFix?.let { LayoutBlock(it); Spacer(Modifier.height(VastuTheme.spacing.s2)) }
            RemedyBlock(remedies)
        }
    }
}

@Composable
private fun LayoutBlock(text: String) = AdviceBlock("✦ Change the layout — free now", text, VastuTheme.colors.primary)

@Composable
private fun RenovateBlock(text: String) = AdviceBlock("If you ever renovate", text, VastuTheme.colors.textTertiary)

@Composable
private fun RemedyBlock(remedies: List<String>) {
    val colors = VastuTheme.colors
    if (remedies.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm).background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel("If it cannot move — remedies", color = colors.secondaryText)
        remedies.forEach { VText("· $it", style = VastuTheme.type.bodySm, color = colors.textSecondary) }
    }
}

@Composable
private fun AdviceBlock(heading: String, text: String, accent: androidx.compose.ui.graphics.Color) {
    val colors = VastuTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm).background(accent.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel(heading, color = accent)
        VText(text, style = VastuTheme.type.bodySm, color = colors.textPrimary)
    }
}

@Composable
private fun ReadingRow(label: String, text: String) {
    val colors = VastuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        VText(label, style = VastuTheme.type.label, color = colors.textPrimary)
        VText(text, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

@Composable
private fun IntentBadge(intent: Intent) {
    val colors = VastuTheme.colors
    val (text, color) = when (intent) {
        Intent.BUILDING -> "BUILDING" to colors.verdictIdeal
        Intent.BUYING -> "BUYING" to colors.info
        Intent.LIVING -> "ALREADY LIVING HERE" to colors.secondary
    }
    TagPill(text = text, color = color)
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(VastuTheme.spacing.s6))
    SectionLabel(text)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
}
