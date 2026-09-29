package com.vastufirst.app.ui.common

import com.vastufirst.designsystem.components.wrappedPillRadiusPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐ A PILL WHOSE WORDS WRAP MUST NOT CUT ITS OWN FIRST LETTER (found by looking, 29 Sep 2026).
 *
 * At a 200 % font a room crossing into the centre read "orth-West · crosses the / entre", in its row
 * and on the plan: the pill kept fully round ends — half its height — and on three lines that curve
 * cut into the first letter of the top line and the bottom one. Every gate stayed green, because the
 * box was the right size and only the ink was cut.
 *
 * The numbers are the 200 % render's: caption lines 64 px tall, 8 px top padding, 16 px side padding,
 * three lines; the first letter's ink starts a few pixels inside its own box.
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST.
 */
class PillShapeTest {

    /** Is (x, y) inside the top-left corner of a rounded rectangle whose corners have radius [r]? */
    private fun insideTopLeftCorner(x: Float, y: Float, r: Float): Boolean {
        if (x >= r || y >= r) return true
        val dx = r - x
        val dy = r - y
        return dx * dx + dy * dy <= r * r
    }

    private val line = 64f
    private val padV = 8f
    private val padH = 16f
    private val inkX = padH + 2f
    private val inkY = padV + 4f

    @Test
    fun `a three-line pill with round ends cuts its first letter, and one with rounded corners does not`() {
        val height = 3 * line + 2 * padV
        assertFalse(
            "round ends (half the height) must be shown to cut the first letter — the bug this pins",
            insideTopLeftCorner(inkX, inkY, height / 2f),
        )
        assertTrue(
            "a wrapped pill's corner must keep the first letter whole",
            insideTopLeftCorner(inkX, inkY, wrappedPillRadiusPx(line, padV)),
        )
    }

    @Test
    fun `a wrapped pill's corner is exactly the round end of a one-line pill`() {
        // So a pill that wraps looks like the same pill, stretched — never a different shape.
        assertEquals("one line: half the height", (line + 2 * padV) / 2f, wrappedPillRadiusPx(line, padV), 0.001f)
    }
}
