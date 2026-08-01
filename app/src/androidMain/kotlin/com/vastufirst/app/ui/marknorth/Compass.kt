package com.vastufirst.app.ui.marknorth

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The phone's compass, as an aid to setting North (docs/SCORE-ACCURACY-CAVEATS.md #3).
 *
 * North is the single input nothing can check: every room's direction rotates with it, so a reading
 * a few degrees out silently moves rooms near a boundary into the wrong zone, and the app had no way
 * at all to help — the user typed a number or dragged a dial from memory.
 *
 * ⚠ The whole file is deliberately split so the ARITHMETIC is pure and testable and only the sensor
 * plumbing is Android. Getting the sign backwards here would be worse than having no compass at all:
 * it would put confident, precise, wrong directions on every room. So the conversion is one pure
 * function with its convention written down and pinned by tests.
 */

/** How much the compass can be trusted right now. */
enum class CompassQuality {
    /** The phone reports the reading is unusable — usually a magnet or a metal desk. */
    UNRELIABLE,

    /** Usable but coarse: the phone wants the figure-of-eight wave. */
    NEEDS_CALIBRATION,

    /** Good. */
    GOOD,
}

/** What the compass block on Mark North is doing. Passed in as plain data so the screen stays pure. */
data class CompassState(
    /** False on a phone with no compass hardware — then nothing about it is shown at all. */
    val supported: Boolean = false,
    /** True while the sensor is actually being read (the user opened the helper). */
    val live: Boolean = false,
    /** Bearing the TOP EDGE of the phone points to, clockwise from magnetic north. Null until a reading arrives. */
    val headingDeg: Float? = null,
    val quality: CompassQuality = CompassQuality.GOOD,
)

/**
 * ⭐ THE CONVENTION, stated once.
 *
 * `north` (what the app stores and the engine consumes) is the bearing of true North measured
 * **inside the drawing's own frame, clockwise from the top of the plan** — that is exactly what the
 * North dial emits, `atan2(dx, -dy)` from the plan's centre.
 *
 * The instruction given to the user is "point the top of your phone the same way as the top of your
 * plan". So [headingDeg] is the real-world bearing of the top of the plan. North, measured clockwise
 * from the top of the plan, is therefore the same angle measured the other way round: 360 − heading.
 *
 * Cross-checked against the engine independently: the engine rotates the plan by `north` degrees
 * anticlockwise to bring it into true-North space, which sends the plan's up-vector (0,1) to
 * (−sin north, cos north). At north = 90° that is (−1, 0), i.e. due West — and 360 − 90 = 270°, a
 * heading of due West. The two derivations agree, which is the point of doing both.
 */
fun northOffsetForHeading(headingDeg: Float): Int {
    val h = ((headingDeg % 360f) + 360f) % 360f
    return ((360f - h).roundToInt() % 360 + 360) % 360
}

/**
 * Circular exponential smoothing of a bearing, so the live reading settles instead of flickering.
 *
 * ⚠ Averaged as a VECTOR, never as a number. A plain weighted mean of 350° and 10° is 180° — the
 * exact opposite direction — and the bug only appears when the user happens to be facing north,
 * which is precisely the case a Vastu app cannot afford to get wrong.
 */
fun smoothHeading(previous: Float?, sample: Float, alpha: Float = 0.25f): Float {
    val s = ((sample % 360f) + 360f) % 360f
    if (previous == null) return s
    val pr = Math.toRadians(previous.toDouble())
    val sr = Math.toRadians(s.toDouble())
    val x = alpha * sin(sr) + (1 - alpha) * sin(pr)
    val y = alpha * cos(sr) + (1 - alpha) * cos(pr)
    val deg = Math.toDegrees(atan2(x, y))
    val norm = ((deg % 360.0) + 360.0) % 360.0
    return (if (norm >= 359.9995) 0.0 else norm).toFloat()
}

/** The eight-point compass word for a bearing — "North", "North-East", … Used to read a heading aloud. */
fun compassWord(bearingDeg: Float): String {
    val b = ((bearingDeg % 360f) + 360f) % 360f
    val names = arrayOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West")
    return names[(((b + 22.5f) / 45f).toInt()) % 8]
}
