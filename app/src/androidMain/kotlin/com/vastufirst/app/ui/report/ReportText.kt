package com.vastufirst.app.ui.report

import com.vastufirst.app.ui.common.short
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.NotChecked
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.Provenance
import com.vastufirst.shared.Remedy
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import com.vastufirst.shared.ZoneInfo

/**
 * ⭐ EVERY SENTENCE THE REPORT SAYS ABOUT A PLACEMENT, as pure functions of engine output.
 *
 * This file exists because of what a ₹699 report actually was: a room name, a direction and a tick.
 * A reader was told a room was wrong without being told **why the tradition says so**, told a room
 * was right with no reason at all, and never shown the middle band at all — while the score screen
 * counted that band in "N more issues" to justify the price.
 *
 * Nothing here invents Vastu. Every sentence is assembled from data that is already in the versioned
 * rule set and already carries a provenance tag: the direction's own Sanskrit name, presiding deity,
 * element and domain come from `zones.json`; the reason a room wants the directions it wants is the
 * rule's own `rationale`; a defect's reason is the defect definition's own `explanation`.
 *
 * Pure Kotlin on purpose — no Compose — so every sentence can be pinned by a unit test.
 */

/** Room names as they read in a sentence ("master bedroom", not the chip's short "Master"). */
fun RoomType.prose(): String = when (this) {
    RoomType.ENTRANCE -> "front door"
    RoomType.KITCHEN -> "kitchen"
    RoomType.MASTER_BEDROOM -> "master bedroom"
    RoomType.BEDROOM -> "bedroom"
    RoomType.POOJA -> "prayer room"
    RoomType.TOILET -> "toilet"
    RoomType.BATHROOM -> "bathroom"
    RoomType.LIVING -> "living room"
    RoomType.DINING -> "dining room"
    RoomType.STAIRCASE -> "staircase"
    RoomType.STUDY -> "study"
    RoomType.STORE -> "store"
    RoomType.GUEST_BEDROOM -> "guest bedroom"
    RoomType.GARAGE -> "garage"
    RoomType.BALCONY -> "balcony"
    RoomType.BASEMENT -> "basement"
    RoomType.COURTYARD -> "courtyard"
    RoomType.UTILITY -> "utility room"
    RoomType.CORRIDOR -> "corridor"
}

/** "the North, the East or the North-East" — never a bare list of codes. */
fun zoneList(zones: Collection<Zone>): String {
    val names = zones.map { "the ${it.short()}" }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
    }
}

/**
 * What a direction IS, in the tradition's own terms. This material has been sitting in the rule set
 * unused since the first build — every direction has a Sanskrit name, a presiding deity, an element
 * and a domain, and a paying reader was shown none of it.
 */
fun zoneMeaning(zone: Zone, zones: List<ZoneInfo>): String? {
    val info = zones.firstOrNull { it.zone == zone } ?: return null
    val parts = mutableListOf<String>()
    info.sanskrit?.let { parts += it }
    info.deity?.let { parts += if (zone == Zone.BRAHMASTHAN) "$it's own square" else "the quarter of $it" }
    info.element?.let { parts += "the element of ${it.lowercase()}" }
    val head = parts.joinToString(", ")
    val domain = info.domain?.let { if (it.endsWith(".")) it else "$it." }
    return when {
        head.isEmpty() && domain == null -> null
        head.isEmpty() -> "In the tradition: $domain"
        domain == null -> "$head."
        else -> "$head. In the tradition: $domain"
    }
}

/**
 * Why a room that is already right IS right. Being told why something is right is worth as much to
 * a reader as being told why something is wrong — and this section used to carry no reason at all.
 */
fun whyRight(r: RoomResult): String {
    val rule = r.rule ?: return ""
    val prose = r.type.prose()
    val head = when (r.verdict) {
        Verdict.IDEAL -> "This is exactly where the tradition puts a $prose."
        Verdict.ACCEPTABLE ->
            if (rule.ideal.isEmpty()) "This is a direction the rule accepts for a $prose."
            else "Not the first choice, but a direction the rule accepts for a $prose — " +
                "the first choice is ${zoneList(rule.ideal)}."
        else -> ""
    }
    return listOf(head, rule.rationale.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * ⭐ Why a "not ideal" room is not ideal.
 *
 * These rooms appeared NOWHERE in the report — they fell between the problems list and the
 * already-right list — while the free score screen counted them in "N more issues" to justify the
 * price. So the app sold issues the report never showed. This is the sentence that closes that.
 */
fun whyNotIdeal(r: RoomResult): String {
    val rule = r.rule ?: return ""
    val prose = r.type.prose()
    val head = if (rule.ideal.isEmpty() && rule.acceptable.isEmpty()) {
        "This rule names no preferred direction for a $prose, so the ${r.zone.short()} is neither " +
            "right nor wrong for it."
    } else {
        // Copy cut (4 Aug 2026): both halves of the precision claim kept — not called right,
        // not ruled out.
        "The ${r.zone.short()} is not wrong for a $prose, and not right either" +
            (if (rule.ideal.isEmpty()) " — the tradition simply doesn't place one here."
             else " — the tradition puts one in ${zoneList(rule.ideal)}.")
    }
    return listOf(head, rule.rationale.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
}

/** The whole "not ideal" band in one honest line, so nobody has to guess what it costs them. */
// Copy cut (4 Aug 2026): 38 words -> 25; the three claims survive whole — not a defect, not
// prohibited, still counted in the score.
const val NOT_IDEAL_INTRO: String =
    "None of these is a defect, and none is prohibited — just not the direction the tradition " +
        "prefers. They still count in your score, so we list them."

// ---- the front door -------------------------------------------------------------------------

/** How the tradition reads the door position this door landed on. */
fun padaStanding(v: PadaVerdict): String = when (v) {
    PadaVerdict.AUSPICIOUS -> "one the tradition counts favourable"
    PadaVerdict.MODERATE -> "one the tradition counts middling"
    PadaVerdict.MIXED -> "one the sources read both ways"
    PadaVerdict.INAUSPICIOUS -> "one the tradition counts unfavourable"
}

/**
 * The word on the door card's badge.
 *
 * ⚠ NOT the room verdict vocabulary. The door card first carried a red "✕ Defect" pill — but only a
 * South-West corner door raises an actual defect, so on every other unfavourable door the reader was
 * shown the word "Defect" for something that appeared in no problems list and had no fix attached.
 * The door is read on its own scale, so it says so in its own words.
 */
fun padaBadge(v: PadaVerdict): String = when (v) {
    PadaVerdict.AUSPICIOUS -> "Favourable"
    PadaVerdict.MODERATE -> "Middling"
    PadaVerdict.MIXED -> "Read both ways"
    PadaVerdict.INAUSPICIOUS -> "Unfavourable"
}

/** The door card's heading — the wall spelled out, never a bare letter. */
fun doorTitle(d: DoorResult): String = "Front door — ${d.pada.side.short().lowercase()} wall"

/** "Position 14 of 32 · Antariksha" — which of the 32 it stands on, and its name where one exists. */
fun doorPlaceLine(d: DoorResult): String {
    val base = "Position ${d.pada.ordinal} of 32"
    val name = d.pada.name ?: return base
    val domain = d.pada.domain ?: return "$base · $name"
    return "$base · $name — $domain"
}

/**
 * ⚠ Two of the 32 positions are left unnamed in the sources we work from, and the app knows which.
 * Rather than render a blank or borrow a name from somewhere else, the report says so — and the
 * bundled sample home's own door happens to land on one of them, so this is not a rare path.
 */
fun doorUnnamedNote(d: DoorResult): String? =
    if (d.pada.name != null) null
    else "The classical sources leave this position unnamed. We say so rather than invent " +
        "a name; the reading is unaffected."

/**
 * Why the door matters more than any room, in the reader's words. The 32 named positions have been
 * in the app since the first build and were never shown to anyone.
 */
fun doorExplanation(d: DoorResult, remediesOnly: Boolean = false): String {
    val parts = mutableListOf(
        // Copy cut (10 Aug 2026): 44 words -> 27. Every claim survives whole — 32 named positions,
        // eight to a wall, judged on the one the door stands on, this door's standing, and that it
        // outweighs any single room. Only the joining words went.
        // ⚠ "32 named positions" is PINNED by two tests and must survive every copy cut. It is the
        // reading itself — the 32-position table is what the door verdict comes from — and a
        // shortened sentence that drops it explains a verdict by asserting it. Cut around it.
        "The tradition lays 32 named positions around the plan's edge, eight per wall, and reads " +
            "the entrance by the one it stands on. Yours is ${padaStanding(d.verdict)} — and the " +
            "front door outweighs any single room.",
    )
    // ⚠ A door can read unfavourably without raising a problem of its own — only the South-West
    // corner arc does that. So the reader would otherwise be told the most important element in the
    // report is unfavourable and given nothing to do about it.
    //
    // ⚠ The closing advice splits on whether the home is still on paper. Telling someone who is
    // buying or already living in a home to move their front door a few feet is advice they cannot
    // take, and the owner's ruling (v0.6.6) is that neither of them is offered a layout change. The
    // "same wall" fact stays in both — an unfavourable door must never be a dead end for the reader.
    if (d.verdict != PadaVerdict.AUSPICIOUS) {
        // Copy cut (4 Aug 2026): the door's weight was stated in the opening paragraph and
        // again here — once is enough.
        parts += if (remediesOnly) {
            "Only two or three of the eight positions on any wall count favourable — the exact " +
                "spot along the same wall matters."
        } else {
            "Only two or three positions per wall count favourable, so a door moved a few feet " +
                "along the same wall reads differently. Worth raising while it is still on paper."
        }
    }
    if (d.spansTwoPadas) {
        parts += "Your doorway spans two positions; it is read on the one it mostly sits on."
    }
    return parts.joinToString(" ")
}

// ---- remedies -------------------------------------------------------------------------------

/** The provenance tag's own words, so a remedy can carry it inline rather than as a pill. */
fun provenanceWords(p: Provenance): String = when (p) {
    Provenance.TEXT -> "From classical text"
    Provenance.DERIV -> "Traditional practice"
    Provenance.MOD -> "Modern practice"
    Provenance.DISP -> "Schools disagree"
}

/**
 * ⭐ A remedy AND WHERE IT COMES FROM, in one line.
 *
 * ⚠ The report used to render `remedy.text` and throw the provenance away — so a rock-salt bowl
 * invented in the 20th century and a rite prescribed in the Mayamatam arrived on the page looking
 * exactly alike. Traceability is the whole product; it cannot be the one field that gets dropped on
 * the way to the screen.
 *
 * Words rather than a coloured pill: the defect's own tag is already a pill above, five more would
 * be noise, and a phrase reads better than a colour to someone holding a phone in daylight.
 */
fun remedyLine(r: Remedy): String = "${r.text} (${provenanceWords(r.provenance)})"

// ---- the rules we could not run -------------------------------------------------------------

/** "· Where your water tank and borewell sit" — the raw rule code used to be printed instead. */
fun notCheckedLine(item: NotChecked): String = item.label

/** The one line under that list telling the reader how to close the gap. */
fun notCheckedHow(items: List<NotChecked>): List<String> =
    items.mapNotNull { it.how }.distinct()

// ---- the free score screen's preview ----------------------------------------------------------

/**
 * The opening sentence of a reason. The free screen shows this much; the paid report gives the whole
 * reason. That is the difference the paywall is actually selling, and it keeps the free screen from
 * growing three times taller when the reasons got longer.
 */
fun firstSentence(text: String): String {
    val cut = text.indexOf(". ")
    return if (cut <= 0) text else text.substring(0, cut + 1)
}

/**
 * ⭐ WHAT THE PAID REPORT ACTUALLY ADDS, counted off THIS home rather than written as marketing.
 *
 * The free screen used to offer one number — "N more issues" — where N quietly mixed the problems it
 * had not shown with the rooms rated "not ideal" that the report did not contain at all. Every line
 * below is a section the reader will really find, with this home's own counts, so the free screen
 * cannot promise something the report does not hold.
 */
fun unlockPreviewLines(a: Analysis, shownFree: Int): List<String> {
    val out = mutableListOf<String>()
    val more = (a.defects.size - shownFree).coerceAtLeast(0)
    out += when {
        a.defects.isEmpty() -> "The full reading, with the reasoning behind every placement"
        // Copy cut (4 Aug 2026), payment honesty: the counts and the reason+remedies promise
        // are unchanged; only the wind is gone.
        more > 0 -> "All ${a.defects.size} problems — $more new to you — each with its full " +
            "reason and remedies"
        else -> "The full reason behind each problem, with remedies"
    }
    val notIdeal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    if (notIdeal > 0) {
        out += "$notIdeal ${plural(notIdeal, "room", "rooms")} rated not ideal — which, and why"
    }
    // ⚠ THREE LINES IS A CEILING, not a coincidence. Every extra line here pushes the payment notice
    // under the unlock button below the fold on a 320 dp phone at large font — and a notice about
    // money that has to be scrolled to is the one thing this card must never do. So the remaining
    // sections are named together rather than each getting a line of their own.
    val good = a.roomResults.count { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
    val rest = mutableListOf<String>()
    if (good > 0) rest += "the $good already right, and why"
    if (a.doorResult != null) rest += "your front door by name"
    if (a.disputes.isNotEmpty()) rest += "both readings where the schools disagree"
    if (rest.isNotEmpty()) out += rest.joinToString(" · ").replaceFirstChar { it.uppercase() }
    return out
}

/** The one line under the unlock heading — exact, never a rounded-up "issues". */
fun remainingLine(a: Analysis, shownFree: Int): String {
    val more = (a.defects.size - shownFree).coerceAtLeast(0)
    val notIdeal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    val bits = mutableListOf<String>()
    if (more > 0) bits += "$more more ${plural(more, "problem", "problems")}"
    if (notIdeal > 0) bits += "$notIdeal ${plural(notIdeal, "room", "rooms")} rated not ideal"
    bits += "every reason and remedy in full"
    return bits.joinToString(" · ")
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
