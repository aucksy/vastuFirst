package com.vastufirst.app.ui.scan

import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.shared.scan.FakePlanReader
import com.vastufirst.shared.scan.SampleScan
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐⭐ "TRY A SAMPLE PLAN" WALKS THE SCAN JOURNEY ON A REAL PLAN PICTURE (owner, 15 Oct 2026: option B).
 *
 * The sample used to be a home drawn on the grid, so the first thing a new user tried showed drawn room
 * boxes where a scanned home shows its photograph. It is now the bundled sample sheet, put on the
 * ordinary scan result with its recorded reading — no read, no key, no network.
 */
class SampleJourneyTest {

    private val noDecoder = object : ImageDecoder {
        override suspend fun toJpeg(source: Any): DecodedImage? = null
    }

    @Test
    fun `the sample opens on the scan result with its photo even in a build that cannot read`() {
        val vm = ScanViewModel(reader = FakePlanReader(), decode = noDecoder, canRead = false)
        vm.showSample()
        val done = vm.state as ScanUiState.Done
        assertTrue("the sample must be a PLACED home", done.outcome is ScanOutcome.Placed)
        assertNotNull("the sample's photo goes on to North, the door and the report", vm.lastImage)
        assertEquals(SampleScan.WIDTH.toDouble() / SampleScan.HEIGHT, vm.lastImage!!.aspect!!, 1e-9)
    }

    @Test
    fun `the sample's front door is on the bottom wall where the sheet draws it`() {
        val outcome = SampleScan.outcome()!!
        val door = doorForPhotoTap(SampleScan.DOOR_PAGE_X, SampleScan.DOOR_PAGE_Y, outcome.rooms)
        assertEquals(DoorSide.S, door?.side)
    }
}
