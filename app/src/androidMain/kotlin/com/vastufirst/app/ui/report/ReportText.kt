package com.vastufirst.app.ui.report

import com.vastufirst.app.ui.common.short
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.NotChecked
import com.vastufirst.shared.PadaVerdict
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
        "The ${r.zone.short()} is neither a direction this rule calls right for a $prose nor one it " +
            "rules out. It is simply not where the tradition puts one" +
            (if (rule.ideal.isEmpty()) "." else " — that is ${zoneList(rule.ideal)}.")
    }
    return listOf(head, rule.rationale.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
}

/** The whole "not ideal" band in one honest line, so nobody has to guess what it costs them. */
const val NOT_IDEAL_INTRO: String =
    "None of these is a defect and none of them is prohibited — each is simply not the direction " +
        "the tradition prefers. They do count towards your score, which is why they are listed here " +
        "rather than left out."

// ---- the front door -------------------------------------------------------------------------

/** How the tradition reads the door position this door landed on. */
fun padaStanding(v: PadaVerdict): String = when (v) {
    PadaVerdict.AUSPICIOUS -> "one the tradition counts favourable"
    PadaVerdict.MODERATE -> "one the tradition counts middling"
    PadaVerdict.MIXED -> "one the sources read both ways"
    PadaVerdict.INAUSPICIOUS -> "one the tradition counts unfavourable"
}

/** "On the north wall · position 3 of 32" — where the door stands, without a code. */
fun doorPlaceLine(d: DoorResult): String =
    "On the ${d.pada.side.short().lowercase()} wall · position ${d.pada.ordinal} of 32"

/** The door position's own name and what the tradition attaches to it. */
fun doorNameLine(d: DoorResult): String {
    val name = d.pada.name
    val domain = d.pada.domain
    return when {
        name == null -> "The sources leave this position unnamed — we say so rather than fill the gap."
        domain == null -> name
        else -> "$name — $domain"
    }
}

/**
 * Why the door matters more than any room, in the reader's words. The 32 named positions have been
 * in the app since the first build and were never shown to anyone.
 */
fun doorExplanation(d: DoorResult): String {
    val base = "The tradition lays 32 named positions around the edge of the plan, eight to a wall, " +
        "and judges the main entrance by which one it stands on. Yours is ${padaStanding(d.verdict)}. " +
        "The front door carries more weight in this reading than any single room."
    return if (d.spansTwoPadas) {
        "$base Your doorway is wide enough to stand across two of these positions; it has been read " +
            "on the one it mostly sits on."
    } else {
        base
    }
}

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
        more > 0 -> "All ${a.defects.size} problems — $more you haven't seen — each with the whole " +
            "reason behind it and remedies for that problem in that direction"
        else -> "The whole reason behind each problem, and remedies for that problem in that direction"
    }
    val notIdeal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    if (notIdeal > 0) {
        out += "$notIdeal ${plural(notIdeal, "room", "rooms")} rated not ideal — which, and why"
    }
    val good = a.roomResults.count { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
    if (good > 0) {
        out += "$good ${plural(good, "room", "rooms")} already right, and why the tradition says so"
    }
    if (a.doorResult != null) out += "Your front door read by name on the 32-position table"
    if (a.disputes.isNotEmpty()) out += "Where the schools disagree, with both readings"
    return out
}

/** The one line under the unlock heading — exact, never a rounded-up "issues". */
fun remainingLine(a: Analysis, shownFree: Int): String {
    val more = (a.defects.size - shownFree).coerceAtLeast(0)
    val notIdeal = a.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
    val bits = mutableListOf<String>()
    if (more > 0) bits += "$more more ${plural(more, "problem", "problems")}"
    if (notIdeal > 0) bits += "$notIdeal rated not ideal"
    return if (bits.isEmpty()) "Every fix and remedy, ranked" else bits.joinToString(" · ")
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
