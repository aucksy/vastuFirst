package com.vastufirst.app.ui.report

import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.directionWords
import com.vastufirst.app.ui.common.readingOrder
import com.vastufirst.app.ui.common.roomDisplayNames
import com.vastufirst.app.ui.common.rowStatus
import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.components.statusWords
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.ZoneInfo

/**
 * ⭐ THE FULL REPORT AS A DOCUMENT — what "Share this report as a PDF" writes (Product PRD §9 Phase 4:
 * "Export to PDF and share — this is the moment the product becomes useful, because it goes to the
 * architect").
 *
 * It is built from the SAME functions the report screen uses for its words (the verdict sentence, the
 * remedies a reader can act on, the flat/building rule, every room's reason, every finding's
 * explanation and provenance), so the PDF can never say something the screen does not, nor leave a
 * finding out (CLAUDE.md §2h — ReportExportTest checks every finding, room and dispute is in it).
 *
 * Only the FULL report is exported: the share button exists only once the report is unlocked.
 * Pure Kotlin, no Compose: each phone draws these blocks onto PDF pages in its own way.
 */
data class ReportDocument(val title: String, val subtitle: String, val blocks: List<ReportBlock>)

sealed interface ReportBlock {
    /** A section heading ("Your rooms"). */
    data class Heading(val text: String) : ReportBlock

    /** The score line: "3.1 / 10 · Needs work", and the one-sentence verdict under it. */
    data class Score(val score: String, val band: String, val sentence: String) : ReportBlock

    /** One finding or room: a small badge ("NEED FIXING"), its title, an optional quiet line, then text. */
    data class Item(val badge: String, val title: String, val meta: String?, val lines: List<Line>) : ReportBlock

    /** Plain text, quiet or not. */
    data class Text(val text: String, val quiet: Boolean = false) : ReportBlock
}

/** A line inside an item: a small label above it (an advice heading), or none. */
data class Line(val text: String, val label: String? = null, val quiet: Boolean = false)

fun buildReportDocument(
    a: Analysis,
    intent: Intent?,
    homeName: String,
    dateLabel: String,
    roomNames: Map<String, String> = emptyMap(),
    decimalMark: Char = '.',
): ReportDocument {
    val resolvedIntent = intent ?: Intent.BUILDING
    val remediesOnly = resolvedIntent != Intent.BUILDING
    val isFlat = a.propertyType == PropertyType.FLAT
    val zones = a.zoneInfo
    val defects = a.defects
    val blocks = mutableListOf<ReportBlock>()

    // --- the verdict ---
    blocks += ReportBlock.Score(
        score = "${scoreOutOfTen(a.score, decimalMark)} / 10",
        band = bandWord(a.score),
        sentence = verdictSentence(a.score, defects.size, remediesOnly, unlocked = true, isFlat = isFlat),
    )
    val good = a.roomResults.count { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
    val notIdeal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    val fixing = a.roomResults.count { it.verdict == Verdict.DEFECT }
    blocks += ReportBlock.Text("$good already right · $notIdeal not ideal · $fixing need fixing")
    a.notes.forEach { blocks += ReportBlock.Text(it.message, quiet = true) }

    // --- where to start ---
    defects.firstOrNull()?.let { top ->
        blocks += ReportBlock.Heading("Start here")
        blocks += defectItem(top, a, zones, remediesOnly, isFlat, roomNames, badge = "START HERE")
    }

    // --- the front door ---
    a.doorResult?.let { d ->
        blocks += ReportBlock.Heading("Your front door")
        blocks += ReportBlock.Item(
            badge = padaBadge(d.verdict).uppercase(),
            title = doorTitle(d),
            meta = doorPlaceLine(d),
            lines = listOfNotNull(
                doorUnnamedNote(d)?.let { Line(it, quiet = true) },
                zoneMeaning(d.pada.side, zones)?.let { Line(it, quiet = true) },
                Line(doorExplanation(d, remediesOnly = remediesOnly || isFlat)),
            ),
        )
    }

    // --- every room, worst first, as the screen orders them ---
    if (a.roomResults.isNotEmpty()) {
        blocks += ReportBlock.Heading("Your rooms (${a.roomResults.size})")
        val byRoom = defects.filter { it.roomId != null }.groupBy { it.roomId }
        val fallback = roomDisplayNames(a.roomResults.map { it.type })
        a.roomResults
            .mapIndexed { i, r -> r to (roomNames[r.roomId]?.takeIf { it.isNotBlank() } ?: fallback[i]) }
            .sortedBy { (r, _) -> r.rowStatus().readingOrder() }
            .forEach { (r, name) ->
                val roomDefects = byRoom[r.roomId].orEmpty()
                val lines = mutableListOf<Line>()
                if (roomDefects.isNotEmpty()) {
                    roomDefects.forEach { lines += defectLines(it, zones, remediesOnly, isFlat) }
                } else {
                    r.rule?.provenance?.let { lines += Line(provenanceWords(it), quiet = true) }
                    zoneMeaning(r.zone, zones)?.let { lines += Line(it, quiet = true) }
                    val reason = when {
                        r.verdict == Verdict.SUBOPTIMAL -> whyNotIdeal(r)
                        r.verdict == Verdict.NOT_SCORED && r.type == RoomType.ENTRANCE -> ENTRANCE_READ_AT_DOOR
                        r.verdict == Verdict.NOT_SCORED -> NOT_RATED_REASON
                        else -> whyRight(r)
                    }
                    if (reason.isNotBlank()) lines += Line(reason)
                }
                blocks += ReportBlock.Item(
                    badge = r.rowStatus().statusWords().uppercase(),
                    title = name,
                    meta = r.directionWords(),
                    lines = lines,
                )
            }
    }

    // --- the shape of the home and what surrounds it ---
    val structural = defects.filter { it.roomId == null }
    if (structural.isNotEmpty()) {
        blocks += ReportBlock.Heading("Your home's shape and surroundings (${structural.size})")
        structural.forEachIndexed { i, d ->
            blocks += defectItem(d, a, zones, remediesOnly, isFlat, roomNames, badge = "#${i + 1}")
        }
    }

    // --- where the schools disagree ---
    if (a.disputes.isNotEmpty()) {
        blocks += ReportBlock.Heading("Where the schools disagree (${a.disputes.size})")
        a.disputes.forEach { disp ->
            blocks += ReportBlock.Item(
                badge = "SCHOOLS DISAGREE",
                title = disp.title,
                meta = null,
                lines = listOfNotNull(
                    Line(disp.readingA.text, label = disp.readingA.label),
                    Line(disp.readingB.text, label = disp.readingB.label),
                    disp.howWeScore?.let { Line(it, label = "What your score uses") },
                ),
            )
        }
    }

    // --- what could not be checked ---
    if (a.notChecked.isNotEmpty()) {
        blocks += ReportBlock.Heading("We couldn't check these (${a.notChecked.size})")
        blocks += ReportBlock.Text("Neither passed nor failed — we lacked details.", quiet = true)
        a.notChecked.forEach { blocks += ReportBlock.Text("· ${notCheckedLine(it)}") }
        notCheckedHow(a.notChecked).forEach { blocks += ReportBlock.Text(it, quiet = true) }
    }

    // --- the honest frame, as on the sources screen ---
    blocks += ReportBlock.Heading("About this report")
    blocks += ReportBlock.Text(
        "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome.",
    )
    blocks += ReportBlock.Text(
        "Every finding says where its rule comes from: a classical text, traditional practice, modern " +
            "practice, or a point where the schools disagree. Rule set ${a.ruleSetVersion}.",
        quiet = true,
    )

    val who = when (resolvedIntent) {
        Intent.BUILDING -> "Building"
        Intent.BUYING -> "Buying"
        Intent.LIVING -> "Already living here"
    }
    val kind = if (isFlat) "Flat" else "House"
    return ReportDocument(
        title = "VastuFirst — $homeName",
        subtitle = "Full report · $who · $kind · $dateLabel",
        blocks = blocks,
    )
}

private fun defectItem(
    d: Defect,
    a: Analysis,
    zones: List<ZoneInfo>,
    remediesOnly: Boolean,
    isFlat: Boolean,
    roomNames: Map<String, String>,
    badge: String,
): ReportBlock.Item = ReportBlock.Item(
    badge = badge,
    title = defectTitle(d, a.roomResults, roomNames),
    meta = zoneLine(d.zone, zones),
    lines = defectLines(d, zones, remediesOnly, isFlat),
)

/** What the screen's DefectBody shows, in the same order: provenance, zone meaning, reason, advice. */
private fun defectLines(d: Defect, zones: List<ZoneInfo>, remediesOnly: Boolean, isFlat: Boolean): List<Line> {
    val lines = mutableListOf<Line>()
    lines += Line(provenanceWords(d.provenance), quiet = true)
    zoneMeaning(d.zone, zones)?.let { lines += Line(it, quiet = true) }
    lines += Line(d.explanation)
    val remedies = advisableRemedies(d, remediesOnly, isFlat).map { remedyLine(it) }
    fun remedyBlock() {
        d.remedyNote?.takeIf { it.isNotBlank() }?.let { lines += Line(it, label = REMEDIES_HEADING, quiet = true) }
        remedies.forEachIndexed { i, r ->
            lines += Line("· $r", label = if (i == 0 && d.remedyNote.isNullOrBlank()) REMEDIES_HEADING else null)
        }
    }
    when {
        isFlat && d.belongsToBuilding -> {
            lines += Line(BUILDING_NOT_YOURS, label = BUILDING_HEADING)
            remedyBlock()
        }
        remediesOnly -> remedyBlock()
        else -> {
            d.layoutFix?.let { lines += Line(it, label = LAYOUT_HEADING) }
            remedyBlock()
        }
    }
    return lines
}
