package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The mapper against the **actual replies the Groq API returned on 2026-07-29**, copied verbatim out
 * of `tools/scan-eval/out/`. These are the only three reads in the project with measured ground
 * truth, so they are the only place the coverage gate can be checked against a known-right and a
 * known-wrong answer rather than against itself.
 *
 *   plan-01        clean digital render   8/8 rooms placed correctly, mean IoU 0.79  → must be Placed
 *   plan-01-jpeg   downscaled + JPEG      6/8 correct, IoU 0.73                      → must be Placed
 *   plan-01-photo  simulated phone photo  2/8 correct, IoU 0.37                      → must be Assisted
 *
 * The threshold that separates them was derived from a *different* corpus — the 24 real 2D plans —
 * before these numbers were looked at, so this is a check, not a fit.
 */
class RecordedScanTest {

    @Test
    fun `all three recorded replies are bundled and parse`() {
        for (id in RecordedScans.ids) {
            val rec = assertNotNull(RecordedScans.load(id), "fixture $id is missing from resources")
            assertEquals("qwen/qwen3.6-27b", rec.model)
            assertEquals(8, rec.reply.rooms.size, "$id should carry all 8 rooms")
        }
    }

    @Test
    fun `⭐ every recorded read names all eight rooms — even the bad photo`() {
        // The measured core finding, three times over: labels survive photography, geometry does not.
        val expected = listOf(
            RoomType.LIVING, RoomType.POOJA, RoomType.KITCHEN, RoomType.MASTER_BEDROOM,
            RoomType.BEDROOM, RoomType.TOILET, RoomType.BATHROOM, RoomType.BALCONY,
        ).sortedBy { it.name }

        for (id in RecordedScans.ids) {
            val draft = RecordedScans.load(id)!!.reply
            val rooms = when (val out = ScanMapper.map(draft)) {
                is ScanOutcome.Placed -> out.rooms
                is ScanOutcome.Assisted -> out.rooms
                is ScanOutcome.Refused -> error("$id must not be refused: ${out.reason}")
            }
            assertEquals(expected, rooms.map { it.type }.sortedBy { it.name }, "room types for $id")
        }
    }

    @Test
    fun `⭐ the clean render is trusted with its geometry`() {
        val out = ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(1.0, placed.notes.coverage, 1e-6)
        assertEquals(8, placed.rooms.size)
        assertTrue(placed.rooms.all { it.rect != null })
    }

    @Test
    fun `compression alone does not cost the geometry`() {
        val out = ScanMapper.map(RecordedScans.load(RecordedScans.COMPRESSED)!!.reply)
        assertIs<ScanOutcome.Placed>(out)
    }

    @Test
    fun `⭐ the phone photo's geometry is thrown away and its rooms are kept`() {
        // Measured: 2 of 8 rooms within IoU 0.5. Placing those would put the kitchen in the wrong
        // zone, and the zone is what gets scored.
        val out = ScanMapper.map(RecordedScans.load(RecordedScans.PHOTO)!!.reply)
        val assisted = assertIs<ScanOutcome.Assisted>(out)
        assertEquals(AssistReason.LOW_COVERAGE, assisted.reason)
        assertEquals(8, assisted.rooms.size)
        assertTrue(assisted.rooms.all { it.rect == null })
        assertTrue(
            assisted.notes.coverage < ScanMapper.PLACED_COVERAGE,
            "the photo read ${assisted.notes.coverage}, gate is ${ScanMapper.PLACED_COVERAGE}",
        )
    }

    @Test
    fun `⭐ the model reported 0-95 confidence on the read it got wrong`() {
        // S2, in the recorded data rather than in a comment. Same self-report, opposite quality.
        val clean = ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)
        val photo = ScanMapper.map(RecordedScans.load(RecordedScans.PHOTO)!!.reply)
        assertEquals(0.95, clean.notes.modelConfidence, 1e-9)
        assertEquals(0.95, photo.notes.modelConfidence, 1e-9)
        assertIs<ScanOutcome.Placed>(clean)
        assertIs<ScanOutcome.Assisted>(photo)
    }

    @Test
    // runBlocking<Unit>, not runBlocking: JUnit4 rejects a test method with a return value, and the
    // last expression here is an assertIs that hands one back.
    fun `the fake reader replays the fixtures through the real mapper`() = runBlocking<Unit> {
        val reader = FakePlanReader()
        val outcomes = List(RecordedScans.ids.size) { reader.read(ByteArray(0), null) }
        assertIs<ScanOutcome.Placed>(outcomes[0])
        assertIs<ScanOutcome.Placed>(outcomes[1])
        assertIs<ScanOutcome.Assisted>(outcomes[2])
        // …and it cycles, so a screen can be driven round the states without re-creating it.
        assertIs<ScanOutcome.Placed>(reader.read(ByteArray(0), null))
    }

    /**
     * ⚠ Plan doc §7 asked whether `MAX_GRID = 10` has enough resolution for a scanned home, since
     * that would mean touching the editor the owner put on hold. **Measured here rather than
     * assumed**: this is the answer, and it is recorded as a test so it stays true.
     */
    @Test
    fun `MAX_GRID 10 holds a scanned home without swallowing rooms`() {
        val placed = assertIs<ScanOutcome.Placed>(
            ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply),
        )
        assertEquals(8, placed.rooms.size, "all 8 rooms survived the snap at ${placed.cols}×${placed.rows}")
        assertTrue(placed.notes.dropped.none { it.reason == DropReason.DEGENERATE })

        // And a deliberately dense one: a 12-room 4BHK with unequal bays, the realistic worst case
        // (§3h saw real reads carrying up to 25 captions). Every room must survive the snap.
        val names = listOf(
            "LIVING ROOM", "KITCHEN", "MASTER BEDROOM", "BEDROOM", "TOILET", "BATH",
            "POOJA", "DINING", "STUDY", "STORE", "BALCONY", "UTILITY",
        )
        val xs = listOf(0.0, 0.30, 0.50, 0.80, 1.0)
        val ys = listOf(0.0, 0.45, 0.70, 1.0)
        val dense = names.mapIndexed { i, n ->
            val c = i % 4
            val r = i / 4
            ScanBox(n, xs[c], ys[r], xs[c + 1] - xs[c], ys[r + 1] - ys[r], 0.9)
        }
        val denseOut = ScanMapper.map(ScanDraft(PlanImageType.TWO_D_PLAN, true, false, dense, 0.95))
        val densePlaced = assertIs<ScanOutcome.Placed>(denseOut)
        assertEquals(12, densePlaced.rooms.size, "a 12-room home must not lose rooms to grid resolution")
        assertEquals(10 to 10, densePlaced.cols to densePlaced.rows)
        assertTrue(densePlaced.notes.dropped.isEmpty())
    }
}
