package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The mapper against the **actual replies the Groq API returned**, copied verbatim out of
 * `tools/scan-eval/out/`. Nothing here is invented.
 *
 *   plan-01        clean digital render   8/8 rooms placed correctly, mean IoU 0.79  → Placed
 *   plan-01-jpeg   downscaled + JPEG      6/8 correct, IoU 0.73                      → Placed
 *   plan-01-photo  simulated phone photo  2/8 correct, IoU 0.37                      → Placed ⚠ known miss
 *   real-dense     real 24-space floor plate, names perfect, rectangles scattered    → Assisted
 *   owner-flat     ⭐ the OWNER'S own 15-room apartment, every room sized on the sheet → Placed
 *
 * ⭐ `real-dense` is the reply that moved the gate from coverage to room count: its coverage (0.421)
 * is *identical* to a real plan that placed well, so coverage could not tell them apart. See
 * [ScanMapper.MAX_TRUSTED_ROOMS] for the full table of what was judged by eye.
 */
class RecordedScanTest {

    @Test
    fun `every recorded reply is bundled and parses`() {
        for (id in RecordedScans.ids) {
            val rec = assertNotNull(RecordedScans.load(id), "fixture $id is missing from resources")
            assertEquals("qwen/qwen3.6-27b", rec.model)
            assertTrue(rec.reply.rooms.isNotEmpty(), "$id carries no rooms")
        }
        assertEquals(8, RecordedScans.load(RecordedScans.CLEAN)!!.reply.rooms.size)
        assertEquals(24, RecordedScans.load(RecordedScans.DENSE)!!.reply.rooms.size)
    }

    @Test
    fun `⭐ every recorded read names all eight rooms — even the bad photo`() {
        // The measured core finding, three times over: labels survive photography, geometry does not.
        val expected = listOf(
            RoomType.LIVING, RoomType.POOJA, RoomType.KITCHEN, RoomType.MASTER_BEDROOM,
            RoomType.BEDROOM, RoomType.TOILET, RoomType.BATHROOM, RoomType.BALCONY,
        ).sortedBy { it.name }

        for (id in listOf(RecordedScans.CLEAN, RecordedScans.COMPRESSED, RecordedScans.PHOTO)) {
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

    /**
     * ⭐⭐ The gate is the ROOM COUNT, and this is the real reply that forced it there.
     *
     * A mirrored two-flat floor plate: 24 named spaces, every name read correctly, and the
     * rectangles scattered nowhere near the rooms they name — verified by drawing them back over the
     * plan (`tools/scan-eval/out/overlay/plan-002.png`). Its coverage is **0.421, identical to a
     * plan that placed well**, which is why coverage could not stay as the gate.
     */
    @Test
    fun `⭐ a dense floor plate keeps its rooms and throws away its geometry`() {
        val out = ScanMapper.map(RecordedScans.load(RecordedScans.DENSE)!!.reply)
        val assisted = assertIs<ScanOutcome.Assisted>(out)
        // ⭐ It now says WHY in the sheet's own terms rather than by counting: this plan prints
        // `LIFT 1850X1850 (8 PERSON)`, so it is a floor of a building and not one home. That reads
        // better on screen too — "this sheet shows a whole floor, it has a lift on it" is something
        // the user can look at their own plan and agree with.
        assertEquals(AssistReason.FLOOR_PLATE, assisted.reason)
        assertTrue(assisted.rooms.size > ScanMapper.MAX_TRUSTED_ROOMS)
        assertTrue(assisted.rooms.all { it.rect == null }, "Assisted must not carry geometry")
        // The names it read are excellent, which is the whole point of the Assisted path.
        val names = assisted.rooms.map { it.type }
        assertTrue(RoomType.KITCHEN in names && RoomType.BEDROOM in names && RoomType.TOILET in names)
    }

    /**
     * ⚠ A KNOWN, DELIBERATE MISS, pinned so it stays deliberate.
     *
     * The simulated phone photo placed only 2 of its 8 rooms correctly — but it has 8 rooms, which is
     * under the room-count gate, so it is trusted. **Nothing available catches it**: its coverage
     * (0.569) sits *above* both real plans that placed perfectly, so no coverage threshold can
     * separate them. The mandatory confirmation step is what catches it, which is precisely what
     * §6.2b requires that step to be for — and the Placed copy asks for exactly that check.
     */
    @Test
    fun `a skewed photo is trusted — the known limit of the room-count gate`() {
        val out = ScanMapper.map(RecordedScans.load(RecordedScans.PHOTO)!!.reply)
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(8, placed.rooms.size)
        assertTrue(
            placed.notes.coverage > 0.39,
            "the photo read ${placed.notes.coverage}, ABOVE the good real plans — so no coverage " +
                "threshold could have separated them",
        )
    }

    @Test
    fun `⭐ the model reported 0-95 confidence on the read it got wrong`() {
        // S2, in the recorded data rather than in a comment. Same self-report, opposite quality.
        val clean = ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)
        val dense = ScanMapper.map(RecordedScans.load(RecordedScans.DENSE)!!.reply)
        assertEquals(0.95, clean.notes.modelConfidence, 1e-9)
        assertEquals(0.95, dense.notes.modelConfidence, 1e-9)
        assertIs<ScanOutcome.Placed>(clean)
        assertIs<ScanOutcome.Assisted>(dense)
    }

    @Test
    // runBlocking<Unit>, not runBlocking: JUnit4 rejects a test method with a return value, and the
    // last expression here is an assertIs that hands one back.
    fun `the fake reader replays the fixtures through the real mapper`() = runBlocking<Unit> {
        val reader = FakePlanReader()
        val outcomes = List(RecordedScans.ids.size) {
            assertIs<ScanResult.Read>(reader.read(ByteArray(0), null)).outcome
        }
        assertIs<ScanOutcome.Placed>(outcomes[0])
        assertIs<ScanOutcome.Placed>(outcomes[1])
        assertIs<ScanOutcome.Placed>(outcomes[2])
        assertIs<ScanOutcome.Assisted>(outcomes[3])
        // ⭐ The owner's own flat — a real fifteen-room apartment, placed. See OwnerFlatScanTest.
        assertIs<ScanOutcome.Placed>(outcomes[4])
        // …and it cycles, so a screen can be driven round the states without re-creating it.
        assertIs<ScanOutcome.Placed>(assertIs<ScanResult.Read>(reader.read(ByteArray(0), null)).outcome)
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
