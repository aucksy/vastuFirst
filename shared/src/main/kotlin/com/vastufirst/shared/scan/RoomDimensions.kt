// RoomDimensions.kt — the room sizes PRINTED on the plan, which we used to throw away.
//
// ⭐ WHY THIS EXISTS. Everything measured about the reader says the same thing: it reads printed
// TEXT at about 95 % and guesses rectangles at 40–70 %. Room shapes were coming entirely from the
// guessed rectangles while the printed sizes — the reliable signal, sitting right there in the same
// caption — were being deleted by the label cleaner as "measurement tokens".
//
// The cost of that showed up on the owner's own flat (Green Court, Sector 90, Gurgaon). Measured
// against the sheet, FOUR of its six dimensioned rooms came out with their orientation INVERTED:
//
//     printed                     drawn            printed w/d   drawn w/d
//     LOBBY    10'-0" X 12'-9"    6 x 2 cells         0.78         3.00
//     KITCHEN   6'-11" X 9'-7"    4 x 1 cells         0.72         4.00
//     PASSAGE   2'-3"  X 9'-6"    3 x 1 cells         0.24         3.00
//     BED ROOM 10'-0" X 10'-6"    6 x 4 cells         0.95         1.50
//
// That is not cosmetic. A room's Vastu direction is decided by where it sits inside the home's
// footprint, so a room of the wrong shape sits in the wrong direction. Re-shaping that plan to its
// printed sizes moves the KITCHEN and the BEDROOM into different directions — between them about
// half of the entire flat's scored weight, and the kitchen is the joint-highest-weighted room the
// engine scores.
//
// The printed numbers are trustworthy: on that sheet they total 353 sq ft against a stated 336 sq ft
// of carpet area, and the 5 % difference is the wall thickness. This is arithmetic on text, not a
// judgement, so it never asks the model anything (safety rule S1 is untouched).
package com.vastufirst.shared.scan

/** A room's size as PRINTED on the plan, normalised to millimetres so plans can be compared. */
data class PrintedSize(val widthMm: Double, val depthMm: Double) {
    val area: Double get() = widthMm * depthMm

    /** Width ÷ depth. Below 1 means the room is drawn deeper than it is wide. */
    val ratio: Double get() = widthMm / depthMm
}

/**
 * Read the size a plan prints beside a room name.
 *
 * Handles the two conventions the corpus actually contains — feet and inches
 * (`BED ROOM 10'-0"X10'-6"`, `LOBBY 10'-3½"X14'-10½"`) and bare millimetres
 * (`BEDROOM 6750X4350`) — including the fraction glyphs, the optional hyphen, curly quotes, and
 * `X`, `x` or `×` as the separator.
 *
 * Returns null whenever there is no honest PAIR of numbers, which is the common case and must stay
 * cheap: `LIFT (8 PERSON)` states a capacity and `BEDROOM-1` states an index. A single-dimension
 * strip caption — `BALCONY 6'-0" WIDE` — is also not a pair, but it is no longer thrown away:
 * [stripDepth] reads it as the strip's printed DEPTH, the one dimension a strip has, and the
 * mapper sets the strip's narrow axis from it while its length stays the wall it was read along.
 */
object RoomDimensions {

    private const val MM_PER_FOOT = 304.8

    /**
     * Vulgar fractions as they are actually printed on Indian architectural sheets — and as the
     * reader TYPES them, which is not the same thing.
     *
     * ⭐ The ASCII forms are a measured bug, not a nicety (plan doc §3p): the models transcribe a
     * printed `½` as the three characters `1/2`, so `10'-7½"` arrives as `10'-71/2"`. The old
     * glyph-only rule then failed the whole pair, the room lost its printed size, and on the
     * furnished-render sheet enough rooms lost theirs that the room-count gate refused a fully
     * dimensioned reply as TOO_MANY_ROOMS. Backtracking resolves the ambiguity: for `71/2` the
     * engine first tries inches=71 (leaving `/2` unmatched), retreats, and lands on 7 + 1/2.
     *
     * ⚠ Honest limit: `11/2` (a transcribed 11½) parses as 1½ — ten inches under. The ASCII form
     * is genuinely ambiguous and under-reading it costs less than refusing the size outright.
     */
    private val FRACTIONS = mapOf(
        "½" to 0.5, "¼" to 0.25, "¾" to 0.75, "⅓" to 1.0 / 3, "⅔" to 2.0 / 3, "⅛" to 0.125,
        "1/2" to 0.5, "1/4" to 0.25, "3/4" to 0.75, "1/3" to 1.0 / 3, "2/3" to 2.0 / 3, "1/8" to 0.125,
    )

    private const val FRACTION = "(½|¼|¾|⅓|⅔|⅛|1/2|1/4|3/4|1/3|2/3|1/8)"

    // One feet-inches measurement: 10'-0"  ·  12'1"  ·  9'-10½"  ·  10'-71/2"  ·  10'
    // Built as a string so the two halves of a pair share one definition.
    private const val FEET_INCHES =
        "(\\d+)\\s*['′’]\\s*-?\\s*(\\d+)?\\s*$FRACTION?\\s*[\"″”]?"

    private val PAIR_FEET_INCHES = Regex("$FEET_INCHES\\s*[X×]\\s*$FEET_INCHES")

    /**
     * A bare number pair: `6750X4350`, `2950 X 4200`, `12X14`.
     *
     * At least two digits a side, so an index or a small count cannot be read as a size, and the
     * look-around keeps it away from anything already carrying a foot or inch mark.
     */
    private val PAIR_PLAIN =
        Regex("(?<![\\d.'\"′″])(\\d{2,5})\\s*(?:MM|CM)?\\s*[X×]\\s*(\\d{2,5})\\s*(?:MM|CM)?(?!\\d)")

    /**
     * A metre pair as Indian sheets print it: `3.72m X 4.50m`, `(3.17mX0.90m)+(1.51mX0.40m)`.
     *
     * ⚠ The `M` is required after **both** numbers, and that is the whole safety of this rule. A
     * looser version that accepted `12 X 14 M` would also read `12X14` — a foot pair — as a
     * twelve-metre room. Real sheets print the unit on each number, so demanding it costs nothing
     * and removes the ambiguity entirely.
     *
     * This was added because the owner's own plan prints metres first and feet second on every
     * caption. The feet half happens to be there too, so it parsed by luck; a metres-only sheet
     * would have gone through with no size at all.
     */
    private val PAIR_METRES =
        Regex("(?<![\\d.])(\\d{1,2}(?:\\.\\d{1,3})?)\\s*M\\s*[X×]\\s*(\\d{1,2}(?:\\.\\d{1,3})?)\\s*M(?![A-Z])")

    private const val MM_PER_METRE = 1000.0

    /**
     * Below this, a bare pair reads as FEET (`12X14`); at or above it, as millimetres
     * (`6750X4350`). No real room is 100 feet across, and none is 99 mm.
     */
    private const val FEET_IF_UNDER = 100

    /**
     * The size for one room the reader returned, preferring the field the plan printed.
     *
     * ⭐ [ScanBox.printedSize] is the whole point — see the note on that field. It falls back to the
     * label because the recorded replies this feature was measured against predate the field and
     * carry the dimensions inside the caption (`BEDROOM 6750X4350`), and a fixture that stops
     * working is a fixture that stops proving anything.
     */
    fun of(box: ScanBox): PrintedSize? =
        box.printedSize.takeIf { it.isNotBlank() }?.let { parse(it) } ?: parse(box.label)

    /**
     * ⭐ A SINGLE-dimension strip caption: `BALCONY 1825 WIDE` / `5'-5" WIDE` / `6'-0" WIDE`.
     *
     * On real sheets this caption sits on the strips — balconies, service ledges — and the number
     * is the strip's clear DEPTH (how far it comes out from the home); its length is whatever wall
     * it runs along. It used to parse as NO size at all, which produced the owner's 336 sq ft
     * plan: every sized room shrank to print scale while the balcony kept the reader's sketch
     * rectangle — a quarter of the whole page — and towered over every real room on the grid.
     *
     * A full pair wins first, so a caption carrying both never half-matches as a strip. Millimetre
     * and feet-inches conventions both appear on real sheets (`1825 WIDE` on the 336 sq ft plan,
     * `5'-5" WIDE` on the tower sheet, `1525 WIDE` on the 526); the bare-number rule is the same
     * as [PAIR_PLAIN]'s — under [FEET_IF_UNDER] reads as feet, at or above it as millimetres.
     */
    fun stripDepthOf(box: ScanBox): Double? =
        box.printedSize.takeIf { it.isNotBlank() }?.let { stripDepth(it) } ?: stripDepth(box.label)

    fun stripDepth(label: String): Double? {
        val s = label.uppercase()
        if (parse(s) != null) return null
        STRIP_FEET_WIDE.find(s)?.let { m ->
            val d = feetInches(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            return if (d > 0.0) d * MM_PER_FOOT else null
        }
        STRIP_PLAIN_WIDE.find(s)?.let { m ->
            val d = m.groupValues[1].toDoubleOrNull() ?: return null
            if (d <= 0.0) return null
            return if (d < FEET_IF_UNDER) d * MM_PER_FOOT else d
        }
        return null
    }

    private val STRIP_FEET_WIDE = Regex("$FEET_INCHES\\s*WIDE")

    private val STRIP_PLAIN_WIDE = Regex("(?<![\\d.'\"′″])(\\d{2,5})\\s*(?:MM|CM)?\\s*WIDE")

    fun parse(label: String): PrintedSize? {
        val s = label.uppercase()

        PAIR_FEET_INCHES.find(s)?.let { m ->
            val a = feetInches(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val b = feetInches(m.groupValues[4], m.groupValues[5], m.groupValues[6])
            return if (a > 0.0 && b > 0.0) PrintedSize(a * MM_PER_FOOT, b * MM_PER_FOOT) else null
        }

        PAIR_METRES.find(s)?.let { m ->
            val a = m.groupValues[1].toDoubleOrNull() ?: return null
            val b = m.groupValues[2].toDoubleOrNull() ?: return null
            return if (a > 0.0 && b > 0.0) PrintedSize(a * MM_PER_METRE, b * MM_PER_METRE) else null
        }

        PAIR_PLAIN.find(s)?.let { m ->
            val a = m.groupValues[1].toDoubleOrNull() ?: return null
            val b = m.groupValues[2].toDoubleOrNull() ?: return null
            if (a <= 0.0 || b <= 0.0) return null
            return if (a < FEET_IF_UNDER && b < FEET_IF_UNDER) {
                PrintedSize(a * MM_PER_FOOT, b * MM_PER_FOOT)
            } else {
                PrintedSize(a, b)
            }
        }
        return null
    }

    private fun feetInches(feet: String, inches: String, fraction: String): Double {
        val f = feet.toDoubleOrNull() ?: return 0.0
        val i = inches.toDoubleOrNull() ?: 0.0
        val fr = FRACTIONS[fraction] ?: 0.0
        return f + (i + fr) / 12.0
    }
}
