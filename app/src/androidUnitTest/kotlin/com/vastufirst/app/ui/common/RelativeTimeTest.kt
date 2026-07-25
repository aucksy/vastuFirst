package com.vastufirst.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure formatter test (no Robolectric needed — java.time only). A fixed `now` and UTC zone keep it
 * deterministic; `days` are whole calendar days, matching what the user perceives.
 */
class RelativeTimeTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // 2026-07-15, midday UTC — the reference "now".
    private val now: Long = LocalDate.of(2026, 7, 15).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, hour: Int = 9): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()

    @Test fun today() {
        assertEquals("Updated today", relativeUpdated(at(2026, 7, 15, hour = 1), now, zone))
    }

    @Test fun yesterday() {
        assertEquals("Updated yesterday", relativeUpdated(at(2026, 7, 14), now, zone))
    }

    @Test fun a_few_days_ago() {
        assertEquals("Updated 3 days ago", relativeUpdated(at(2026, 7, 12), now, zone))
    }

    @Test fun six_days_is_still_counted() {
        assertEquals("Updated 6 days ago", relativeUpdated(at(2026, 7, 9), now, zone))
    }

    @Test fun a_week_or_more_shows_the_date() {
        assertEquals("Updated on 8 Jul", relativeUpdated(at(2026, 7, 8), now, zone))
    }

    @Test fun a_different_year_includes_the_year() {
        assertEquals("Updated on 20 Dec 2025", relativeUpdated(at(2025, 12, 20), now, zone))
    }

    @Test fun a_clock_skewed_future_stamp_reads_as_today_not_negative() {
        assertEquals("Updated today", relativeUpdated(at(2026, 7, 16), now, zone))
    }
}
