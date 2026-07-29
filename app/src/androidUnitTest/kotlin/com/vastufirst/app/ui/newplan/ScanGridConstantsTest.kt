package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.scan.ScanMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scan mapper lives in `:shared`, which is pure Kotlin and cannot see `:app`, so it MIRRORS the
 * editor's grid bounds rather than importing them — the same arrangement as `CellRect` mirroring
 * `GridRoom`. That mirror is only safe if something fails the build when the two drift, and this is
 * that something.
 *
 * ⚠ It matters more than a constant usually would: the mapper snaps rooms to a grid the editor then
 * has to hold. If `:app` lowered MAX_GRID and `:shared` did not, every scanned plan would arrive with
 * rooms hanging outside the plot — which is exactly the coordinate-space bug that cost v0.3.7.
 */
class ScanGridConstantsTest {

    @Test
    fun `the scan mapper and the editor agree on the grid bounds`() {
        assertEquals(MIN_GRID, ScanMapper.MIN_GRID, "MIN_GRID drifted between :app and :shared")
        assertEquals(MAX_GRID, ScanMapper.MAX_GRID, "MAX_GRID drifted between :app and :shared")
    }

    @Test
    fun `a scan draws on the finest grid the editor allows`() {
        // Deliberately NOT the editor's hand-drawing default of 8: measured on the recorded plan-01
        // read, an 8×8 grid rounds the toilet and the bath away entirely (both 0.1 of the plan deep).
        assertEquals(MAX_GRID, ScanMapper.DEFAULT_GRID)
        assertTrue(ScanMapper.DEFAULT_GRID > GRID, "scan should have more resolution than a finger does")
    }
}
