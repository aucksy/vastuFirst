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
 *   same day    → "Updated today"
 *   1 day ago   → "Updated yesterday"
 *   2..6 days   → "Updated N days ago"
 *   older       → "Updated on 3 Jul" (or "3 Jul 2025" if a different year)
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
        days <= 0L -> "Updated today"          // same day (or a clock-skewed future stamp)
        days == 1L -> "Updated yesterday"
        days < 7L -> "Updated $days days ago"
        else -> {
            val pattern = if (then.year == today.year) "d MMM" else "d MMM yyyy"
            "Updated on " + then.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
}
