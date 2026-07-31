package com.vastufirst.app.ui.grid

import com.vastufirst.app.ui.common.label
import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐ The owner's report: *"Toilet is not built correctly to show the word Toilet."*
 *
 * His flat's second toilet is 4'-11" wide in a home about 21 ft across, so on a ten-step grid it is
 * one step — 26–34 dp on a real phone — and the tile ellipsised the name to "To…". These pin the
 * rungs that replace that: full name across, full name turned on its side the way a plan labels a
 * narrow room, the short word, and only then nothing.
 *
 * ⚠ `:app` tests are JUnit, so the message is the FIRST argument. (`:shared` is kotlin.test, where it
 * is the last — getting it backwards binds to the Double overload and fails to compile.)
 */
class RoomTileLabelTest {

    /** A stand-in for the real text measurer: monospace-ish, so every expectation is arithmetic. */
    private fun measurer(charPx: Float, linePx: Float): (String) -> Pair<Float, Float> =
        { text -> text.length * charPx to linePx }

    private val normal = measurer(charPx = 9f, linePx = 18f)

    /** 200 % font scale: every glyph and the line box double. */
    private val huge = measurer(charPx = 18f, linePx = 36f)

    @Test
    fun `a room with room for its name simply prints it`() {
        val got = chooseTileLabel("Toilet", "WC", widthPx = 120f, heightPx = 90f, measure = normal)
        assertEquals(TileLabel("Toilet", rotated = false), got)
    }

    @Test
    fun `⭐ a one-cell-wide toilet turns its name on its side rather than truncating it`() {
        // One cell across (34 dp ≈ 90 px at mdpi-ish), three deep. "Toilet" is 54 px long and needs
        // 18 px of line — across it does not fit in 30, down it fits easily in 270.
        val got = chooseTileLabel("Toilet", "WC", widthPx = 30f, heightPx = 270f, measure = normal)
        assertEquals("the full word must survive, turned rather than cut", TileLabel("Toilet", true), got)
    }

    @Test
    fun `a turned label is refused when the line itself is wider than the room`() {
        // The trap this exists for: at 200 % font a line box is 36 px tall, and a one-cell room is
        // 30 px across — so turning "Toilet" would draw it hanging out of its own tile. It must fall
        // to the short word, which fits across at 2 characters × 18 = 36… also too wide, so nothing.
        val got = chooseTileLabel("Toilet", "WC", widthPx = 30f, heightPx = 270f, measure = huge)
        assertNull("a label that cannot fit either way must be left out, never overflowed", got)
    }

    @Test
    fun `the short word rescues a room the full name cannot fit`() {
        // 44 px across: "Toilet" is 54 and will not go, "WC" is 18 and will.
        val got = chooseTileLabel("Toilet", "WC", widthPx = 44f, heightPx = 40f, measure = normal)
        assertEquals(TileLabel("WC", rotated = false), got)
    }

    @Test
    fun `a wide shallow room never turns its label`() {
        // Rotation is a narrow-room convention. In a room wider than it is tall it reads as a bug.
        // 60 px across takes "Court" (45) but not "Courtyard" (81); 20 px deep leaves no length
        // to turn into, and the room is wider than it is tall, so turning is not offered anyway.
        val got = chooseTileLabel("Courtyard", "Court", widthPx = 60f, heightPx = 20f, measure = normal)
        assertEquals("a wide room may shorten, but must not rotate", TileLabel("Court", false), got)
    }

    @Test
    fun `a single cell with room for nothing prints nothing, as before`() {
        assertNull(chooseTileLabel("Bedroom", "Bed", widthPx = 20f, heightPx = 20f, measure = normal))
    }

    @Test
    fun `a zero-sized tile is not a crash`() {
        assertNull(chooseTileLabel("Bedroom", "Bed", widthPx = 0f, heightPx = 40f, measure = normal))
        assertNull(chooseTileLabel("Bedroom", "Bed", widthPx = 40f, heightPx = 0f, measure = normal))
    }

    @Test
    fun `the full name is always preferred to the short one where both fit`() {
        val got = chooseTileLabel("Bedroom", "Bed", widthPx = 200f, heightPx = 60f, measure = normal)
        assertEquals(TileLabel("Bedroom", false), got)
    }

    @Test
    fun `every room kind has a short form no longer than its full name`() {
        for (t in RoomType.entries) {
            val full = t.label()
            val short = t.shortLabel()
            assertTrue(
                "${t.name}: short form \"$short\" is longer than the full name \"$full\"",
                short.length <= full.length,
            )
            assertTrue("${t.name}: short form is blank", short.isNotBlank())
        }
    }

    @Test
    fun `⭐ the short forms are real words, not invented abbreviations`() {
        // The rule this encodes: a shorter label must be something a person says out loud. Where no
        // such word exists the full name is kept and the tile relies on turning it or leaving it out.
        assertEquals("WC", RoomType.TOILET.shortLabel())        // how Indian plans print it
        assertEquals("Bath", RoomType.BATHROOM.shortLabel())
        assertEquals("Bed", RoomType.BEDROOM.shortLabel())
        assertEquals("Entry", RoomType.ENTRANCE.shortLabel())
        assertEquals("Court", RoomType.COURTYARD.shortLabel())
        // No natural short form: unchanged, so the rung is simply skipped.
        assertEquals("Kitchen", RoomType.KITCHEN.shortLabel())
        assertEquals("Corridor", RoomType.CORRIDOR.shortLabel())
    }
}
