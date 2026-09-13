package com.vastufirst.engine

import kotlin.math.PI
import kotlin.math.floor

/**
 * The engine's arithmetic helpers, written without `java.lang.Math`.
 *
 * **Why this file exists.** The scorer is compiled twice: once for the phone, and once to
 * JavaScript so the admin panel can re-score a home in a browser using THIS code rather than a
 * second implementation that drifts. `java.lang.Math` does not exist on the JavaScript side, so
 * every call site moved here.
 *
 * **Why not `kotlin.math.round`.** It rounds a tie to the nearest EVEN integer, which is not what
 * `Math.round` does and would have moved scores silently. `Math.round(double)` is specified as
 * `floor(a + 0.5)`, ties going to positive infinity, and NaN going to zero — all three of which
 * [roundHalfUp] reproduces exactly. `Double.roundToInt()` matches the first two and **throws** on
 * NaN, so it is deliberately not used either.
 */
internal fun roundHalfUp(v: Double): Long {
    if (v.isNaN()) return 0L
    return floor(v + 0.5).toLong()
}

/** [roundHalfUp], narrowed to Int — the shape every scoring call site wanted anyway. */
internal fun roundHalfUpToInt(v: Double): Int = roundHalfUp(v).toInt()

/** Radians to degrees. `java.lang.Math.toDegrees` is the same multiplication. */
internal fun toDegrees(radians: Double): Double = radians * 180.0 / PI

/** Degrees to radians. `java.lang.Math.toRadians` is the same multiplication. */
internal fun toRadians(degrees: Double): Double = degrees * PI / 180.0
