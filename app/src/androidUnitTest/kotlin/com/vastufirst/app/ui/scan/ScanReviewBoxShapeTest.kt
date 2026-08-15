package com.vastufirst.app.ui.scan

import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.RoomDimensions
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Test
import kotlin.test.assertTrue

/**
 * ⭐⭐ THE BOX DRAWN ON THE PHOTOGRAPH IS THE READER'S OWN RECTANGLE — never re-shaped from the
 * size the caption prints.
 *
 * **What the owner saw** (15 Aug 2026): he photographed "Check what we read", tapped a toilet, and
 * said the highlight did not match the room. It did not. Between 10 and 15 August every box was
 * redrawn at the size its own caption states, at the sheet's fitted scale, about the reader's
 * centre. The reasoning behind that was sound and is still right for the GRID — the reader
 * transcribes printed text at about 95 % and guesses rectangles at 40–70 %, so where the two
 * disagree about the HOME, the text wins.
 *
 * It was wrong for the PICTURE, because **a builder's sheet is not always drawn to its own
 * captions**, and this box is drawn on the sheet. His Gurgaon plan prints its second toilet
 * 8'0" x 5'5" and draws it taller than wide; his Green Court plan prints its kitchen 2920 x 2100
 * and draws it neither. Marked by hand against both sheets and scored room by room
 * (`tools/scan-eval/truth-rooms.json`, `exp-truth-boxes.py`), the caption-shaped box covered its
 * own room 56 % on that toilet and 51 % on that kitchen; the reader's rectangle covers them 75 %
 * and 95 %.
 *
 * **This test exists to stop it being re-applied by someone who reads the 95 % and not the sheet.**
 * It asserts the drawn box is the read box, and it prints, for every bundled plan, how many rooms
 * the caption would turn ninety degrees away from the way the sheet draws them. Across every
 * recorded read from the reader we ship, that is one room in three.
 *
 * Every line is marked so CI lifts it into the build log, where the numbers can be read.
 */
class ScanReviewBoxShapeTest {

    private fun say(line: String) = println("SCORE| $line")

    @Test
    fun `the drawn box is the box that was read, and the caption never turns it`() {
        say("")
        say("═══ ON-PHOTO ROOM BOXES — drawn exactly as the reader read them ═══")
        say("turned = rooms whose CAPTION says wide where the sheet DRAWS tall, or the other way.")
        say("Each one is a highlight lying across the room it names. That is why the caption is")
        say("not allowed to shape this box, only the grid. See ScannedRoom.source.")
        say("")
        say("plan".padEnd(26) + "rooms".padEnd(7) + "sized".padEnd(7) + "turned by the caption")
        say("-".repeat(74))

        var measured = 0
        var reshaped = 0
        var decoupled = 0
        var totalSized = 0
        var totalTurned = 0

        for (id in RecordedScans.ids) {
            val reply = RecordedScans.load(id)?.reply ?: continue
            val placed = ScanMapper.map(reply, imageAspect = 1.0) as? ScanOutcome.Placed ?: continue
            measured++

            // ⭐ The invariant. `planRoomsOf` is what the review screen draws, and `source` is what
            // `doorForPhotoTap` places the front door through. Asserting they are the SAME object
            // is what stops the two ever disagreeing again: a picture that contradicts the door is
            // a picture of a home nobody scored.
            val drawn = planRoomsOf(placed.rooms)
            for (i in placed.rooms.indices) {
                val box = drawn[i].box
                if (box !== placed.rooms[i].source) {
                    say("$id: ${placed.rooms[i].label} is drawn from something other than the box we read")
                    reshaped++
                }
            }
            if (drawn.size != placed.rooms.size) decoupled++

            var sized = 0
            var turned = 0
            for (room in placed.rooms) {
                val printed = RoomDimensions.parse(room.printedSize.ifBlank { room.label }) ?: continue
                val box = room.source ?: continue
                if (printed.widthMm == printed.depthMm || box.w == box.h) continue
                sized++
                if ((printed.widthMm > printed.depthMm) != (box.w > box.h)) turned++
            }
            totalSized += sized
            totalTurned += turned
            say(id.padEnd(26) + placed.rooms.size.toString().padEnd(7) + sized.toString().padEnd(7) +
                (if (sized == 0) "—" else "$turned of $sized"))
        }

        say("")
        say("$measured plans · the caption would turn $totalTurned of $totalSized dimensioned rooms")
        say("across the way their own sheet draws them")
        say("")

        assertTrue(measured >= 4, "only $measured recorded plans were measured — the corpus is not being read")
        assertTrue(decoupled == 0, "$decoupled plan(s) drew a different number of rooms than were placed")
        assertTrue(
            reshaped == 0,
            "$reshaped room(s) are drawn from a rectangle other than the one the reader returned. " +
                "The on-photo box has been re-shaped again — read the note on ScannedRoom.source " +
                "before undoing this; it costs the owner's toilet 19 points of its own room.",
        )
    }
}
