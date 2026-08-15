package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Plain-English "last updated" line for the saved-plans list (E2E-ASSESSMENT B12 — the list used to
 * say a fixed "updated recently" for every home). Buckets by whole calendar days in the given zone so
 * "today"/"yesterday" match what the user perceives, not a rolling 24-hour window:
 *
 *   same day    → "today"
 *   1 day ago   → "yesterday"
 *   2..6 days   → "N days ago"
 *   older       → "3 Jul" (or "3 Jul 2025" if a different year)
 *
 * ⚠ No "Updated" prefix, and that is a measured cut, not a style choice. Every caller puts this
 * after a "·" that already carries the when-was-this meaning ("Living · …", "5 rooms so far · …"),
 * and at 200 % font the prefix was exactly the ~8 characters that pushed the row's second line into
 * the ellipsis — the owner's phone showed "Updated 3 d…", a date that says nothing. The stated
 * blemish of v0.7.4.
 *
 * Pure: `now` and `zone` are passed in, so it is deterministic and unit-tested (and the screenshot
 * harness can pin a fixed `now` for stable goldens).
 */
fun relativeUpdated(
    updatedAt: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
): String {
    val then = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, today)
    return when {
        days <= 0L -> "today"          // same day (or a clock-skewed future stamp)
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        else -> {
            val pattern = if (then.year == today.year) "d MMM" else "d MMM yyyy"
            then.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}

/** The local calendar day [millis] falls on, in [zone]. Pure — the one place the conversion lives. */
fun localDayOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * How long from [now] until the next local midnight in [zone]. Pure, so it is unit-tested.
 *
 * ⚠ Floored at one second. A clock sitting exactly on midnight would otherwise answer 0 and spin
 * the caller's wait loop as fast as the phone allows — a flat battery instead of a stale date.
 */
fun millisUntilNextDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val nextMidnight = localDayOf(now, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (nextMidnight - now).coerceAtLeast(1_000L)
}

/**
 * ⚠⚠ NEVER CALL THIS FROM A STATELESS "…Content" COMPOSABLE. It waits forever, on purpose, and the
 * screenshot harness waits for the screen to go idle before it takes the picture — so a golden that
 * composed this would hang the whole render run with no error and no timeout. It belongs in the
 * stateful screen wrapper ONLY, which no golden ever composes. (docs/UI-POLISH.md §6.7a.)
 *
 * ⭐ WHY IT EXISTS. The saved-homes list dates ("today", "yesterday") were sampled once, when the
 * screen was first drawn, from a default argument. Leave the app open across midnight and every home
 * still claimed it was touched "today" — a day stale until something rebuilt the screen. Nothing was
 * lost, but the list quietly said the wrong thing about the user's own data, which is the one thing
 * this list exists to say.
 *
 * It wakes at most every [DAY_CLOCK_CHECK_MS] and **only publishes a new time when the local
 * calendar day has actually changed**, so the list recomposes once a day rather than all day. The
 * cap is what makes it survive a phone that slept through midnight: Android's timers do not advance
 * in deep sleep, so a single wait aimed at midnight can land late. It self-corrects within the cap.
 */
@Composable
fun rememberDayClock(zone: ZoneId = ZoneId.systemDefault()): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(zone) {
        while (true) {
            delay(millisUntilNextDay(System.currentTimeMillis(), zone).coerceAtMost(DAY_CLOCK_CHECK_MS))
            val t = System.currentTimeMillis()
            if (localDayOf(t, zone) != localDayOf(now, zone)) now = t
        }
    }
    return now
}

/**
 * The longest this clock ever sleeps: one minute. It is a CEILING on how late a date can be, not a
 * polling rate — the ordinary wait is "until midnight", and the ceiling only bites on a phone that
 * was asleep when midnight passed.
 *
 * ⚠ It is the whole reason a dozing phone is covered. Android's timers do not advance in deep
 * sleep, so a single wait aimed at midnight lands late on a phone that slept through it; a short
 * ceiling closes that gap without any lifecycle plumbing. It is NOT a recomposition every minute —
 * waking costs one clock read and one date comparison, and the screen is only told about a new time
 * on the one wake a day where the calendar day has actually turned over.
 */
const val DAY_CLOCK_CHECK_MS: Long = 60 * 1000L
