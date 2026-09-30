package com.vastufirst.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐⭐ THE PICTURE AND THE FINGER MUST AGREE.
 *
 * The plan is drawn letterboxed inside its box, and several features read that same rectangle: the
 * room highlights, the front-door mark, the tapped room's label, and every tap. The arithmetic is ONE
 * function, [planFit], and this is the test that keeps it honest.
 *
 * ⚠ Its zoom and pan cases went on 30 Sep 2026 with the pinch-to-zoom they tested: that lived only on
 * "Check what we read", the one screen that pinned the picture outside a scrolling page, and that screen
 * is gone. On the report the picture sits in the page's scroll and has never zoomed.
 *
 * Pure — no rendering, no Robolectric, no device.
 */
class PlanFitTest {

    private val boxW = 364f
    private val boxH = 320f

    /** A real portrait builder's sheet, the shape the owner once complained about. */
    private val tallW = 1256
    private val tallH = 2760

    /** A landscape sheet. */
    private val wideW = 1400
    private val wideH = 990

    @Test
    fun `the picture is centred and fits inside its box`() {
        val f = planFit(boxW, boxH, tallW, tallH)
        assertTrue("must fit horizontally", f.w <= boxW + 0.01f)
        assertTrue("must fit vertically", f.h <= boxH + 0.01f)
        // Centred: the margin on the left equals the margin on the right.
        assertEquals(f.ox, boxW - (f.ox + f.w), 0.01f)
        assertEquals(f.oy, boxH - (f.oy + f.h), 0.01f)
    }

    /**
     * ⚠ Asserted against numbers worked out by hand, not against the function's own output. A test
     * that only compares planFit with planFit passes for any consistent formula, including a wrong one.
     *
     * A 1256 × 2760 sheet in a 364 × 320 box: the height binds, so the scale is 320/2760 = 0.11594, and
     * the drawn width is 1256 × 0.11594 = 145.6.
     */
    @Test
    fun `the drawn size is the arithmetic a person would do on paper`() {
        val tall = planFit(boxW, boxH, tallW, tallH)
        assertEquals(145.6f, tall.w, 0.2f)
        assertEquals(320f, tall.h, 0.2f)
        // A wide sheet: 1400 × 990 in the same box — now the WIDTH binds, 364/1400 = 0.26, so 257.4 tall.
        val wide = planFit(boxW, boxH, wideW, wideH)
        assertEquals(364f, wide.w, 0.2f)
        assertEquals(257.4f, wide.h, 0.2f)
    }

    /**
     * ⭐ A tap inside a room's drawn rectangle must select that room: a real box walked through the SAME
     * conversion the tap reader uses, and `roomAtPoint` — the real one — asked what it finds.
     */
    @Test
    fun `a tap in a room's drawn box finds that room`() {
        val room = PlanRoom(
            id = "r1",
            type = com.vastufirst.shared.RoomType.KITCHEN,
            name = "Kitchen",
            box = com.vastufirst.shared.scan.ScanBox(x = 0.30, y = 0.55, w = 0.20, h = 0.15),
        )
        // ⚠ Held in a local rather than smart-cast off `room.box` — the property lives in another
        // compilation unit, so Kotlin will not smart-cast it after the null check.
        val b = requireNotNull(room.box)
        for ((w, h) in listOf(tallW to tallH, wideW to wideH)) {
            val f = planFit(boxW, boxH, w, h)
            val cx = f.ox + (b.x + b.w / 2).toFloat() * f.w
            val cy = f.oy + (b.y + b.h / 2).toFloat() * f.h
            val hit = roomAtPoint(listOf(room), (cx - f.ox) / f.w, (cy - f.oy) / f.h)
            assertEquals("the centre of the kitchen must select the kitchen on a $w × $h sheet", "r1", hit?.id)
        }
    }

    /** A zero-sized image must not divide by zero or hand back a garbage rectangle. */
    @Test
    fun `a picture with no size yields nothing rather than an infinity`() {
        val f = planFit(boxW, boxH, 0, 0)
        assertEquals(0f, f.w, 0.001f)
        assertEquals(0f, f.h, 0.001f)
    }

    /* ─────────────────────── what a screen reader is told ─────────────────────── */

    @Test
    fun `the description names the door when there is one`() {
        val withDoor = buildPlanDescription(selectedName = null, hasDoor = true)
        assertTrue("must mention the front door", withDoor.contains("front door", ignoreCase = true))
        assertFalse("must not offer a pinch the picture no longer answers", withDoor.contains("Pinch", ignoreCase = true))
    }

    @Test
    fun `the description says nothing about a door when there is none`() {
        val plain = buildPlanDescription(selectedName = null, hasDoor = false)
        assertFalse("must not promise a door mark that is not drawn", plain.contains("front door", ignoreCase = true))
    }

    @Test
    fun `the description names the selected room`() {
        val sel = buildPlanDescription(selectedName = "Kitchen", hasDoor = false)
        assertTrue(sel.contains("Kitchen"))
    }
}
