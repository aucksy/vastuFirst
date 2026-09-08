package com.vastufirst.shared.scan

import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * ⭐ THE WALL SNAP, PROVEN TWO WAYS: on a drawing made in code, where every wall, tile hairline and
 * piece of furniture is placed by hand and the right answer is known to the pixel; and on the
 * bundled fixture plan, whose layout is exact by construction (`tools/scan-eval/fixtures/plan-01.html`)
 * and whose recorded reply the reader got visibly wrong (mean overlap 79 %).
 *
 * Every line marked `SCORE|` is lifted into the CI log, where the numbers can be read.
 */
class WallSnapTest {

    private fun say(line: String) = println("SCORE| $line")

    // ---- a plan drawn in code -------------------------------------------------------------------
    //
    // 600 x 600. Outer wall 10 px thick with its inner faces at 50 and 550; two 5 px partitions with
    // their faces at 300 and 305; dark 1 px floor-tile hairlines every 40 px; a wooden table in the
    // first room; a pale grey rug with no dark edge in the last. Four rooms, inner faces:
    //   A x 50..300  y 50..300      B x 305..550  y 50..300
    //   C x 50..300  y 305..550     D x 305..550  y 305..550
    private fun synthetic(): PlanImage {
        val w = 600
        val h = 600
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, rgb: Int) {
            for (y in y0 until y1) for (x in x0 until x1) if (x in 0 until w && y in 0 until h) px[y * w + x] = (0xFF shl 24) or rgb
        }
        val black = 0x111111
        val tile = 0x505050   // as dark as a wall's outline, and one pixel wide
        val wood = 0xB0602A   // a table: dark enough to be ink by brightness, but coloured
        val rug = 0xC8C8C8    // grey, wide, and without a dark core
        var x = 90
        while (x < 550) { fill(x, 50, x + 1, 550, tile); x += 40 }
        var y = 90
        while (y < 550) { fill(50, y, 550, y + 1, tile); y += 40 }
        fill(120, 120, 200, 220, wood)
        fill(340, 340, 500, 500, rug)
        fill(40, 40, 560, 50, black)
        fill(40, 550, 560, 560, black)
        fill(40, 40, 50, 560, black)
        fill(550, 40, 560, 560, black)
        fill(300, 40, 305, 560, black)
        fill(40, 300, 560, 305, black)
        return PlanImage.fromArgb(w, h, px)
    }

    private fun near(expected: WallSnap.Edges, actual: WallSnap.Edges, what: String) {
        val off = listOf(expected.x0 - actual.x0, expected.y0 - actual.y0, expected.x1 - actual.x1, expected.y1 - actual.y1)
        assertTrue(off.all { kotlin.math.abs(it) <= 1 }, "$what: expected $expected, got $actual")
    }

    @Test
    fun `edges snap to walls, never to tile hairlines, a wooden table or a grey rug`() {
        val img = synthetic()
        val frame = WallSnap.Edges(40, 40, 560, 560)
        val read = listOf(
            WallSnap.Edges(62, 70, 322, 340),    // A: read too far right and down, and too big
            WallSnap.Edges(290, 60, 540, 280),   // B: read across the partition, and short
            WallSnap.Edges(70, 320, 280, 520),   // C: read small, inside the room
            WallSnap.Edges(320, 330, 530, 530),  // D: the rug room, read inside the rug's edges
        )
        val out = WallSnap.snapAll(img, read, frame, trace = ::say)
        near(WallSnap.Edges(50, 50, 300, 300), out[0], "room A")
        near(WallSnap.Edges(305, 50, 550, 300), out[1], "room B")
        near(WallSnap.Edges(50, 305, 300, 550), out[2], "room C")
        near(WallSnap.Edges(305, 305, 550, 550), out[3], "room D")
    }

    @Test
    fun `a blank picture, or no picture, leaves the reply untouched and the same instance`() {
        val draft = RecordedScans.load(RecordedScans.CLEAN)!!.reply
        assertSame(draft, WallSnap.refine(draft, null))
        val blank = PlanImage(200, 200, ByteArray(200 * 200) { 0xFF.toByte() }, ByteArray(200 * 200))
        assertSame(draft, WallSnap.refine(draft, blank))
    }

    @Test
    fun `only the rectangle moves - label, printed size and confidence survive a snap`() {
        val draft = RecordedScans.load(RecordedScans.CLEAN)!!.reply.copy(building = BODY)
        val after = WallSnap.refine(draft, load("plan-01.png"))
        assertEquals(draft.rooms.size, after.rooms.size)
        for ((a, b) in draft.rooms.zip(after.rooms)) {
            assertEquals(a.label, b.label)
            assertEquals(a.printedSize, b.printedSize)
            assertEquals(a.confidence, b.confidence)
        }
        assertEquals(draft.building, after.building, "the building box is not touched")
    }

    // ---- the fixture plan with exact ground truth -----------------------------------------------

    /** plan-01's true rooms, fractions of the plan body — copied from the fixture's own JSON. */
    private val truth = mapOf(
        "LIVING ROOM" to ScanBox(x = 0.0, y = 0.0, w = 0.6, h = 0.4),
        "POOJA" to ScanBox(x = 0.6, y = 0.0, w = 0.4, h = 0.2),
        "KITCHEN" to ScanBox(x = 0.6, y = 0.2, w = 0.4, h = 0.2),
        "MASTER BEDROOM" to ScanBox(x = 0.0, y = 0.4, w = 0.5, h = 0.45),
        "BEDROOM 2" to ScanBox(x = 0.5, y = 0.4, w = 0.5, h = 0.3),
        "TOILET" to ScanBox(x = 0.5, y = 0.7, w = 0.2333, h = 0.15),
        "BATH" to ScanBox(x = 0.7333, y = 0.7, w = 0.2667, h = 0.15),
        "BALCONY" to ScanBox(x = 0.0, y = 0.85, w = 1.0, h = 0.15),
    )

    /** The plan body on the sheet: 900 x 1200 px at (50, 120) on a 1000 x 1400 page (plan-01.html). */
    private val BODY = ScanBox(x = 50.0 / 1000, y = 120.0 / 1400, w = 900.0 / 1000, h = 1200.0 / 1400)

    private fun load(name: String): PlanImage {
        val stream = WallSnapTest::class.java.getResourceAsStream("/scan/$name") ?: error("missing test resource $name")
        val img = stream.use { ImageIO.read(it) } ?: error("$name did not decode")
        val w = img.width
        val h = img.height
        val px = IntArray(w * h)
        img.getRGB(0, 0, w, h, px, 0, w)
        return PlanImage.fromArgb(w, h, px)
    }

    private fun iou(a: ScanBox, b: ScanBox): Double {
        val ix = maxOf(0.0, minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x))
        val iy = maxOf(0.0, minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y))
        val i = ix * iy
        return i / (a.w * a.h + b.w * b.h - i)
    }

    private fun meanIou(rooms: List<ScanBox>): Double =
        rooms.map { r -> iou(r, truth.getValue(r.label.uppercase())) }.average()

    private fun pct(v: Double) = "${(v * 100).toInt()} %"

    @Test
    fun `plan-01 - the reader's loose boxes land on the true rooms, clean and compressed alike`() {
        say("")
        say("═══ WALL SNAP — mean overlap of the reader's boxes with the TRUE rooms, before -> after ═══")
        for ((id, file, floor) in listOf(
            Triple(RecordedScans.CLEAN, "plan-01.png", 0.95),
            Triple(RecordedScans.COMPRESSED, "plan-01-jpeg.jpg", 0.93),
        )) {
            // These recordings predate the building box: their rectangles are fractions of the
            // plan body, so the body IS their building box.
            val draft = RecordedScans.load(id)!!.reply.copy(building = BODY)
            val before = meanIou(draft.rooms)
            val after = WallSnap.refine(draft, load(file))
            val afterIou = meanIou(after.rooms)
            say("${id.padEnd(14)} ${pct(before)} -> ${pct(afterIou)}")
            assertTrue(afterIou >= floor, "$id: overlap after the snap is ${pct(afterIou)}, below ${pct(floor)}")
            assertTrue(afterIou > before + 0.10, "$id: the snap did not improve on the reader (${pct(before)} -> ${pct(afterIou)})")
        }
    }

    @Test
    fun `plan-01 photographed at an angle - nothing is thrown away and no box runs wild`() {
        val draft = RecordedScans.load(RecordedScans.PHOTO)!!.reply.copy(building = BODY)
        val after = WallSnap.refine(draft, load("plan-01-photo.jpg"))
        assertEquals(draft.rooms.size, after.rooms.size)
        var moved = 0
        for ((a, b) in draft.rooms.zip(after.rooms)) {
            if (a != b) moved++
            val ratio = (b.w * b.h) / (a.w * a.h)
            assertTrue(ratio in 0.25..2.25, "${a.label}: area became ${"%.2f".format(ratio)}x what was read")
        }
        say("plan-01-photo    $moved of ${draft.rooms.size} boxes moved toward a wall; none beyond the sanity bounds")
    }
}
