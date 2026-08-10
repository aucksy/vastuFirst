package com.vastufirst.app.ui.scan

import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.RoomDimensions
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * ⭐⭐ THE ON-PHOTO BOXES ARE DRAWN AT THE SIZE THE PLAN PRINTS — and the front door did not move.
 *
 * **What the owner saw** (10 Aug 2026): *"almost every time the boxes we are placing over the rooms
 * are slightly bigger than actual rooms."* He was right, and the cause was not in our drawing code:
 * the review screen draws the reader's own rectangle. Measured over 330 room dimensions from 16
 * recorded live readings, the reader adds a roughly CONSTANT margin of about five inches to every
 * wall — +7 % on a wall under 5 ft, −3 % on one over 12 ft. Small rooms are disfigured; big ones are
 * if anything slightly small. [ScanMapper.tightenToPrinted] replaces the guessed extent with the
 * printed one and keeps the reader's centre.
 *
 * **Why the second half of this test matters more than the first.** These same boxes stopped being
 * decoration on 6 August, when the front door moved onto the photograph: [doorForPhotoTap] maps a
 * finger through the UNION of them. The door is the highest-weighted element the engine scores, so a
 * change to these boxes can change a score. The owner's instruction was explicit — *"I cant have you
 * messing up what is already working"* — so this runs every recorded plan BOTH ways over a grid of
 * taps and compares the door that comes out. It is measurement, not argument.
 *
 * Every line is marked so CI lifts it into the build log, where the numbers can actually be read.
 */
class ScanTintPrintedSizeTest {

    private fun say(line: String) = println("SCORE| $line")

    private fun placed(id: String, tighten: Boolean): ScanOutcome.Placed? =
        RecordedScans.load(id)?.reply
            ?.let { ScanMapper.map(it, imageAspect = 1.0, tightenTints = tighten) }
            as? ScanOutcome.Placed

    /**
     * How far a plan's sized rooms disagree about the sheet's scale, as a fraction.
     *
     * Every room on one sheet is drawn at one scale, so `box ÷ printed size` must come out the same
     * for all of them. The SPREAD of that ratio is therefore a direct, truth-free measure of how
     * wrong the boxes are relative to each other — 0 means every sized room is in exact proportion.
     */
    private fun scaleSpread(rooms: List<ScannedRoom>): Double? {
        val ratios = rooms.mapNotNull { r ->
            val box = r.printedBox ?: r.source ?: return@mapNotNull null
            val printed = RoomDimensions.parse(r.printedSize.ifBlank { r.label }) ?: return@mapNotNull null
            if (box.w <= 0.0 || box.h <= 0.0 || printed.widthMm <= 0.0 || printed.depthMm <= 0.0) {
                return@mapNotNull null
            }
            val long = maxOf(box.w, box.h) / maxOf(printed.widthMm, printed.depthMm)
            val short = minOf(box.w, box.h) / minOf(printed.widthMm, printed.depthMm)
            listOf(long, short)
        }.flatten()
        if (ratios.size < 4) return null
        val mean = ratios.average()
        if (mean <= 0.0) return null
        return ratios.maxOf { abs(it - mean) } / mean
    }

    @Test
    fun `the tint is drawn at the printed size, and the front door does not move`() {
        say("")
        say("═══ ON-PHOTO ROOM BOXES — drawn at the printed size ═══")
        say("spread = how far a sheet's sized rooms disagree about its scale. 0 = exact proportion.")
        say("door   = taps (of 361 across the page) that place a DIFFERENT front door than before.")
        say("")
        say("plan".padEnd(26) + "rooms".padEnd(7) + "sized".padEnd(7) + "spread before".padEnd(15) + "after".padEnd(9) + "door moved")
        say("-".repeat(80))

        var measured = 0
        var totalMoved = 0
        var worsened = 0

        for (id in RecordedScans.ids) {
            val before = placed(id, tighten = false) ?: continue
            val after = placed(id, tighten = true) ?: continue
            if (before.rooms.size != after.rooms.size) {
                say("$id: room COUNT changed — the tint step must never add or drop a room")
                worsened++
                continue
            }
            measured++

            val sized = after.rooms.count {
                RoomDimensions.parse(it.printedSize.ifBlank { it.label }) != null
            }
            val spreadBefore = scaleSpread(before.rooms)
            val spreadAfter = scaleSpread(after.rooms)
            if (spreadBefore != null && spreadAfter != null && spreadAfter > spreadBefore + 1e-6) worsened++

            // Every room's CENTRE must survive untouched — that is the half of the reader's answer
            // the measurement showed to be sound. The first cut clamped the corrected box into the
            // page and slid a large room's centre by 0.074 of the sheet; this is what caught it.
            for (i in before.rooms.indices) {
                val b = before.rooms[i].source
                val a = after.rooms[i].printedBox ?: after.rooms[i].source
                if (b == null || a == null) continue
                val movedX = abs((b.x + b.w / 2) - (a.x + a.w / 2))
                val movedY = abs((b.y + b.h / 2) - (a.y + a.h / 2))
                if (movedX > 0.02 || movedY > 0.02) {
                    say("$id: ${after.rooms[i].label} centre moved by ${"%.3f".format(maxOf(movedX, movedY))}")
                    worsened++
                }
            }

            // ⭐ ORIENTATION MUST FOLLOW THE PRINTED ORDER, not the reader's rectangle — the same
            // ruling `reshapeToPrinted` follows for the grid, pinned here because this screen's
            // whole job is letting someone check what we read. A bedroom printed 3.72 x 4.50 drawn
            // landscape, while the grid we scored has it portrait, is a picture that contradicts the
            // answer. The first cut of this fix kept the reader's sense and did exactly that.
            for (room in after.rooms) {
                val drawn = room.printedBox ?: continue
                val printed = RoomDimensions.parse(room.printedSize.ifBlank { room.label }) ?: continue
                if (printed.widthMm == printed.depthMm) continue
                val printedIsWide = printed.widthMm > printed.depthMm
                if (drawn.w > drawn.h != printedIsWide) {
                    say("$id: ${room.label} is drawn ${if (drawn.w > drawn.h) "wide" else "tall"} " +
                        "but prints ${if (printedIsWide) "wide" else "tall"}")
                    worsened++
                }
            }

            // 19 x 19 taps across the whole photograph — every wall, every corner, and the margin
            // outside the home, which is where a tap most easily changes its mind about a side.
            var moved = 0
            for (ix in 0..18) {
                for (iy in 0..18) {
                    val px = ix / 18f
                    val py = iy / 18f
                    if (doorForPhotoTap(px, py, before.rooms) != doorForPhotoTap(px, py, after.rooms)) moved++
                }
            }
            totalMoved += moved

            say(
                id.padEnd(26) +
                    after.rooms.size.toString().padEnd(7) +
                    sized.toString().padEnd(7) +
                    (spreadBefore?.let { "%.0f%%".format(it * 100) } ?: "n/a").padEnd(15) +
                    (spreadAfter?.let { "%.0f%%".format(it * 100) } ?: "n/a").padEnd(9) +
                    "$moved of 361",
            )
        }
        say("")
        say("$measured plans measured · $totalMoved of ${measured * 361} taps place a different door")
        say("")

        assertTrue(measured >= 4, "only $measured recorded plans were measured — the corpus is not being read")
        assertTrue(worsened == 0, "$worsened plan(s) got WORSE or lost a room — see the lines above")

        // ⭐ A HARD ZERO, AND IT WAS EARNED RATHER THAN ASSUMED. The first cut of this change
        // corrected [ScannedRoom.source] in place, and this very check measured the cost: 182 of
        // 2888 taps placed a DIFFERENT front door — the highest-weighted element the engine scores,
        // moved as a side effect of fixing a picture. That is why the corrected box now travels
        // beside the reader's rectangle instead of replacing it, and why zero is the only bound
        // worth writing here: the door is untouched BY CONSTRUCTION, so any drift means the two
        // boxes have been wired together again by someone who did not read the note on printedBox.
        assertTrue(
            totalMoved == 0,
            "$totalMoved of ${measured * 361} taps now place a different front door. The tint box " +
                "and the door frame have been coupled again — see ScannedRoom.printedBox.",
        )
    }
}
