package com.vastufirst.app.ui.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScannedRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐⭐ A PLAN THAT CAPTIONS FIVE ROOMS THE SAME MUST NOT SPEAK FIVE IDENTICAL ROWS.
 *
 * **How this was found.** A real builder's sheet prints `5'-0" WIDE BALCONY` five times, states no
 * size for any of them, and captions three separate dressing areas `DRESS AREA`. Nothing in the app
 * had ever drawn it, so nothing had ever spoken it. The moment that plan became the fixture behind
 * the assisted screen, the accessibility pass reported duplicate speakable text — five rows saying
 * the identical sentence, on the one screen whose entire purpose is "tell us which of these we read
 * wrong".
 *
 * ⚠ THE VISIBLE CAPTION IS DELIBERATELY UNCHANGED, and this test does not ask for it to change.
 * Writing "Balcony 2" onto a row would be the app putting words on somebody's plan — `roomNamesById`
 * refuses to do exactly that, for exactly that reason. A position spoken aloud is a different thing:
 * it is where you are in a list, which a sighted reader already gets for free by looking down it.
 */
class DuplicateLabelOrdinalTest {

    private fun room(label: String, size: String = "", type: RoomType = RoomType.BALCONY) =
        ScannedRoom(type = type, label = label, rect = null, printedSize = size)

    @Test
    fun `a caption that stands alone is never numbered`() {
        val out = ordinalsForDuplicateLabels(
            listOf(room("KITCHEN", type = RoomType.KITCHEN), room("LIVING", type = RoomType.LIVING)),
        )
        assertTrue("nothing here repeats, so nothing should be numbered: $out", out.all { it == null })
    }

    @Test
    fun `a run of identical captions is counted, in the order the plan lists them`() {
        val out = ordinalsForDuplicateLabels(
            listOf(room("5'-0\" WIDE BALCONY"), room("5'-0\" WIDE BALCONY"), room("5'-0\" WIDE BALCONY")),
        )
        assertEquals(listOf("1 of 3", "2 of 3", "3 of 3"), out)
    }

    /**
     * ⚠ The printed size is part of what makes a room distinct. A sheet naming two bedrooms and
     * dimensioning them differently has already told them apart, and numbering those would put a
     * pointless "1 of 2" on the end of every row for a screen-reader user to sit through.
     */
    @Test
    fun `the same word at two different printed sizes is two different rooms`() {
        val out = ordinalsForDuplicateLabels(
            listOf(
                room("BEDROOM", "3300X3650", RoomType.BEDROOM),
                room("BEDROOM", "3000X3650", RoomType.BEDROOM),
            ),
        )
        assertNull(out[0])
        assertNull(out[1])
    }

    @Test
    fun `capitals and stray spaces do not hide a duplicate`() {
        val out = ordinalsForDuplicateLabels(listOf(room("Dress Area"), room("DRESS AREA "), room("dress area")))
        assertEquals(listOf("1 of 3", "2 of 3", "3 of 3"), out)
    }

    /**
     * ⭐ The real sheet, through the real mapper — so this cannot pass on an invented example while
     * the plan that caused it still speaks five identical rows.
     */
    @Test
    fun `the real unsized sheet gets its repeated captions told apart`() {
        val outcome = ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)
        val rooms = outcome.scannedRooms()
        val ordinals = ordinalsForDuplicateLabels(rooms)

        // The sentence the row actually speaks, built the same way the screen builds it.
        val spoken = rooms.mapIndexed { i, r ->
            "We read \"${r.label}\" as ${r.type}" + (ordinals[i]?.let { " ($it)" } ?: "") +
                (if (r.printedSize.isNotBlank()) ", ${r.printedSize}" else "")
        }
        assertEquals(
            "every row on this plan must say something a screen reader can tell apart",
            spoken.size,
            spoken.toSet().size,
        )
        assertTrue(
            "this fixture must actually contain repeated captions, or the test proves nothing",
            ordinals.any { it != null },
        )
    }
}
