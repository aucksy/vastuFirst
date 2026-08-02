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

    /**
     * ⭐ plan-020 — a real Gurgaon builder plan whose every size is printed in **feet and inches**
     * (`11'-0" x 15'-0"`), the one measurement convention no recorded fixture exercised end-to-end.
     *
     * ⚠ The gap was found the hard way. The fuzz mirror's feet-inches pattern had a broken string
     * escape, so no feet-inch size ever parsed there — and this plan came out ASSISTED in the
     * mirror while Kotlin, whose pattern was right, PLACED it. Two implementations of one design
     * disagreed on a plan's entire outcome and every suite stayed green. This test pins Kotlin's
     * half; the mirror pins its own in `sim.mjs`, so the two can no longer drift apart silently.
     */
    @Test
    fun `⭐ the feet-and-inches plan places, with every size read from its printed text`() {
        val rec = assertNotNull(RecordedScans.load(RecordedScans.PLAN_020), "plan-020 is not bundled")
        assertEquals(14, rec.reply.rooms.size, "the sheet names fourteen spaces")
        assertEquals(
            14,
            rec.reply.rooms.count { RoomDimensions.of(it) != null },
            "every space prints a feet-and-inches size, and every one must parse",
        )
        val out = ScanMapper.map(rec.reply, imageAspect = 1399.0 / 1389.0)
        val placed = assertIs<ScanOutcome.Placed>(out, "a fully dimensioned single home must place")
        assertEquals(13, placed.rooms.size, "everything but the dressing area places")
        assertEquals(
            listOf("DRESS"),
            placed.notes.dropped.map { it.label },
            "the dressing area drops by NAME — the only drop on this sheet",
        )
        assertEquals(
            4,
            placed.rooms.count { it.type == RoomType.BALCONY },
            "four rooms captioned just BALCONY, at four different printed sizes, stay four rooms",
        )
    }

    /**
     * ⭐ greencourt-526 — a real 2BHK scanned live on 2 August 2026, after the owner reported its
     * branded twin drawing badly on his phone. Two firsts: every size prints in raw MILLIMETRES
     * (`3285X3350`), and the balcony prints only ONE dimension (`1525 WIDE`). That caption is not
     * a pair — [RoomDimensions.of] still reads it as nothing — but since v0.6.5 it is the strip's
     * printed DEPTH: the balcony keeps the full-width band the reader drew (its length and place
     * are arrangement evidence) and its depth comes from the sheet's own 1 525 mm, one row on
     * this plan's scale. The owner's one note on this sheet's clean copy — "only the balcony
     * strip is fat" — is this line.
     */
    @Test
    fun `⭐ the millimetre 2BHK places whole, and the one-dimension balcony is a strip at its printed depth`() {
        val rec = assertNotNull(RecordedScans.load(RecordedScans.GREENCOURT), "greencourt-526 is not bundled")
        assertEquals(7, rec.reply.rooms.size, "the sheet names seven spaces")
        assertEquals(
            6,
            rec.reply.rooms.count { RoomDimensions.of(it) != null },
            "six spaces print a millimetre pair; '1525 WIDE' is one number, not a pair",
        )
        assertEquals(
            1525.0,
            rec.reply.rooms.firstNotNullOf { RoomDimensions.stripDepthOf(it) },
            1e-6,
            "…and that one number is the balcony's printed depth",
        )
        val out = ScanMapper.map(rec.reply, imageAspect = 691.0 / 954.0)
        val placed = assertIs<ScanOutcome.Placed>(out, "a dimensioned single 2BHK must place")
        assertEquals(7, placed.rooms.size, "every named space places — nothing on this sheet drops")
        assertTrue(placed.notes.dropped.isEmpty(), "nothing may be dropped: ${placed.notes.dropped}")

        val balcony = placed.rooms.single { it.type == RoomType.BALCONY }
        assertEquals(0, balcony.rect!!.row, "the balcony is the sheet's north edge")
        assertEquals(
            placed.cols,
            balcony.rect!!.w,
            "the balcony must span the home's full width, as the reader drew it",
        )
        assertEquals(1, balcony.rect!!.h, "1 525 mm is one row on this plan's scale — the fat strip is gone")
        // Directions from the printed millimetre pairs — the properties, not the trimmer's arithmetic.
        val lobby = placed.rooms.single { it.type == RoomType.LIVING }
        assertTrue(lobby.rect!!.h > lobby.rect!!.w, "LOBBY prints 3135X4535 — deeper than wide")
        val toilet = placed.rooms.single { it.label == "TOILET" }
        assertTrue(toilet.rect!!.w > toilet.rect!!.h, "TOILET prints 2485X1525 — wider than deep")
        val wc = placed.rooms.single { it.label == "W.C" }
        assertTrue(wc.rect!!.h > wc.rect!!.w, "W.C prints 1500X1950 — deeper than wide")
    }

    /**
     * ⭐⭐ greencourt-336 — the owner's 336 sq ft 1BHK, the plan whose drawn output he rejected
     * outright on 2 August 2026: "balcony towering over the rooms, everything clumped left, a huge
     * mostly-empty grid I have to scroll". Two recordings of the SAME sheet — a clean copy whose
     * arrangement reads close to the paper, and the branded builder page (logo third) whose
     * arrangement scrambles. Both carry `BALCONY 1825 WIDE`.
     *
     * The three v0.6.5 drawing rules this plan forced, asserted here exactly as the mirror pins
     * them (sim.mjs, with a fault injection each):
     *  - the balcony is a STRIP — 1 825 mm deep on the plan's own scale, strictly shallower than
     *    the bedroom behind it, instead of the reader's quarter-page sketch;
     *  - the grid ends where the home does — the rows the shrink frees are deleted, so a 336 sq ft
     *    flat stops arriving on a scrolling villa grid;
     *  - a room read flush against the home's outer wall STAYS on it when it shrinks to print
     *    scale: the bedroom his sheet puts on the east wall keeps that wall.
     */
    @Test
    fun `⭐⭐ the 336 clean copy — the balcony stops towering, the grid ends at the home, the bedroom keeps its wall`() {
        val rec = assertNotNull(
            RecordedScans.load(RecordedScans.GREENCOURT_336_CLEAN),
            "greencourt-336-clean is not bundled",
        )
        assertEquals(7, rec.reply.rooms.size, "the sheet names seven spaces")
        assertEquals(
            1825.0,
            rec.reply.rooms.firstNotNullOf { RoomDimensions.stripDepthOf(it) },
            1e-6,
            "the balcony prints one dimension: its depth",
        )
        val out = ScanMapper.map(rec.reply, imageAspect = 669.0 / 976.0)
        val placed = assertIs<ScanOutcome.Placed>(out, "a fully dimensioned 1BHK must place")
        assertEquals(7, placed.rooms.size, "every named space places")
        assertTrue(placed.notes.dropped.isEmpty(), "nothing may be dropped: ${placed.notes.dropped}")

        val balcony = placed.rooms.single { it.type == RoomType.BALCONY }
        val bedroom = placed.rooms.single { it.type == RoomType.BEDROOM }
        assertTrue(
            balcony.rect!!.h < bedroom.rect!!.h,
            "the balcony (${balcony.rect}) must be shallower than the bedroom (${bedroom.rect}) — " +
                "it is a 1.8 m strip, not the biggest room on the flat",
        )
        assertEquals(9, placed.rows, "the grid ends where the home does — measured 9 rows, not 10")
        assertEquals(
            placed.cols,
            bedroom.rect!!.right,
            "the bedroom was READ against the home's east wall — his sheet puts it there — and must stay on it",
        )
    }

    @Test
    fun `⭐ the 336 branded copy — the same rules hold on a scrambled read, and every room still places`() {
        // The branded page's ARRANGEMENT is wrong (the reader returns the bedroom full-width when
        // the sheet has it on the right) and stays wrong: measured across every recording, no
        // signal in a single reply separates this copy from the clean one — the one candidate
        // (read-vs-print orientation contradictions) scores the owner's own flat WORSE than this
        // page, so a gate would bin the best mapping in the corpus and wave this one through.
        // What honest drawing rules CAN promise on a scrambled read is pinned here.
        val rec = assertNotNull(
            RecordedScans.load(RecordedScans.GREENCOURT_336_BRANDED),
            "greencourt-336-branded is not bundled",
        )
        val out = ScanMapper.map(rec.reply, imageAspect = 1100.0 / 1100.0)
        val placed = assertIs<ScanOutcome.Placed>(out)
        assertEquals(7, placed.rooms.size, "all seven rooms place — a scrambled read loses nothing")
        val balcony = placed.rooms.single { it.type == RoomType.BALCONY }
        assertEquals(2, balcony.rect!!.h, "the 1 825 mm strip holds on this copy too")
        assertEquals(9, placed.rows, "and the dead edge rows are gone here too")
    }
}
