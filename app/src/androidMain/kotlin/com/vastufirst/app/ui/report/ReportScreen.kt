package com.vastufirst.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.BalanceMeter
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.TagPill
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VerdictPill
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.foundation.clickableTap
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
 * crowds out the advice they can. For both, this screen is remedies only.
 *
 * ⭐⭐ REBUILT TO BE READ (9 Aug 2026). The content did not change; the way it is delivered did,
 * because the old one was a twenty-screen wall of prose that opened on a front-door card and never
 * once told the reader how their home had done overall. Four things changed and each is load-bearing:
 *
 *  1. **It opens with a verdict.** Score, band, one sentence — before any finding.
 *  2. **[BalanceMeter] shows what is RIGHT before what is wrong.** A reader with a decent home used
 *     to close this feeling accused, because "already right" sat below every problem.
 *  3. **Three chapters, not one scroll** — fix first / already right / good to know, with counts.
 *  4. **Every finding collapses.** The headline, room, zone and verdict are always visible; the whole
 *     reason, the zone's meaning, the layout change and every remedy are one tap down. ⚠ Nothing is
 *     CUT — the Sanskrit, the deities, the provenance on every line and the sentence admitting when
 *     the texts record no remedy are all still there. Cutting the Vastu vocabulary to make this
 *     shorter would remove the only thing worth ₹699.
 *
 * ⭐ FREE vs PAID lives in [FreeTier] and nowhere else. With [unlocked] false, the entrance, kitchen
 * and toilets read in full and every other room still shows its name and verdict with its reasoning
 * locked. The score and the counts are never hidden — see the note in [FreeTier].
 */
@Composable
fun ReportScreen(vm: NewPlanViewModel, onDone: () -> Unit, onUnlock: () -> Unit = {}) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the report renders headlessly from
    // fixture state in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    ReportContent(
        analysis = analysis,
        intent = vm.intent,
        unlocked = vm.unlocked,
        onUnlock = onUnlock,
        onDone = onDone,
    )
}

/**
 * The three chapters.
 *
 * ⚠ Public, and [ReportContent] takes an [initialTab], for ONE reason: a chapter the harness cannot
 * open is a chapter no picture has ever contained. Two thirds of the paid report would otherwise be
 * unphotographable — the exact shape of defect UI-POLISH §6.4 was written after ("a golden is a
 * viewport, not a document"). Every chapter is rendered at the full config matrix because every
 * chapter can be selected from a test.
 */
const val TAB_FIX = 0
const val TAB_RIGHT = 1
const val TAB_MORE = 2

/** Full report as a pure function of its state — no ViewModel — so the render harness can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportContent(
    analysis: Analysis?,
    intent: Intent?,
    unlocked: Boolean = true,
    initialTab: Int = TAB_FIX,
    /**
     * Open every finding at once.
     *
     * ⚠ A testing and photography seam, and a necessary one. Collapsed, a card's reasoning is not in
     * the semantics tree at all — so the test that pins "only someone BUILDING is ever offered a
     * layout change" would find nothing and pass for the wrong reason, and no golden would ever
     * contain an opened card. Both of those are how a paid feature quietly stops working.
     */
    expandAll: Boolean = false,
    onUnlock: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    val a = analysis
    val resolvedIntent = intent ?: Intent.BUILDING
    val remediesOnly = resolvedIntent != Intent.BUILDING

    if (a == null) {
        // Never a forever spinner: if the draft was reclaimed in the background, offer a way back to
        // the saved plans instead of trapping the user on "Preparing your report…".
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
    val defects = a.defects
    val notIdeal = a.roomResults.filter { it.verdict == Verdict.SUBOPTIMAL }
    val good = a.roomResults.filter { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
    val moreCount = notIdeal.size + a.disputes.size + a.notChecked.size

    // ⚠ Hoisted ABOVE the `when` that swaps chapters (UI-POLISH §3.B). Scroll state declared inside a
    // branch is torn down and recreated every time the branch changes, which silently jumps the
    // reader back to the top — the same defect the room palette had in v0.2.1.
    val scroll = rememberScrollState()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }

    Box(Modifier.screenRoot(colors.paper)) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(VastuTheme.spacing.s6),
        ) {
            // FlowRow, not Row: at 200 % font the label and the intent pill are together wider than
            // the screen, and a Row draws them into each other.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            ) {
                SectionLabel(
                    if (unlocked) "Full report" else "Your report",
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                IntentBadge(resolvedIntent)
            }

            Spacer(Modifier.height(VastuTheme.spacing.s4))
            VerdictHeader(a.score, defects.size, remediesOnly, unlocked)

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            BalanceMeter(
                right = good.size,
                notIdeal = notIdeal.size,
                needsFixing = defects.size,
            )

            if (a.notes.isNotEmpty()) {
                Spacer(Modifier.height(VastuTheme.spacing.s4))
                NotesStrip(a.notes)
            }

            // ⭐ START HERE — the one thing to do first. The old report ranked its problems but never
            // said "begin with this", so a reader facing eight cards had to work out the entry point
            // themselves. Always free: it is the single most useful sentence on the screen.
            defects.firstOrNull()?.let { top ->
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                StartHere(top, a.roomResults, remediesOnly)
            }

            // ---- chapters -------------------------------------------------------------------
            // FlowRow of chips, NOT a segmented control (UI-POLISH §6.7b). Three fixed-width segments
            // holding "Already right" shatter at 200 % font on a 320 dp phone — the ink is drawn wider
            // than the segment and characters are lost off both ends, with every geometry gate green.
            // Chips wrap onto their own line instead, where each has the full width to itself.
            Spacer(Modifier.height(VastuTheme.spacing.s6))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            ) {
                // ⚠ `onClick` is NOT the last parameter of VastuChip (modifier is), so a trailing
                // lambda does not compile here. Named, deliberately.
                VastuChip("Fix first (${defects.size})", selected = tab == TAB_FIX, onClick = { tab = TAB_FIX })
                VastuChip("Already right (${good.size})", selected = tab == TAB_RIGHT, onClick = { tab = TAB_RIGHT })
                VastuChip("Good to know ($moreCount)", selected = tab == TAB_MORE, onClick = { tab = TAB_MORE })
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            when (tab) {
                TAB_RIGHT -> ChapterRight(a.doorResult, good, zones, unlocked, remediesOnly, expandAll)
                TAB_MORE -> ChapterMore(a, notIdeal, zones, unlocked, expandAll)
                else -> ChapterFix(defects, a.roomResults, zones, unlocked, remediesOnly, expandAll)
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            Box(
                Modifier.fillMaxWidth().clip(VastuTheme.shapes.md)
                    .background(colors.surface).padding(VastuTheme.spacing.s4),
            ) {
                VText(
                    "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome.",
                    style = VastuTheme.type.body, color = colors.textPrimary,
                )
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            VastuButton("Done — see all my plans", onClick = onDone)

            // Clearance so the sticky pay bar never covers the last control. Without it the bar sits
            // on top of "Done" at the end of the scroll, which is the bottom-CTA-unreachable defect.
            Spacer(Modifier.height(if (unlocked) VastuTheme.spacing.s4 else VastuTheme.spacing.s16))
        }

        if (!unlocked) {
            PayBar(Modifier.align(Alignment.BottomCenter), a, onUnlock)
        }
    }
}

/* ─────────────────────────── the opening verdict ─────────────────────────── */

/**
 * Score, band and one sentence — the first thing on the screen.
 *
 * The old report opened straight into the front-door card, so the one question a reader actually
 * arrived with ("is my home all right?") was never answered on the screen they paid for.
 */
@Composable
private fun VerdictHeader(score: Int, defectCount: Int, remediesOnly: Boolean, unlocked: Boolean) {
    val colors = VastuTheme.colors
    val mark = LocalDecimalMark.current
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        // Baseline-aligned so "6.4" and "/ 10" sit on one line; FlowRow so the band word drops to its
        // own line at 200 % font rather than squeezing the number.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VText(
                scoreOutOfTen(score, mark),
                style = VastuTheme.type.display,
                color = scoreBandColor(score),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            VText(
                "/ 10",
                style = VastuTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TagPill(
                text = bandWord(score),
                color = scoreBandColor(score),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        VText(verdictSentence(score, defectCount, remediesOnly, unlocked), style = VastuTheme.type.body, color = colors.textSecondary)
    }
}

private fun bandWord(score: Int): String = when {
    score >= 75 -> "Strong"
    score >= 50 -> "Workable"
    else -> "Needs work"
}

/**
 * One honest sentence about the whole home. It leads with what is working, because the counts
 * immediately below break the same home down and a reader needs the shape before the detail.
 */
private fun verdictSentence(score: Int, defectCount: Int, remediesOnly: Boolean, unlocked: Boolean): String {
    val head = when {
        score >= 75 && defectCount == 0 -> "This home reads well throughout, with nothing the tradition counts as a defect."
        score >= 75 -> "Most of this home reads well."
        score >= 50 -> "This home reads workably, with real problems worth addressing."
        else -> "Several core placements work against this home."
    }
    val tail = when {
        defectCount == 0 -> ""
        remediesOnly -> " Everything below can be done without moving a wall."
        else -> " Nothing is built yet, so every change below is still free to make."
    }
    val free = if (unlocked) "" else " Your entrance, kitchen and toilets are below in full, free."
    return head + tail + free
}

/* ─────────────────────────── start here ─────────────────────────── */

@Composable
private fun StartHere(d: Defect, rooms: List<RoomResult>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = colors.verdictDefect, background = colors.surfaceRaised) {
        SectionLabel("Start here", color = colors.verdictDefect)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(defectTitle(d, rooms), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Of everything below, this is the one that moves your score most.",
            style = VastuTheme.type.bodySm, color = colors.textSecondary,
        )
        val first = if (remediesOnly) d.remedies.firstOrNull()?.let { remedyLine(it) } else d.layoutFix
        if (!first.isNullOrBlank()) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Column(
                Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
                    .background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
            ) {
                SectionLabel("Do this first", color = colors.secondaryText)
                VText(first, style = VastuTheme.type.bodySm, color = colors.textPrimary)
            }
        }
    }
}

/* ─────────────────────────── chapters ─────────────────────────── */

@Composable
private fun ChapterFix(
    defects: List<Defect>,
    rooms: List<RoomResult>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    remediesOnly: Boolean,
    expandAll: Boolean,
) {
    val colors = VastuTheme.colors
    if (defects.isEmpty()) {
        VText("No defects to rank — the placements read well.", style = VastuTheme.type.body, color = colors.textSecondary)
        return
    }
    VText(
        "Ranked by how much each one moves your score. Tap a row for the whole reason and what to do.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s4))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        defects.forEachIndexed { i, d ->
            val free = unlocked || d.isFreeToRead(rooms)
            FindingRow(
                title = defectTitle(d, rooms),
                meta = zoneLine(d.zone, zones),
                accent = if (remediesOnly) colors.secondary else colors.verdictDefect,
                verdict = VastuVerdict.DEFECT,
                rank = i + 1,
                locked = !free,
                // The first readable finding starts open, so a reader meets the depth of this report
                // immediately instead of a column of shut rows they have to guess their way into.
                startOpen = expandAll || (i == 0 && free),
            ) {
                DefectBody(d, zones, remediesOnly)
            }
        }
    }
}

@Composable
private fun ChapterRight(
    door: DoorResult?,
    good: List<RoomResult>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    remediesOnly: Boolean,
    expandAll: Boolean,
) {
    val colors = VastuTheme.colors
    // ⭐ The front door leads this chapter and is ALWAYS free ([FreeTier]). It is the highest-weighted
    // single element in the reading, and the 32 named positions sat unused in the rule data for the
    // app's first eight builds while the screen showed nothing about the door at all.
    door?.let {
        SectionLabel("Your front door")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        DoorCard(it, zones, remediesOnly)
        Spacer(Modifier.height(VastuTheme.spacing.s6))
    }
    if (good.isEmpty()) {
        VText("No room came back already right this time.", style = VastuTheme.type.body, color = colors.textSecondary)
        return
    }
    VText(
        "${good.size} of your rooms are where the tradition wants them. Being told why something is right is worth as much as being told why something is wrong.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s4))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        good.forEachIndexed { i, r ->
            val free = unlocked || r.isFreeToRead()
            FindingRow(
                title = "${r.type.label()} — ${r.zone.short()}",
                meta = zoneLine(r.zone, zones),
                accent = colors.verdictIdeal,
                verdict = r.verdict.toVastu(),
                rank = null,
                locked = !free,
                startOpen = expandAll || (i == 0 && free),
            ) {
                RoomBody(r, zones, whyRight(r))
            }
        }
    }
}

@Composable
private fun ChapterMore(
    a: Analysis,
    notIdeal: List<RoomResult>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    expandAll: Boolean,
) {
    val colors = VastuTheme.colors

    if (notIdeal.isNotEmpty()) {
        SectionLabel("Not ideal — and not a fault")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(NOT_IDEAL_INTRO, style = VastuTheme.type.bodySm, color = colors.textSecondary)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            notIdeal.forEachIndexed { i, r ->
                val free = unlocked || r.isFreeToRead()
                FindingRow(
                    title = "${r.type.label()} — ${r.zone.short()}",
                    meta = zoneLine(r.zone, zones),
                    accent = colors.verdictSuboptimal,
                    verdict = VastuVerdict.SUBOPTIMAL,
                    rank = null,
                    locked = !free,
                    startOpen = expandAll || (i == 0 && free),
                ) {
                    RoomBody(r, zones, whyNotIdeal(r))
                }
            }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s6))
    }

    DisputesSection(a.disputes)

    val notChecked = a.notChecked
    if (notChecked.isNotEmpty()) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("We could not check these")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "Neither passed nor failed — we did not have the details.",
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
}

/* ─────────────────────────── the expandable finding ─────────────────────────── */

/**
 * One finding: always shows what it is and how it read; opens to the whole reasoning.
 *
 * ⚠ [rememberSaveable], not [androidx.compose.runtime.remember] — an open card must survive a
 * rotation and a brief process reclaim, or a reader loses their place mid-report.
 *
 * ⚠ A [locked] row is NOT a blurred teaser. It still names the room, the zone and the verdict,
 * because the free score already counts it and hiding that would be the bait Product PRD §6.4
 * forbids. Only the reasoning is behind the price, and the row says exactly that.
 */
@Composable
private fun FindingRow(
    title: String,
    meta: String?,
    accent: Color,
    verdict: VastuVerdict,
    rank: Int?,
    locked: Boolean,
    startOpen: Boolean = false,
    body: @Composable () -> Unit,
) {
    val colors = VastuTheme.colors
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }

    VastuCard(accent = if (locked) colors.borderStrong else accent, background = colors.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = if (locked) null else if (open) "collapse this" else "read the whole reason",
                    enabled = !locked,
                ) { open = !open },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VerdictPill(verdict)
                if (rank != null) VText("#$rank", style = VastuTheme.type.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(title, style = VastuTheme.type.h3, color = colors.textPrimary)
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(VastuTheme.spacing.s1))
                VText(meta, style = VastuTheme.type.bodySm, color = colors.textTertiary)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                when {
                    locked -> "Reasoning and remedies — in the full report"
                    open -> "Tap to close"
                    else -> "Tap to read the whole reason"
                },
                style = VastuTheme.type.caption,
                color = if (locked) colors.textTertiary else colors.primaryDark,
            )
        }
        if (open && !locked) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Box(Modifier.fillMaxWidth().height(VastuTheme.borders.regular).background(colors.borderDefault))
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            body()
        }
    }
}

/** The whole of a defect's reasoning — unchanged in substance from the first paid report. */
@Composable
private fun DefectBody(d: Defect, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    ProvenanceTag(d.provenance.toVastu())
    zoneMeaning(d.zone, zones)?.let {
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
    }
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(d.explanation, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    // ⭐ Each remedy carries its OWN provenance, not the defect's — a rite from the Mayamatam and a
    // 20th-century rock-salt bowl can sit two lines apart.
    val remedies = d.remedies.map { remedyLine(it) }
    if (remediesOnly) {
        RemedyBlock(remedies, d.remedyNote)
    } else {
        d.layoutFix?.let { LayoutBlock(it); Spacer(Modifier.height(VastuTheme.spacing.s2)) }
        RemedyBlock(remedies, d.remedyNote)
    }
}

@Composable
private fun RoomBody(r: RoomResult, zones: List<ZoneInfo>, reason: String) {
    val colors = VastuTheme.colors
    r.rule?.provenance?.let {
        ProvenanceTag(it.toVastu())
        Spacer(Modifier.height(VastuTheme.spacing.s2))
    }
    zoneMeaning(r.zone, zones)?.let {
        VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
    }
    if (reason.isNotBlank()) {
        VText(reason, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

/** The one-line zone identity under a finding's title — Sanskrit name and what it governs. */
private fun zoneLine(zone: com.vastufirst.shared.Zone, zones: List<ZoneInfo>): String? =
    zones.firstOrNull { it.zone == zone }?.let { info ->
        listOfNotNull(info.sanskrit, info.deity).joinToString(" · ").takeIf { it.isNotBlank() }
    }

/* ─────────────────────────── the pay bar ─────────────────────────── */

/**
 * Sticky, and it names what is still locked rather than shouting a price.
 *
 * ⚠ It sits over the scroll, so the column above reserves clearance for it — a bottom bar that
 * covers the last control is the unreachable-CTA defect UI-POLISH §3.B exists to prevent.
 */
@Composable
private fun PayBar(modifier: Modifier, a: Analysis, onUnlock: () -> Unit) {
    val colors = VastuTheme.colors
    val locked = a.lockedCount()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .border(VastuTheme.borders.regular, colors.borderDefault)
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        VText(
            if (locked > 0) "$locked more findings, with the reason and remedies for each"
            else "The whole reading, with the reason and remedies for each finding",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        VastuButton(
            "Unlock the full report",
            onClick = onUnlock,
            modifier = Modifier.testTag("report.unlock"),
        )
    }
}

/* ─────────────────────────── unchanged pieces ─────────────────────────── */

/** The front door, read on the 32-position table the tradition actually uses. */
@Composable
private fun DoorCard(d: DoorResult, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = padaAccent(d.verdict), background = colors.surfaceRaised) {
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
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
            .background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel("If it cannot move — remedies", color = colors.secondaryText)
        if (!note.isNullOrBlank()) VText(note, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        remedies.forEach { VText("· $it", style = VastuTheme.type.bodySm, color = colors.textSecondary) }
    }
}

@Composable
private fun AdviceBlock(heading: String, text: String, accent: Color) {
    val colors = VastuTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
            .background(accent.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
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
 * ⭐ "WHERE THE SCHOOLS DISAGREE" — both readings, and which one the number uses.
 *
 * ⚠ Public, and rendered on its own in the screenshot harness, for a reason. It sits at the bottom of
 * a long document, so no full-screen golden has ever contained it — a golden is a viewport, not a
 * document. It also carries the product's whole promise: we have ruled on some of these questions
 * and we still show the reader both sides.
 */
@Composable
fun DisputesSection(disputes: List<Dispute>) {
    if (disputes.isEmpty()) return
    val colors = VastuTheme.colors
    SectionLabel("Where the schools disagree")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Both readings, no winner declared — and which one your score follows.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        disputes.forEach { disp ->
            VastuCard(accent = colors.provenanceDisp, background = colors.surfaceRaised) {
                VText(disp.title, style = VastuTheme.type.h3, color = colors.textPrimary)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingA.label, disp.readingA.text)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingB.label, disp.readingB.text)
                // ⭐ Where the NUMBER stands, on the disputes we have ruled on. Showing both readings
                // and staying silent about which one moved the score would be a half-truth. Absent on
                // every dispute the score genuinely skips, so it never claims a position we lack.
                disp.howWeScore?.let {
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    ReadingRow("What your score uses", it)
                }
            }
        }
    }
}
