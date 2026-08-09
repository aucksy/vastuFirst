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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.billing.FALLBACK_PRICE
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.app.ui.details.coverageLine
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.report.firstSentence
import com.vastufirst.app.ui.report.unlockPreviewLines
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
import com.vastufirst.app.ui.common.screenRoot
import org.koin.compose.koinInject

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
    onAddDetails: () -> Unit,
    onChangeNorth: () -> Unit,
    onRestart: () -> Unit = {},
    billing: Billing = koinInject(),
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
        siteAnswers = vm.siteAnswers,
        onAddDetails = onAddDetails,
        onChangeNorth = onChangeNorth,
        billingState = billing.state,
        onRestart = onRestart,
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
    /** The optional extras the user has answered — drives the honest "what this covers" line. */
    siteAnswers: SiteAnswers = SiteAnswers(),
    onAddDetails: () -> Unit = {},
    /**
     * ⭐ Back to the North dial. Until v0.6.6 a home that had been saved could only be RENAMED: the
     * rooms and the North mark — the two things the whole score is built from — had no way back in,
     * so a wrong North meant deleting the home and drawing it again from nothing.
     */
    onChangeNorth: () -> Unit = {},
    /** Drives the price and the notice on the unlock card, so the score screen can never promise
     *  a charge the unlock screen would not make. */
    billingState: BillingState = BillingState(),
    /** Back to the flow's first question — the recovery path when rooms survived a process kill
     *  but the intent answer did not. Rooms stay on the shared draft the whole way. */
    onRestart: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    val a = analysis

    when {
        // Draft present AND complete, engine still computing (normal, ~50 ms): a calm loading line.
        // ⚠ `intent != null` is part of "complete": without it the engine can never produce a
        // result (buildEnginePlan returns null), so this branch would be a spinner with no exit —
        // exactly what a process-killed session used to land on (4 Aug 2026 audit, B1).
        a == null && rooms.isNotEmpty() && intent != null -> Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            LoadingState("Reading your home…")
        }

        // Rooms survived but the first answer didn't (the app was closed by the phone mid-flow,
        // on an older build that didn't restore it). The rooms are safe; only the "what brings
        // you here" answer is missing — so send the user to answer exactly that, keeping their
        // rooms, instead of spinning forever or dead-ending at the plans list.
        a == null && rooms.isNotEmpty() -> Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            GuidanceState(
                title = "One answer went missing",
                body = "Your rooms are safe. The first question — what brings you to Vastu — was lost when the app closed. Answer it again and we'll read your home.",
                action = { VastuButton("Answer the first question", onClick = onRestart) },
            )
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

        else -> ScoreResult(
            rooms, north, intent, a, onUnlock, onDone, unlocked, cols, rows, siteAnswers,
            onAddDetails, onFix, onChangeNorth, billingState,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreResult(
    rooms: List<GridRoom>, north: Int, intent: Intent?, a: Analysis, onUnlock: () -> Unit,
    onDone: () -> Unit, unlocked: Boolean, cols: Int, rows: Int,
    siteAnswers: SiteAnswers, onAddDetails: () -> Unit,
    onFix: () -> Unit, onChangeNorth: () -> Unit, billingState: BillingState,
) {
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

        // ⭐⭐ THE WAY BACK IN. Directly under the picture of their home, because that picture is
        // where someone notices it is wrong — a room in the wrong place, or North pointing the wrong
        // way. Until v0.6.6 there was no way back at all once a home was saved: the saved-homes list
        // offered a rename and nothing else, so the only route to a corrected score was to delete the
        // home and draw it again. Two buttons, each naming exactly what it changes; no icons, because
        // this screen's reader is more reliably served by words in daylight.
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VText(
            "Not quite right? Change it — the score follows.",
            style = VastuTheme.type.bodySm, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VastuButton(
            "Change the rooms or the front door",
            onClick = onFix,
            style = VastuButtonStyle.SECONDARY,
            large = false,
            modifier = Modifier.testTag("score.edit.rooms"),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VastuButton(
            "Change which way North is",
            onClick = onChangeNorth,
            style = VastuButtonStyle.SECONDARY,
            large = false,
            modifier = Modifier.testTag("score.edit.north"),
        )

        // ⭐ WHAT THE NUMBER ACTUALLY LOOKED AT (docs/SCORE-ACCURACY-CAVEATS.md #4). The score has only
        // ever come from rooms, the front door and the shape — but it reads as a complete verdict, and
        // it is really a CEILING: a home with its water tank in the worst possible corner scored
        // exactly the same as one with it in the best, because nothing ever asked. Saying so is not
        // optional for a paid product, and the sentence comes with the way to close the gap.
        Divider()
        SectionLabel("What this covers")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(coverageLine(siteAnswers), style = VastuTheme.type.body, color = colors.textSecondary)
        if (siteAnswers.answeredCount < SiteItem.entries.size) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton(
                "Answer a few more and check more",
                onClick = onAddDetails,
                style = VastuButtonStyle.SECONDARY,
                large = false,
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
                        // ⭐ The OPENING SENTENCE only. The reasons are now several sentences long —
                        // which direction, which element, which deity, what the tradition holds and
                        // where the thing belongs instead — and that whole reason is what the paid
                        // report gives. Printing it in full here would both give the paid part away
                        // and make the free screen three times taller than it is designed to be.
                        VText(firstSentence(d.explanation), style = VastuTheme.type.bodySm, color = colors.textSecondary)
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
            UnlockCard(a = a, shownFree = top.size, onUnlock = onUnlock, billingState = billingState)
        }

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // The scale is named in the sentence, so the caveat travels with the number wherever the
        // number goes. "A summary, not a measurement" is new wording: a score with a decimal in it
        // looks more precise than the same score written whole, and this is the one line that can
        // say so plainly, for free, right under it.
        VText(
            // Copy cut (4 Aug 2026): 36 words -> 21. All four claims survive: our own summary,
            // not a measurement, not part of the tradition, no promised outcome.
            "This score is our own summary — not a measurement, and not part of the tradition. " +
                "Vastu is guidance, not a promise.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )

        // Always a visible way out of the flow to the saved-plans list — a first-time user has no
        // other in-app path to Home (E2E-ASSESSMENT §A2).
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton("See all my plans", onClick = onDone, style = VastuButtonStyle.SECONDARY)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

/**
 * The one line under the number. It must not promise something the reader cannot do: "fix it while
 * it is still on paper" is true only for someone still BUILDING. A buyer and a resident are both
 * looking at a home that is already standing, so both are pointed at remedies — the same ruling the
 * full report follows (v0.6.6).
 */
private fun verdictLine(score: Int, intent: Intent?): String {
    val remediesOnly = intent != null && intent != Intent.BUILDING
    return when {
        score >= 75 -> "Strong — a few refinements left."
        score >= 50 && remediesOnly -> "Workable, with real problems worth addressing — remedies can help."
        score >= 50 -> "Workable, with real problems to fix while it is still on paper."
        remediesOnly -> "Several core placements work against the tradition — remedies can help."
        else -> "Several core placements work against you — worth fixing now, on paper."
    }
}

/**
 * ⭐ The way into the report — NOT a paywall any more (9 Aug 2026).
 *
 * ⚠ This used to be the ₹699 wall: a price, a list of what was behind it, and a button that led to
 * checkout. It no longer leads to checkout, so it no longer talks like it does. The report itself is
 * now the shop window — the entrance, kitchen and toilets read in full for free there, and the price
 * is asked on the report's own pay bar, next to the findings it would unlock. Leading with a price
 * for a report the reader can already open would be asking for money before showing the goods.
 *
 * ⚠ It still previews with THIS home's real counts (`unlockPreviewLines`). That exists because the
 * card once offered one number — "N more issues" — which quietly added rooms rated "not ideal" that
 * the paid report did not contain at all, i.e. it sold issues the report never showed.
 */
@Composable
private fun UnlockCard(a: Analysis, shownFree: Int, onUnlock: () -> Unit, billingState: BillingState) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.lg)
            .background(colors.secondary.copy(alpha = 0.12f))
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
    ) {
        Column(Modifier.fillMaxWidth()) {
            VText("Read your report", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
            VText(
                "Your entrance, kitchen and toilets in full — free.",
                style = VastuTheme.type.bodySm, color = colors.textSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
            unlockPreviewLines(a, shownFree).forEach {
                VText("· $it", style = VastuTheme.type.bodySm, color = colors.textSecondary)
            }
        }
        VastuButton("Open my report", onClick = onUnlock)
        VText(
            "The rest of the rooms are ${billingState.price ?: FALLBACK_PRICE}, one-time, asked for inside the report.",
            style = VastuTheme.type.caption, color = colors.textTertiary,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier.padding(vertical = VastuTheme.spacing.s4).fillMaxWidth().height(VastuTheme.borders.regular).background(VastuTheme.colors.borderDefault),
    )
}
