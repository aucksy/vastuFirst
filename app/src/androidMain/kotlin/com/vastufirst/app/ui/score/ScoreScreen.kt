package com.vastufirst.app.ui.score

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.ScoreDisplay
import com.vastufirst.designsystem.components.spokenScore
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VerdictPill
import com.vastufirst.designsystem.components.ZoneMap
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Verdict
import com.vastufirst.app.ui.common.screenRoot

/**
 * Score — free tier (§6.4). The big band-coloured number, the zone map, the top 3 problems, and
 * an HONEST count of the rest (no hidden wall). A tappable note explains the number is the app's
 * own construction, not traditional (§4.5.3).
 *
 * Never a scary dead-end ([[vastufirst-no-error-states]]): while the engine computes we show a
 * calm loading line (never a bare red 0), and if a plan is too sparse to read we guide the user
 * back to add rooms instead of showing "0.0 / 10".
 */
@Composable
fun ScoreScreen(
    vm: NewPlanViewModel,
    onUnlock: () -> Unit,
    onFix: () -> Unit,
    onDone: () -> Unit,
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the screen (incl. its loading and
    // "insufficient plan" states) renders headlessly from fixture state in the harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    ScoreContent(
        rooms = vm.rooms,
        north = vm.north,
        intent = vm.intent,
        analysis = analysis,
        onUnlock = onUnlock,
        onFix = onFix,
        onDone = onDone,
        unlocked = vm.unlocked,
        cols = vm.gridCols,
        rows = vm.gridRows,
    )
}

/** Score as a pure function of its state — no ViewModel — so the render harness can draw every
 *  state: loading (analysis null), the "let's finish your plan" guidance (INSUFFICIENT), and the
 *  full result. */
@Composable
fun ScoreContent(
    rooms: List<GridRoom>,
    north: Int,
    intent: Intent?,
    analysis: Analysis?,
    onUnlock: () -> Unit,
    onFix: () -> Unit,
    onDone: () -> Unit = {},
    unlocked: Boolean = false,
    cols: Int = GRID,
    rows: Int = GRID,
) {
    val colors = VastuTheme.colors
    val a = analysis

    when {
        // Draft present but the engine is still computing (normal, ~50 ms): a calm loading line.
        a == null && rooms.isNotEmpty() -> Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            LoadingState("Reading your home…")
        }

        // No draft AND nothing computed — the OS reclaimed the in-progress plan (process death on a
        // low-memory phone). Never a forever spinner: guide the user back to their saved plans so they
        // can reopen, instead of trapping them on "Reading your home…" (E2E-ASSESSMENT §A4).
        a == null -> Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            GuidanceState(
                title = "Let's pick up where you left off",
                body = "We couldn't find this plan on screen — it may have closed in the background. Head back to your saved plans to reopen it.",
                action = { VastuButton("Go to my plans", onClick = onDone) },
            )
        }

        a.quality == AnalysisQuality.INSUFFICIENT -> Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            GuidanceState(
                title = "Let's finish your plan",
                body = a.notes.firstOrNull()?.message
                    ?: "Add a few rooms and your front door, and we'll read your home.",
                action = { VastuButton("Add my rooms", onClick = onFix) },
            )
        }

        else -> ScoreResult(rooms, north, intent, a, onUnlock, onDone, unlocked, cols, rows)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreResult(rooms: List<GridRoom>, north: Int, intent: Intent?, a: Analysis, onUnlock: () -> Unit, onDone: () -> Unit, unlocked: Boolean, cols: Int, rows: Int) {
    val colors = VastuTheme.colors
    // buildZoneMapModel memoises its own heavy part internally (C14), keyed on rooms/analysis/grid/
    // theme, so repeated recompositions here reuse the room + wedge lists rather than rebuilding.
    val model = buildZoneMapModel(rooms, a, north, cols, rows)

    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Your result · free")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        ScoreDisplay(score = a.score)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(verdictLine(a.score, intent), style = VastuTheme.type.body, color = colors.textSecondary)

        // The engine's honest, plain-language caveats (unusual shape, tilt, long-and-narrow).
        if (a.notes.isNotEmpty()) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            NotesStrip(a.notes)
        }

        Divider()
        SectionLabel("Zone map")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ZoneMap(
                model = model,
                modifier = Modifier.fillMaxWidth(0.62f),
                showLabels = false,
                contentDescription = "Your plan with Vastu zones, North at $north degrees, ${spokenScore(a.score, LocalDecimalMark.current)}.",
            )
        }

        Divider()
        SectionLabel("Biggest problems")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        val defects = a.defects
        val top = defects.take(3)
        if (top.isEmpty()) {
            VText("No major defects found — a strong start.", style = VastuTheme.type.body, color = colors.textSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                top.forEach { d ->
                    VastuCard(accent = colors.verdictDefect) {
                        // FlowRow, not Row: the verdict pill + a multi-word provenance tag are two
                        // unweighted children that, at 320 dp / font 2.0, overflow and clip (the tag
                        // broke mid-word: "Traditiona/l practice"). FlowRow wraps the tag onto its own
                        // line instead. Matches the Report screen's fix (UI-POLISH §3.D).
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                        ) {
                            VerdictPill(VastuVerdict.DEFECT)
                            ProvenanceTag(d.provenance.toVastu())
                        }
                        Spacer(Modifier.height(VastuTheme.spacing.s2))
                        VText(defectTitle(d, a.roomResults), style = VastuTheme.type.h3, color = colors.textPrimary)
                        Spacer(Modifier.height(VastuTheme.spacing.s1))
                        VText(d.explanation, style = VastuTheme.type.bodySm, color = colors.textSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // Once the report is unlocked, don't keep showing the ₹699 paywall — offer the report
        // straight away (E2E-ASSESSMENT §B11). onUnlock routes to the report when already unlocked.
        if (unlocked) {
            VastuButton("See the full report", onClick = onUnlock)
        } else {
            val remaining = remainingIssueCount(a)
            UnlockCard(remaining = remaining, onUnlock = onUnlock)
        }

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // The scale is named in the sentence, so the caveat travels with the number wherever the
        // number goes. "A summary, not a measurement" is new wording: a score with a decimal in it
        // looks more precise than the same score written whole, and this is the one line that can
        // say so plainly, for free, right under it.
        VText(
            "The score out of 10 is VastuFirst's own way of summarising the report — a summary, not a measurement, and not part of the tradition. Vastu is traditional guidance for your own decisions, not a guaranteed outcome.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )

        // Always a visible way out of the flow to the saved-plans list — a first-time user has no
        // other in-app path to Home (E2E-ASSESSMENT §A2).
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton("See all my plans", onClick = onDone, style = VastuButtonStyle.SECONDARY)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

private fun verdictLine(score: Int, intent: Intent?): String {
    val living = intent == Intent.LIVING
    return when {
        score >= 75 -> "Strong — a few refinements left."
        score >= 50 && living -> "Workable, with real problems worth addressing — remedies can help."
        score >= 50 -> "Workable, with real problems to fix while it is still on paper."
        living -> "Several core placements work against the tradition — remedies can help."
        else -> "Several core placements work against you — worth fixing now, on paper."
    }
}

private fun remainingIssueCount(a: Analysis): Int {
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
        VastuButton("See the full report", onClick = onUnlock)
        VText("Preview build: no payment is taken yet — the report unlocks on this device.", style = VastuTheme.type.caption, color = colors.textTertiary)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.padding(vertical = VastuTheme.spacing.s4).fillMaxWidth().height(VastuTheme.borders.regular).background(VastuTheme.colors.borderDefault),
    )
}
