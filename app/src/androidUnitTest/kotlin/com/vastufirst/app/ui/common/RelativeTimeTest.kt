package com.vastufirst.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure formatter test (no Robolectric needed — java.time only). A fixed `now` and UTC zone keep it
 * deterministic; `days` are whole calendar days, matching what the user perceives.
 *
 * ⚠ These strings are deliberately prefix-free ("3 days ago", never "Updated 3 days ago") — the
 * prefix is what cut the date to "Updated 3 d…" on a home row at 200 % font (the stated blemish of
 * v0.7.4). Every caller supplies its own context before a "·". If a prefix ever creeps back in,
 * this test failing IS the feature.
 */
class RelativeTimeTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // 2026-07-15, midday UTC — the reference "now".
    private val now: Long = LocalDate.of(2026, 7, 15).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, hour: Int = 9): Long =
        LocalDate.of(y, m, d).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()

    @Test fun today() {
        assertEquals("today", relativeUpdated(at(2026, 7, 15, hour = 1), now, zone))
    }

    @Test fun yesterday() {
        assertEquals("yesterday", relativeUpdated(at(2026, 7, 14), now, zone))
    }

    @Test fun a_few_days_ago() {
        assertEquals("3 days ago", relativeUpdated(at(2026, 7, 12), now, zone))
    }

    @Test fun six_days_is_still_counted() {
        assertEquals("6 days ago", relativeUpdated(at(2026, 7, 9), now, zone))
    }

    @Test fun a_week_or_more_shows_the_date() {
        assertEquals("8 Jul", relativeUpdated(at(2026, 7, 8), now, zone))
    }

    @Test fun a_different_year_includes_the_year() {
        assertEquals("20 Dec 2025", relativeUpdated(at(2025, 12, 20), now, zone))
    }

    @Test fun a_clock_skewed_future_stamp_reads_as_today_not_negative() {
        assertEquals("today", relativeUpdated(at(2026, 7, 16), now, zone))
    }

    // --- the wait that keeps "today" honest overnight -------------------------------------------
    //
    // Every bucket above turns over at local midnight, so the screen only has to be told once a day
    // — at midnight. These fix that arithmetic. The clock that USES them lives in the stateful
    // screen and is deliberately untested here: composing it would hang the harness.

    @Test fun the_wait_runs_to_the_next_local_midnight() {
        // Midday, so half a day is left.
        assertEquals(12 * 60 * 60 * 1000L, millisUntilNextDay(now, zone))
    }

    @Test fun one_minute_before_midnight_waits_one_minute() {
        val almost = at(2026, 7, 15, hour = 23) + 59 * 60 * 1000L
        assertEquals(60 * 1000L, millisUntilNextDay(almost, zone))
    }

    @Test fun exactly_on_midnight_waits_a_whole_day_never_zero() {
        // ⚠ The guard that matters. Answering 0 here would spin the waiting loop as fast as the
        // phone allows — a flat battery in place of a stale date.
        val midnight = LocalDate.of(2026, 7, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(24 * 60 * 60 * 1000L, millisUntilNextDay(midnight, zone))
    }

    @Test fun the_wait_is_never_zero_or_negative_across_a_whole_day() {
        var t = LocalDate.of(2026, 7, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = t + 24 * 60 * 60 * 1000L
        while (t < end) {
            assertTrue("non-positive wait at $t", millisUntilNextDay(t, zone) > 0L)
            t += 37 * 60 * 1000L      // a stride that is not a factor of an hour
        }
    }

    @Test fun a_day_is_read_in_the_zone_it_is_asked_about() {
        // 23:30 UTC on 15 July is already the 16th in Delhi. Two zones, two calendar days, one
        // instant — which is exactly why the date shown must never be computed in a fixed zone.
        val late = at(2026, 7, 15, hour = 23) + 30 * 60 * 1000L
        assertEquals(LocalDate.of(2026, 7, 15), localDayOf(late, zone))
        assertEquals(LocalDate.of(2026, 7, 16), localDayOf(late, ZoneId.of("Asia/Kolkata")))
    }

    @Test fun a_short_day_from_the_clocks_going_forward_is_still_handled() {
        // 29 March 2026 is the day British clocks go forward, so it is 23 hours long, not 24.
        // Asking java.time for the start of the next day — rather than adding 24 hours to this one
        // — is what makes the wait come out right, and it is the only reason this is worth a test.
        val london = ZoneId.of("Europe/London")
        val shortDayStart = LocalDate.of(2026, 3, 29).atStartOfDay(london).toInstant().toEpochMilli()
        assertEquals(23 * 60 * 60 * 1000L, millisUntilNextDay(shortDayStart, london))
    }

    @Test fun a_long_day_from_the_clocks_going_back_is_still_handled() {
        // …and 25 October 2026 is 25 hours long. The same call handles both without knowing either.
        val london = ZoneId.of("Europe/London")
        val longDayStart = LocalDate.of(2026, 10, 25).atStartOfDay(london).toInstant().toEpochMilli()
        assertEquals(25 * 60 * 60 * 1000L, millisUntilNextDay(longDayStart, london))
    }
}
