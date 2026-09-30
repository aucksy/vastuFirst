package com.vastufirst.app.navigation

import org.junit.Test
import kotlin.test.assertTrue

/**
 * ⭐⭐ "CHANGE NORTH" ON A SCANNED HOME'S REPORT MUST SHOW THE READER'S OWN PLAN.
 *
 * Found on 30 Sep 2026 by reading the route. The owner's rule since 6 Aug 2026 is that North is
 * marked "only on this actual floor plan" — the photograph the reader took. The flow did that. But the
 * report's "Change North" opened the same dial WITHOUT the photograph, so a reader correcting North on
 * their finished report was handed our redrawn coloured squares instead of their own plan: the one
 * screen the photo flow was built to keep them away from.
 *
 * The photograph in the hand-over slot always belongs to the home on screen (every door into a
 * different home empties it), so the dial opened from that home's own report may draw it.
 */
class NorthPhotoRuleTest {

    @Test
    fun changingNorthFromTheReportShowsTheHomesOwnPlan() {
        assertTrue(
            northShowsPhoto(fromReport = true, fromDraft = false),
            "Opened from a scanned home's report, the North dial must draw that home's own photograph.",
        )
    }

    @Test
    fun anUnfinishedScannedHomeStillShowsItsPlan() {
        assertTrue(
            northShowsPhoto(fromReport = false, fromDraft = true),
            "\"Carry on\" with a scanned home must still draw its own photograph on the North dial.",
        )
    }
}
