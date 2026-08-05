package com.vastufirst.app.ui.common

import java.time.Instant
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
