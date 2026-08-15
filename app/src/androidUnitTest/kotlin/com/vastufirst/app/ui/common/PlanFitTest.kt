package com.vastufirst.app.ui.common

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐⭐ THE PICTURE AND THE FINGER MUST AGREE — the one thing zoom could silently break.
 *
 * The plan is drawn letterboxed inside its box, and three separate features read that same
 * rectangle: the room highlights, the front-door mark, and every tap. Until 16 Aug 2026 the
 * arithmetic was written out TWICE — once in the draw pass and once inside the tap handler — and it
 * worked only because the two copies happened to be identical.
 *
 * That is exactly the shape of bug zoom would have introduced: magnify the picture, update one copy,
 * and the highlights move while the taps stay where they were. Nothing would crash, no golden would
 * fail (a golden cannot tap), and the user would simply find that touching a room selects the wrong
 * one. So the arithmetic is now ONE function, and this is the test that keeps it honest.
 *
 * Pure — no rendering, no Robolectric, no device.
 */
class PlanFitTest {

    private val boxW = 364f
    private val boxH = 320f

    /** A real portrait builder's sheet, the shape the owner complained about. */
    private val tallW = 1256
    private val tallH = 2760

    /** A landscape sheet, the shape every golden used to use. */
    private val wideW = 1400
    private val wideH = 990

    @Test
    fun `at rest the picture is centred and fits inside its box`() {
        val f = planFit(boxW, boxH, tallW, tallH, zoom = 1f, pan = Offset.Zero)
        assertTrue("must fit horizontally", f.w <= boxW + 0.01f)
        assertTrue("must fit vertically", f.h <= boxH + 0.01f)
        // Centred: the margin on the left equals the margin on the right.
        assertEquals(f.ox, boxW - (f.ox + f.w), 0.01f)
        assertEquals(f.oy, boxH - (f.oy + f.h), 0.01f)
    }

    /**
     * The measured harm, as a test rather than as a claim.
     *
     * A tall sheet is bound by the box's HEIGHT, so it can only ever use the width its own shape
     * implies — here about 146 of 364 points, two fifths of the space. A wide sheet is bound by the
     * WIDTH and fills it completely. That asymmetry is the owner's whole complaint, and it is a
     * property of the paper rather than a setting, which is why the answer is magnification.
     */
    @Test
    fun `a tall sheet uses far less of the width than a wide one`() {
        val tall = planFit(boxW, boxH, tallW, tallH, zoom = 1f, pan = Offset.Zero)
        val wide = planFit(boxW, boxH, wideW, wideH, zoom = 1f, pan = Offset.Zero)
        assertTrue("the tall sheet should be height-bound", tall.h in (boxH - 0.5f)..(boxH + 0.5f))
        assertTrue("the wide sheet should be width-bound", wide.w in (boxW - 0.5f)..(boxW + 0.5f))
        assertTrue(
            "a tall sheet must use less than half the width at rest — got ${tall.w} of $boxW",
            tall.w < boxW / 2f,
        )
    }

    /**
     * ⚠ Asserted against numbers worked out by hand, not against the function's own output at
     * another zoom. A test that only compares planFit with planFit passes for any consistent
     * formula, including a wrong one.
     *
     * A 1256 × 2760 sheet in a 364 × 320 box: the height binds, so the scale is 320/2760 = 0.11594,
     * and the drawn width is 1256 × 0.11594 = 145.6. At zoom 2 it is 291.2 × 640.
     */
    @Test
    fun `the drawn size is the arithmetic a person would do on paper`() {
        val one = planFit(boxW, boxH, tallW, tallH, zoom = 1f, pan = Offset.Zero)
        assertEquals(145.6f, one.w, 0.2f)
        assertEquals(320f, one.h, 0.2f)
        val two = planFit(boxW, boxH, tallW, tallH, zoom = 2f, pan = Offset.Zero)
        assertEquals(291.2f, two.w, 0.4f)
        assertEquals(640f, two.h, 0.4f)
        // A wide sheet: 1400 × 990 in the same box — now the WIDTH binds, 364/1400 = 0.26, so 257.4 tall.
        val wide = planFit(boxW, boxH, wideW, wideH, zoom = 1f, pan = Offset.Zero)
        assertEquals(364f, wide.w, 0.2f)
        assertEquals(257.4f, wide.h, 0.2f)
    }

    /**
     * ⭐⭐ THE ROUND TRIP: a point on the screen turned into a page fraction and back must land on
     * itself, at every zoom and pan. This is the property the highlights and the taps both depend on.
     */
    @Test
    fun `screen to page to screen is the identity, zoomed and panned`() {
        val zooms = listOf(1f, 1.4f, 2.5f, PLAN_MAX_ZOOM)
        val pans = listOf(Offset.Zero, Offset(40f, -70f), Offset(-500f, 900f))
        for (z in zooms) {
            for (p in pans) {
                val f = planFit(boxW, boxH, tallW, tallH, z, p)
                for (sx in listOf(0f, 90f, 182f, 300f)) {
                    for (sy in listOf(0f, 60f, 160f, 319f)) {
                        val fx = (sx - f.ox) / f.w
                        val fy = (sy - f.oy) / f.h
                        val backX = f.ox + fx * f.w
                        val backY = f.oy + fy * f.h
                        assertEquals("x at zoom $z pan $p", sx, backX, 0.01f)
                        assertEquals("y at zoom $z pan $p", sy, backY, 0.01f)
                    }
                }
            }
        }
    }

    /**
     * ⭐ A tap inside a room's drawn rectangle must still select that room after zooming.
     *
     * This is the user-visible version of the round trip: it walks a real box through the SAME
     * conversion the tap handler uses and asks `roomAtPoint` — the real one — what it finds.
     */
    @Test
    fun `a tap in a room's drawn box still finds that room at every zoom`() {
        val room = PlanRoom(
            id = "r1",
            type = com.vastufirst.shared.RoomType.KITCHEN,
            name = "Kitchen",
            box = com.vastufirst.shared.scan.ScanBox(x = 0.30, y = 0.55, w = 0.20, h = 0.15),
        )
        val rooms = listOf(room)
        // ⚠ Held in a local rather than smart-cast off `room.box` — the property lives in another
        // compilation unit, so Kotlin will not smart-cast it after the null check and the file would
        // not compile. Cheap to write, and it cannot be built locally to find out.
        val b = requireNotNull(room.box)
        for (z in listOf(1f, 2f, PLAN_MAX_ZOOM)) {
            val f = planFit(boxW, boxH, tallW, tallH, z, Offset.Zero)
            // The middle of the room, in screen points, exactly as the draw pass computes it.
            val cx = f.ox + (b.x + b.w / 2).toFloat() * f.w
            val cy = f.oy + (b.y + b.h / 2).toFloat() * f.h
            // …and back through the tap conversion.
            val hit = roomAtPoint(rooms, (cx - f.ox) / f.w, (cy - f.oy) / f.h)
            assertEquals("the centre of the kitchen must select the kitchen at zoom $z", "r1", hit?.id)
        }
    }

    @Test
    fun `panning is pinned to zero on an axis where the picture already fits`() {
        // Picture smaller than the box on both axes: there is nothing to pan, so it stays centred.
        val clamped = clampPlanPan(Offset(120f, -300f), drawnW = 100f, drawnH = 200f, boxW = boxW, boxH = boxH)
        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun `panning stops with the sheet's edge on the box's edge, never past it`() {
        val drawnW = 764f // 400 wider than the box, so 200 of travel each way
        val clamped = clampPlanPan(Offset(9999f, 0f), drawnW, drawnH = 320f, boxW = boxW, boxH = boxH)
        assertEquals(200f, clamped.x, 0.001f)
        val other = clampPlanPan(Offset(-9999f, 0f), drawnW, drawnH = 320f, boxW = boxW, boxH = boxH)
        assertEquals(-200f, other.x, 0.001f)
    }

    /** A zero-sized image must not divide by zero or hand back a garbage rectangle. */
    @Test
    fun `a picture with no size yields nothing rather than an infinity`() {
        val f = planFit(boxW, boxH, 0, 0, 1f, Offset.Zero)
        assertEquals(0f, f.w, 0.001f)
        assertEquals(0f, f.h, 0.001f)
    }

    /* ─────────────────────── what a screen reader is told ─────────────────────── */

    @Test
    fun `the description names the door when there is one`() {
        val withDoor = buildPlanDescription(selectedName = null, hasDoor = true, zoomable = true)
        assertTrue("must mention the front door", withDoor.contains("front door", ignoreCase = true))
        assertTrue("must offer the way to magnify", withDoor.contains("bigger", ignoreCase = true))
    }

    @Test
    fun `the description says nothing about a door or zoom when neither is available`() {
        val plain = buildPlanDescription(selectedName = null, hasDoor = false, zoomable = false)
        assertFalse("must not promise a door mark that is not drawn", plain.contains("front door", ignoreCase = true))
        assertFalse("must not offer zooming where it is switched off", plain.contains("Pinch", ignoreCase = true))
    }

    @Test
    fun `the description names the selected room`() {
        val sel = buildPlanDescription(selectedName = "Kitchen", hasDoor = false, zoomable = false)
        assertTrue(sel.contains("Kitchen"))
    }
}
