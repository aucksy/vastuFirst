package com.vastufirst.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
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
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.ZoneInfo
import com.vastufirst.app.ui.common.screenRoot

/**
 * Full report (§6.5/§6.6) — branches on intent (§2).
 *
 * ⭐ ONLY "I am building" is offered layout changes, and that is the owner's ruling (v0.6.6). Someone
 * BUYING is looking at a home that is already standing, and someone ALREADY LIVING there cannot
 * rebuild it — telling either of them to move the kitchen is advice they cannot act on, and it
 * crowds out the advice they can. For both, this screen is remedies only: no "change the layout",
 * no "if you ever renovate", and nothing about redrawing the plan.
 *
 * Every rule carries its provenance tag. Disputes show both readings, no winner.
 *
 * ⭐ WHAT THIS SCREEN IS FOR, restated because it had drifted: it is the thing the customer pays
 * ₹699 for, and it used to explain almost nothing. A problem was a room name, a direction and two
 * remedies that were the same two remedies on almost every problem. A room that was already right
 * carried no reason at all. And a room rated "not ideal" appeared NOWHERE — while the free score
 * screen counted it in "N more issues" to justify the price. Every sentence added here comes out of
 * the rule data with its provenance attached; nothing is written on the screen that the dataset
 * cannot source ([ReportText]).
 */
@Composable
fun ReportScreen(vm: NewPlanViewModel, onDone: () -> Unit) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the report renders headlessly from
    // fixture state in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    ReportContent(analysis = analysis, intent = vm.intent, onDone = onDone)
}

/** Full report as a pure function of its state — no ViewModel — so the render harness can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportContent(
    analysis: Analysis?,
    intent: Intent?,
    onDone: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    val a = analysis
    val resolvedIntent = intent ?: Intent.BUILDING
    // ⭐ The one branch in this document. True for BUYING and for ALREADY LIVING — both are looking
    // at a home whose walls are already up — and it removes every layout suggestion rather than
    // demoting it. Only BUILDING sees "change the layout", because only BUILDING can.
    val remediesOnly = resolvedIntent != Intent.BUILDING

    if (a == null) {
        // Never a forever spinner: if the draft was reclaimed in the background, offer a way back to
        // the saved plans instead of trapping the user on "Preparing your report…" (E2E-ASSESSMENT §A4).
        Column(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s4),
        ) {
            Spacer(Modifier.height(VastuTheme.spacing.s8))
            LoadingState("Preparing your report…")
            VastuButton("Go to my plans", onClick = onDone, style = VastuButtonStyle.SECONDARY)
        }
        return
    }

    val zones = a.zoneInfo

    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        // FlowRow, not Row (audit C5): at 200 % font "FULL REPORT" and the intent pill are together
        // wider than the screen, and a Row drew them into each other. Here the pill drops to its own
        // line instead, where it can wrap on word boundaries with the full width to itself.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            SectionLabel("Full report", modifier = Modifier.align(Alignment.CenterVertically))
            IntentBadge(resolvedIntent)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(if (remediesOnly) "What to do now" else "What to change", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // Copy cut (4 Aug 2026): "Ranked by how much each matters" dropped from all three —
            // the section header right below says "most important first", so it was said twice on
            // every report.
            when (resolvedIntent) {
                Intent.BUILDING -> "Nothing is built yet — every change below is still free to make."
                Intent.BUYING -> "This home is already built, so everything below can be done without moving a wall."
                Intent.LIVING -> "Walls can't move, so everything below works in the home as it stands."
            },
            style = VastuTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        if (a.notes.isNotEmpty()) {
            NotesStrip(a.notes)
            Spacer(Modifier.height(VastuTheme.spacing.s4))
        }

        // The 16-zone reading isn't built yet, so its segment is shown disabled ("(soon)") and is not
        // tappable — no live-looking control that silently does nothing (E2E-ASSESSMENT §B10).
        // ⚠ Short words, and that is a constraint not a preference. At 200 % font "Traditional" was
        // wider than its half of the row and the reader saw "Iraditional 9-zone" — every word here
        // must fit a half-width segment on its own. The caption below carries the full meaning.
        // "(soon)" not "· soon" (audit C6): when the label wraps at 200 % font it must break into
        // whole readable pieces — "· soon" stranded its dot at the end of the first line.
        VastuSegmented(
            options = listOf("8 zones", "16 zones (soon)"),
            selectedIndex = 0,
            onSelect = {},
            disabledIndices = setOf(1),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // "school" was the one house term left on a customer screen (audit E5); the section
        // header "Where the schools disagree" keeps the word where it earns its place.
        VText("The 16-zone reading is coming in a later update.", style = VastuTheme.type.caption, color = colors.textTertiary)

        // ⭐ THE FRONT DOOR. It is the highest-weighted single element in the whole reading and it had
        // no section at all — while the 32 named door positions, each with a meaning, sat unused in
        // the rule data since the first build.
        a.doorResult?.let { door ->
            SectionHeader("Your front door")
            DoorCard(door, zones, remediesOnly)
        }

        SectionHeader(if (remediesOnly) "What to do, most important first" else "What to change, most important first")
        val defects = a.defects
        if (defects.isEmpty()) {
            VText("No defects to rank — the placements read well.", style = VastuTheme.type.body, color = colors.textSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                defects.forEachIndexed { i, d -> DefectCard(i + 1, d, a.roomResults, zones, remediesOnly) }
            }
        }

        // ⭐ NOT IDEAL — the band that appeared nowhere. The free score screen counts these in
        // "N more issues" to justify ₹699, so a report that omitted them was selling issues it never
        // showed. They are not defects and the copy says so plainly.
        val notIdeal = a.roomResults.filter { it.verdict == Verdict.SUBOPTIMAL }
        if (notIdeal.isNotEmpty()) {
            SectionHeader("Not ideal — worth knowing")
            VText(NOT_IDEAL_INTRO, style = VastuTheme.type.bodySm, color = colors.textSecondary)
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                notIdeal.forEach { r ->
                    RoomVerdictCard(r, zones, colors.verdictSuboptimal, whyNotIdeal(r))
                }
            }
        }

        // Already right — and now WITH a reason. Being told why something is right is worth as much
        // to a reader as being told why something is wrong; this section used to be a tick.
        val good = a.roomResults.filter { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
        if (good.isNotEmpty()) {
            SectionHeader("Already right — leave alone")
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                good.forEach { r -> RoomVerdictCard(r, zones, colors.verdictIdeal, whyRight(r)) }
            }
        }

        // Where schools disagree.
        DisputesSection(a.disputes)

        // ⚠ Not assessed — in WORDS. This list used to print the app's internal rule codes ("· X-09")
        // straight to the customer.
        val notChecked = a.notChecked
        if (notChecked.isNotEmpty()) {
            SectionHeader("Couldn't check these yet")
            VText(
                "We didn't have the details to check these, so they are neither passed nor failed.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
                notChecked.forEach {
                    VText("· ${notCheckedLine(it)}", style = VastuTheme.type.bodySm, color = colors.textSecondary)
                }
                notCheckedHow(notChecked).forEach {
                    VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
                }
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

        // The end of the flow: a visible way out to the saved-plans list. A first-time user reaches
        // the report with no other in-app path to Home (E2E-ASSESSMENT §A2).
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton("Done — see all my plans", onClick = onDone)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

/**
 * One problem, with the whole of its reason.
 *
 * ⚠ The reason was not on this screen at all — the card went straight from the room's name to the
 * fix. The free score screen showed a one-line version and the paid report showed none.
 */
@Composable
private fun DefectCard(rank: Int, d: Defect, rooms: List<RoomResult>, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = if (remediesOnly) colors.secondary else colors.verdictDefect) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            VerdictPill(VastuVerdict.DEFECT)
            VText("#$rank", style = VastuTheme.type.caption, color = colors.textTertiary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(defectTitle(d, rooms), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        ProvenanceTag(d.provenance.toVastu())

        // What that direction IS, before why this placement is wrong in it.
        zoneMeaning(d.zone, zones)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(d.explanation, style = VastuTheme.type.bodySm, color = colors.textSecondary)

        Spacer(Modifier.height(VastuTheme.spacing.s3))
        // ⭐ Each remedy carries its OWN provenance, not the defect's. Within one problem they
        // genuinely differ — a rite from the Mayamatam and a 20th-century rock-salt bowl can sit two
        // lines apart, and the reader has to be able to tell which is which.
        //
        // ⚠ When the home is already standing, the layout line is DROPPED, not demoted. It used to
        // survive as "if you ever renovate" — which is a building instruction wearing a smaller hat,
        // and the owner's ruling is that a buyer and a resident should be shown remedies and
        // nothing else.
        val remedies = d.remedies.map { remedyLine(it) }
        if (remediesOnly) {
            RemedyBlock(remedies, d.remedyNote)
        } else {
            d.layoutFix?.let { LayoutBlock(it); Spacer(Modifier.height(VastuTheme.spacing.s2)) }
            RemedyBlock(remedies, d.remedyNote)
        }
    }
}

/**
 * One room with its verdict and its reason — used by both "not ideal" and "already right".
 *
 * The pill sits on its own line rather than sharing one with the title, which reads better at 200 %
 * font on a narrow phone than a row that has to wrap.
 */
@Composable
private fun RoomVerdictCard(
    r: RoomResult,
    zones: List<ZoneInfo>,
    accent: androidx.compose.ui.graphics.Color,
    reason: String,
) {
    val colors = VastuTheme.colors
    VastuCard(accent = accent) {
        VerdictPill(r.verdict.toVastu())
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText("${r.type.label()} — ${r.zone.short()}", style = VastuTheme.type.h3, color = colors.textPrimary)
        r.rule?.provenance?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            ProvenanceTag(it.toVastu())
        }
        zoneMeaning(r.zone, zones)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(reason, style = VastuTheme.type.bodySm, color = colors.textSecondary)
        }
    }
}

/** The front door, read on the 32-position table the tradition actually uses. */
@Composable
private fun DoorCard(d: DoorResult, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = padaAccent(d.verdict)) {
        // A TagPill in the door's own words, not a VerdictPill — see [padaBadge].
        TagPill(text = padaBadge(d.verdict), color = padaAccent(d.verdict))
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorTitle(d), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorPlaceLine(d), style = VastuTheme.type.body, color = colors.textPrimary)
        doorUnnamedNote(d)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        zoneMeaning(d.pada.side, zones)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorExplanation(d, remediesOnly), style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

@Composable
private fun padaAccent(v: PadaVerdict) = with(VastuTheme.colors) {
    when (v) {
        PadaVerdict.AUSPICIOUS -> verdictIdeal
        PadaVerdict.MODERATE -> verdictAcceptable
        PadaVerdict.MIXED -> verdictSuboptimal
        PadaVerdict.INAUSPICIOUS -> verdictDefect
    }
}

/** Only ever drawn for someone still BUILDING — see `remediesOnly` at the top of this file. */
@Composable
private fun LayoutBlock(text: String) = AdviceBlock("✦ Change the layout — free now", text, VastuTheme.colors.primary)

/**
 * The remedies for THIS problem — and, where the classical texts record none, the sentence that says
 * so. Filling the table with an invented remedy would destroy the one thing this product is for.
 */
@Composable
private fun RemedyBlock(remedies: List<String>, note: String?) {
    val colors = VastuTheme.colors
    if (remedies.isEmpty() && note.isNullOrBlank()) return
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm).background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel("If it cannot move — remedies", color = colors.secondaryText)
        if (!note.isNullOrBlank()) VText(note, style = VastuTheme.type.bodySm, color = colors.textTertiary)
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

/**
 * ⭐ "WHERE THE SCHOOLS DISAGREE" — both readings, and now which one the number uses.
 *
 * ⚠ Public, and rendered on its own in the screenshot harness, for a reason. This section sits at the
 * very bottom of a long document, so **no golden has ever contained it** — a golden is a viewport,
 * not a document, and the fixtures that lift the other lower sections into frame still cannot reach
 * this one. It is also the section that carries the product's whole promise: we have ruled on some of
 * these questions, and we still show the reader both sides. Shipping it unphotographed would repeat
 * the exact mistake the report release ended.
 */
@Composable
fun DisputesSection(disputes: List<Dispute>) {
    if (disputes.isEmpty()) return
    val colors = VastuTheme.colors
    SectionHeader("Where the schools disagree")
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        disputes.forEach { disp ->
            VastuCard(accent = colors.provenanceDisp) {
                VText(disp.title, style = VastuTheme.type.h3, color = colors.textPrimary)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingA.label, disp.readingA.text)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingB.label, disp.readingB.text)
                // ⭐ Where the NUMBER stands, on the disputes we have ruled on. Showing both readings
                // and staying silent about which one moved the score would be a half-truth — and this
                // is the one product whose promise is that it does not quietly pick a side. Absent on
                // every dispute the score genuinely skips, so it never claims a position we lack.
                disp.howWeScore?.let { scored ->
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    ReadingRow("What your score uses", scored)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(VastuTheme.spacing.s6))
    SectionLabel(text)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
}
