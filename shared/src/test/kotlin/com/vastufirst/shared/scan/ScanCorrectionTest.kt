package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The user overruling what we read off their plan — §6.2b's "confirm **or correct** each one".
 *
 * The confirmation screen has always printed *"we read LOBBY as Corridor"* and put a CHECK against
 * the ones it was unsure of. Until this build it offered no way to answer, which is the same as not
 * asking: the flag spent the user's attention and then had nowhere to send it.
 */
class ScanCorrectionTest {

    private val notes = ScanNotes(coverage = 0.42, areaVariation = 0.7, modelConfidence = 0.95)

    private fun room(
        type: RoomType,
        label: String,
        flags: Set<RoomFlag> = emptySet(),
        rect: CellRect? = null,
    ) = ScannedRoom(type, label, rect, flags)

    private fun assisted(vararg rooms: ScannedRoom) =
        ScanOutcome.Assisted(rooms.toList(), AssistReason.TOO_MANY_ROOMS, notes)

    // ── the correction lands ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the named room takes the new kind`() {
        // The owner's Gurgaon flat: the largest room in a 2BHK, printed LOBBY, read as circulation.
        val out = assisted(
            room(RoomType.CORRIDOR, "LOBBY 10'-3½\"X14'-10½\""),
            room(RoomType.KITCHEN, "KITCHEN"),
        ).withRoomType(0, RoomType.LIVING)

        val rooms = (out as ScanOutcome.Assisted).rooms
        assertEquals(RoomType.LIVING, rooms[0].type)
        assertEquals("the neighbour must be untouched", RoomType.KITCHEN, rooms[1].type)
    }

    @Test
    fun `the printed caption is kept, because it is the evidence`() {
        // What was on the plan does not change when the user tells us what it means. Keeping it is
        // what lets the row still read "Living / LOBBY 10'-3½"X14'-10½"" — our reading beside theirs.
        val out = assisted(room(RoomType.CORRIDOR, "LOBBY")).withRoomType(0, RoomType.LIVING)
        assertEquals("LOBBY", (out as ScanOutcome.Assisted).rooms[0].label)
    }

    @Test
    fun `a placed room keeps its rectangle`() {
        val rect = CellRect(2, 3, 4, 2)
        val out = ScanOutcome.Placed(10, 10, listOf(room(RoomType.CORRIDOR, "LOBBY", rect = rect)), notes)
            .withRoomType(0, RoomType.LIVING)
        val corrected = (out as ScanOutcome.Placed).rooms[0]
        assertEquals(rect, corrected.rect)
        assertEquals(10, out.cols)
        assertEquals(10, out.rows)
    }

    // ── the flags that were asking a question ────────────────────────────────────────────────────

    @Test
    fun `picking a kind answers the flags that were unsure of the reading`() {
        val out = assisted(
            room(
                RoomType.CORRIDOR, "LOBBY",
                flags = setOf(RoomFlag.LOOSE_LABEL_MATCH, RoomFlag.LOW_MODEL_CONFIDENCE),
            ),
        ).withRoomType(0, RoomType.LIVING)
        assertTrue(
            "a CHECK the user has already answered must stop asking",
            (out as ScanOutcome.Assisted).rooms[0].flags.isEmpty(),
        )
    }

    @Test
    fun `flags about the room's SHAPE survive, because knowing its kind says nothing about its size`() {
        val out = assisted(
            room(
                RoomType.CORRIDOR, "LOBBY",
                flags = setOf(RoomFlag.OVERLAP_TRIMMED, RoomFlag.OUT_OF_BOUNDS_CLAMPED, RoomFlag.LOOSE_LABEL_MATCH),
            ),
        ).withRoomType(0, RoomType.LIVING)
        assertEquals(
            setOf(RoomFlag.OVERLAP_TRIMMED, RoomFlag.OUT_OF_BOUNDS_CLAMPED),
            (out as ScanOutcome.Assisted).rooms[0].flags,
        )
    }

    // ── nothing happens when nothing should ──────────────────────────────────────────────────────

    @Test
    fun `re-picking the kind a room already is returns the very same outcome`() {
        // Identity, not equality: the ViewModel treats a different instance as an edit.
        val outcome = assisted(room(RoomType.KITCHEN, "KITCHEN"))
        assertSame(outcome, outcome.withRoomType(0, RoomType.KITCHEN))
    }

    @Test
    fun `an index outside the list is ignored rather than crashing a paid flow`() {
        val outcome = assisted(room(RoomType.KITCHEN, "KITCHEN"))
        assertSame(outcome, outcome.withRoomType(1, RoomType.LIVING))
        assertSame(outcome, outcome.withRoomType(-1, RoomType.LIVING))
        assertSame(outcome, outcome.withRoomType(Int.MAX_VALUE, RoomType.LIVING))
    }

    @Test
    fun `a refusal has no rooms to correct`() {
        val refused = ScanOutcome.Refused(RefusalReason.NOT_2D, notes)
        assertSame(refused, refused.withRoomType(0, RoomType.LIVING))
    }

    @Test
    fun `the diagnostics ride along unchanged`() {
        // coverage / areaVariation / modelConfidence answer "why did it do that?" and must not be
        // quietly rewritten by a user correction — they describe the READ, not the final answer.
        val outcome = assisted(room(RoomType.CORRIDOR, "LOBBY"))
        val out = outcome.withRoomType(0, RoomType.LIVING)
        assertEquals(notes, out.notes)
        assertEquals(AssistReason.TOO_MANY_ROOMS, (out as ScanOutcome.Assisted).reason)
    }

    @Test
    fun `every kind the app can hold can be picked`() {
        val outcome = assisted(room(RoomType.CORRIDOR, "LOBBY"))
        for (type in RoomType.entries) {
            val out = outcome.withRoomType(0, type) as ScanOutcome.Assisted
            assertEquals(type, out.rooms[0].type)
        }
    }
}
