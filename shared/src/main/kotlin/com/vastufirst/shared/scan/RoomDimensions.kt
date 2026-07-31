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
 * cheap: `BALCONY 6'-0" WIDE` states one dimension, `LIFT (8 PERSON)` states a capacity, and
 * `BEDROOM-1` states an index. Guessing a shape from any of those would be worse than not trying.
 */
object RoomDimensions {

    private const val MM_PER_FOOT = 304.8

    /** Vulgar fractions as they are actually printed on Indian architectural sheets. */
    private val FRACTIONS = mapOf(
        '½' to 0.5,     // half
        '¼' to 0.25,    // quarter
        '¾' to 0.75,    // three quarters
        '⅓' to 1.0 / 3, // third
        '⅔' to 2.0 / 3, // two thirds
        '⅛' to 0.125,   // eighth
    )

    // One feet-inches measurement: 10'-0"  ·  12'1"  ·  9'-10½"  ·  10'
    // Built as a string so the two halves of a pair share one definition.
    private const val FEET_INCHES =
        "(\\d+)\\s*['′’]\\s*-?\\s*(\\d+)?\\s*([½¼¾⅓⅔⅛])?\\s*[\"″”]?"

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
     * Below this, a bare pair reads as FEET (`12X14`); at or above it, as millimetres
     * (`6750X4350`). No real room is 100 feet across, and none is 99 mm.
     */
    private const val FEET_IF_UNDER = 100

    fun parse(label: String): PrintedSize? {
        val s = label.uppercase()

        PAIR_FEET_INCHES.find(s)?.let { m ->
            val a = feetInches(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val b = feetInches(m.groupValues[4], m.groupValues[5], m.groupValues[6])
            return if (a > 0.0 && b > 0.0) PrintedSize(a * MM_PER_FOOT, b * MM_PER_FOOT) else null
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
        val fr = fraction.firstOrNull()?.let { FRACTIONS[it] } ?: 0.0
        return f + (i + fr) / 12.0
    }
}
