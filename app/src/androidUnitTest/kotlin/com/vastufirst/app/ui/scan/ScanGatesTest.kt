package com.vastufirst.app.ui.scan

import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.ScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two gates in front of the network, tested rather than trusted.
 *
 * ⭐ Both exist because of the same mistake. v0.3.14 and v0.3.15 shipped a scan screen wired to a
 * stand-in reader that replayed four recorded readings in a loop; three of the four were the same
 * test plan, so every upload produced the same room list, and the screen gave no hint that the real
 * reader was not connected. The owner uploaded picture after picture at something that was never
 * going to look at them.
 *
 * So: a build that cannot read plans must be **visibly** unable to, and it must not so much as open a
 * file picker. That is what these assert. Everything here is synchronous on purpose — none of it
 * reaches `viewModelScope`, so it needs no dispatcher, no Robolectric and no network.
 */
class ScanGatesTest {

    /** Never called by these tests — a read would need a coroutine, which is the point. */
    private val neverCalled = object : PlanReader {
        override suspend fun read(image: ByteArray, imageAspect: Double?): ScanResult =
            error("no read should be attempted in these tests")
    }

    private val neverDecodes = object : ImageDecoder {
        override suspend fun toJpeg(source: Any): DecodedImage? =
            error("no decode should be attempted in these tests")
    }

    private fun vm(canRead: Boolean) =
        ScanViewModel(reader = neverCalled, decode = neverDecodes, canRead = canRead)

    @Test
    fun `a build without the reading key says so from the first frame`() {
        assertEquals(ScanUiState.NotConfigured, vm(canRead = false).state)
    }

    @Test
    fun `a build without the reading key refuses to start a read at all`() {
        val model = vm(canRead = false)
        // Whatever the picker hands back, nothing is decoded and nothing is sent. Both fakes above
        // throw if touched, so this passing means the read path was never entered.
        model.scan("content://some/plan.pdf")
        assertEquals(ScanUiState.NotConfigured, model.state)
        model.retrySameImage()
        assertEquals(ScanUiState.NotConfigured, model.state)
        model.reset()
        assertEquals(ScanUiState.NotConfigured, model.state)
    }

    @Test
    fun `a build with the key opens on the ask, not on an excuse`() {
        val model = vm(canRead = true)
        assertEquals(ScanUiState.Idle, model.state)
        // A cancelled picker returns null and must land back on the ask, not on an error.
        model.scan(null)
        assertEquals(ScanUiState.Idle, model.state)
    }

    @Test
    fun `consent starts switched off and survives being switched back off`() {
        // A fresh install has agreed to nothing. The scan route reads exactly this before deciding
        // whether the scanner or the explanation screen comes next.
        val consent = InMemoryPlanReadingConsent()
        assertFalse(consent.isGranted(), "a fresh install must not be treated as having consented")

        consent.set(true)
        assertTrue(consent.isGranted())
        // Withdrawable, which is what makes it consent (DPDP, NFR §10).
        consent.set(false)
        assertFalse(consent.isGranted())
    }

    /**
     * ⭐ The size the plan printed, shortened enough to sit on one caption line beside the room name.
     *
     * ⚠ Not cosmetic. That line repeats once per room on the tallest screen in the app, and Indian
     * sheets print the same measurement twice — metric then imperial. Left whole, fifteen rooms of
     * `3.72m X 4.50m ( 12'-2" x 14'-9" )` wrap to several lines each at 200 % font on a 320 dp phone
     * and push the button that leaves the screen out of reach, which is a defect this project has
     * shipped before.
     */
    @Test
    fun `the printed size loses its repeat but never its measurement`() {
        assertEquals(
            "3.72m X 4.50m",
            shortSize("3.72m X 4.50m ( 12'-2\" x 14'-9\" )"),
            "the bracketed repeat of the same measurement is dropped",
        )
        // A sheet that prints only one system keeps all of it.
        assertEquals("10'-0\"X10'-6\"", shortSize("10'-0\"X10'-6\""), "nothing to drop, nothing dropped")
        // ⚠ A compound space — an L-shaped utility — OPENS with a bracket, so the brackets are the
        // measurement rather than a repeat of it. Trimming there would leave an empty string, which
        // is the worst outcome: a room silently showing no size at all.
        val compound = "(3.17mX0.90m)+(1.51mX0.40m) (10'-5\"X3'-0\")+(5'-0\"X1'-4\")"
        assertEquals(compound, shortSize(compound), "a compound size must survive whole")
        assertEquals("", shortSize(""), "a plan that prints no size shows none")
    }
}
