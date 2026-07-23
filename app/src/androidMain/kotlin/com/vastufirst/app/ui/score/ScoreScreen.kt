package com.vastufirst.app.ui.score

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.ScoreDisplay
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VerdictPill
import com.vastufirst.designsystem.components.ZoneMap
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Verdict

/**
 * Score — free tier (§6.4). The big band-coloured number, the zone map, the top 3 problems, and
 * an HONEST count of the rest (no hidden wall). A tappable note explains the number is the app's
 * own construction, not traditional (§4.5.3).
 */
@Composable
fun ScoreScreen(
    vm: NewPlanViewModel,
    onUnlock: () -> Unit,
) {
    val colors = VastuTheme.colors
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val a = analysis
    val model = buildZoneMapModel(vm.rooms, a, vm.north)

    Column(
        modifier = Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Your result · free")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        ScoreDisplay(score = a?.score ?: 0)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(verdictLine(a?.score ?: 0), style = VastuTheme.type.body, color = colors.textSecondary)

        Divider()
        SectionLabel("Zone map")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ZoneMap(model = model, modifier = Modifier.fillMaxWidth(0.62f), showLabels = false, contentDescription = "Your plan with Vastu zones, North at ${vm.north} degrees, score ${a?.score ?: 0} of 100.")
        }

        Divider()
        SectionLabel("Biggest problems")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        val defects = a?.defects.orEmpty()
        val top = defects.take(3)
        if (top.isEmpty()) {
            VText("No major defects found — a strong start.", style = VastuTheme.type.body, color = colors.textSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                top.forEach { d ->
                    VastuCard(accent = colors.verdictDefect) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
                            VerdictPill(VastuVerdict.DEFECT)
                            ProvenanceTag(d.provenance.toVastu())
                        }
                        Spacer(Modifier.height(VastuTheme.spacing.s2))
                        VText(defectTitle(d, a?.roomResults.orEmpty()), style = VastuTheme.type.h3, color = colors.textPrimary)
                        Spacer(Modifier.height(VastuTheme.spacing.s1))
                        VText(d.explanation, style = VastuTheme.type.bodySm, color = colors.textTertiary)
                    }
                }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        val remaining = remainingIssueCount(a)
        UnlockCard(remaining = remaining, onUnlock = onUnlock)

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            "The 0–100 score is VastuFirst's own way of summarising the report — it is not part of the tradition.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

private fun verdictLine(score: Int): String = when {
    score >= 75 -> "Strong — a few refinements left."
    score >= 50 -> "Workable, with real problems to fix while it is still on paper."
    else -> "Several core placements work against you — worth fixing now, on paper."
}

private fun remainingIssueCount(a: com.vastufirst.shared.Analysis?): Int {
    if (a == null) return 0
    val moreDefects = (a.defects.size - 3).coerceAtLeast(0)
    val suboptimal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    return moreDefects + suboptimal
}

@Composable
private fun UnlockCard(remaining: Int, onUnlock: () -> Unit) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.secondary.copy(alpha = 0.12f))
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                VText("Unlock the full report & remedies", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
                VText(
                    if (remaining > 0) "$remaining more issues · every fix & remedy, ranked" else "Every fix & remedy, ranked",
                    style = VastuTheme.type.bodySm, color = colors.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                VText("₹699", style = VastuTheme.type.h2, color = colors.textPrimary)
                VText("ONE-TIME", style = VastuTheme.type.caption, color = colors.textTertiary)
            }
        }
        VastuButton("Unlock full report & remedies · ₹699", onClick = onUnlock)
        VText("One-time · price shown before you pay · no hidden wall", style = VastuTheme.type.caption, color = colors.textTertiary)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.padding(vertical = VastuTheme.spacing.s4).fillMaxWidth().height(VastuTheme.borders.regular).background(VastuTheme.colors.borderDefault),
    )
}
