package com.vastufirst.shared.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sizes plans PRINT beside their room names — the reliable signal we were deleting.
 *
 * Every caption here is real: from the 30-plan corpus, or from the owner's own Green Court sheet.
 * All twenty were checked numerically before a line of this was written, because a wrong reading
 * here silently mis-shapes a room and a room's shape decides its Vastu direction.
 */
class RoomDimensionsTest {

    private val mmPerFoot = 304.8

    private fun feet(label: String): Pair<Double, Double>? =
        RoomDimensions.parse(label)?.let { it.widthMm / mmPerFoot to it.depthMm / mmPerFoot }

    private fun assertFeet(label: String, w: Double, d: Double) {
        val got = feet(label) ?: throw AssertionError("no size read from \"$label\"")
        assertEquals(w, got.first, 0.01, "width of \"$label\"")
        assertEquals(d, got.second, 0.01, "depth of \"$label\"")
    }

    // ── feet and inches, the convention on the owner's plan ──────────────────────────────────────

    @Test
    fun `plain feet and inches`() {
        assertFeet("BED ROOM 10'-0\"X10'-6\"", 10.0, 10.5)
        assertFeet("PASSAGE 2'-3\"X9'-6\"", 2.25, 9.5)
        assertFeet("KITCHEN 6'-11\"X9'-7\"", 6.9167, 9.5833)
    }

    @Test
    fun `the hyphen is optional`() {
        assertFeet("BED ROOM 12'1\"X11'0\"", 12.0833, 11.0)
        assertFeet("LOBBY/DINING 17'1\"X9'10\"", 17.0833, 9.8333)
    }

    @Test
    fun `half-inch fractions are printed as glyphs and must be read`() {
        // Straight off the owner's sheet. Dropping the ½ would mis-size the room by half an inch —
        // harmless — but failing to PARSE it drops the whole size, which is not.
        assertFeet("LOBBY 10'-3½\"X14'-10½\"", 10.2917, 14.875)
        assertFeet("W.C 4'-11\"X6'-4½\"", 4.9167, 6.375)
    }

    // ── millimetres, the commoner convention in the corpus ───────────────────────────────────────

    @Test
    fun `bare millimetre pairs`() {
        val m = RoomDimensions.parse("BEDROOM 6750X4350")!!
        assertEquals(6750.0, m.widthMm, 0.01)
        assertEquals(4350.0, m.depthMm, 0.01)
    }

    @Test
    fun `millimetres are not mistaken for feet`() {
        // 2950 X 4200 is a kitchen in millimetres. Read as feet it would be a stadium.
        val m = RoomDimensions.parse("KITCHEN 2950X4200")!!
        assertTrue(m.widthMm > 1000, "a four-digit pair is millimetres, not feet")
        assertEquals(0.702, m.ratio, 0.001)
    }

    @Test
    fun `a small bare pair reads as feet`() {
        val m = RoomDimensions.parse("BEDROOM 12X14")!!
        assertEquals(12 * mmPerFoot, m.widthMm, 0.01)
        assertEquals(14 * mmPerFoot, m.depthMm, 0.01)
    }

    @Test
    fun `a trailing parenthetical does not disturb the pair`() {
        val m = RoomDimensions.parse("LIFT 1850X1850 (8 PERSON)")!!
        assertEquals(1850.0, m.widthMm, 0.01)
        assertEquals(1850.0, m.depthMm, 0.01)
    }

    @Test
    fun `an index prefix does not disturb the pair`() {
        val m = RoomDimensions.parse("BEDROOM-1 3000X3650")!!
        assertEquals(3000.0, m.widthMm, 0.01)
    }

    // ── and the far more common case: no honest pair, so no guess ────────────────────────────────

    @Test
    fun `one dimension is not a size PAIR — it is a strip depth`() {
        // A pair cannot be invented from a single number, so parse() stays null — but the number is
        // no longer thrown away: it is the strip's printed DEPTH, read by stripDepth(), and the
        // mapper sets the strip's narrow axis from it. Before that rule the owner's 336 sq ft plan
        // drew its balcony at the reader's sketch size — a quarter of the page — towering over the
        // bedroom, because '1825 WIDE' parsed as nothing at all.
        assertNull(RoomDimensions.parse("BALCONY 6'-0\" WIDE"))
        assertNull(RoomDimensions.parse("5'-0\" WIDE BALCONY"))
        assertNull(RoomDimensions.parse("BALCONY 1500MM WIDE"))
        assertEquals(6 * mmPerFoot, RoomDimensions.stripDepth("BALCONY 6'-0\" WIDE")!!, 0.01)
        assertEquals(5 * mmPerFoot, RoomDimensions.stripDepth("5'-0\" WIDE BALCONY")!!, 0.01)
        assertEquals(1500.0, RoomDimensions.stripDepth("BALCONY 1500MM WIDE")!!, 0.01)
    }

    @Test
    fun `⭐ strip depths — every caption form the real sheets print`() {
        // All read off recorded replies: the owner's 336 sq ft plan, the 526, and the tower sheet.
        assertEquals(1825.0, RoomDimensions.stripDepth("1825 WIDE")!!, 0.01)
        assertEquals(1525.0, RoomDimensions.stripDepth("1525 WIDE")!!, 0.01)
        assertEquals((5 + 5.0 / 12) * mmPerFoot, RoomDimensions.stripDepth("BALCONY 5'-5\" WIDE")!!, 0.01)
        assertEquals((4 + 11.0 / 12) * mmPerFoot, RoomDimensions.stripDepth("SERVICE BALCONY 4'-11\" WIDE")!!, 0.01)
        // A full pair wins first — a caption carrying both never half-matches as a strip.
        assertNull(RoomDimensions.stripDepth("BEDROOM 3050X3200 WIDE OPEN"))
        // No WIDE, no strip; an index or a capacity still reads as nothing.
        assertNull(RoomDimensions.stripDepth("BALCONY"))
        assertNull(RoomDimensions.stripDepth("BEDROOM-1"))
        assertNull(RoomDimensions.stripDepth("LIFT (8 PERSON)"))
        // The reader's own size field wins over the caption, exactly as it does for pairs.
        assertEquals(
            1825.0,
            RoomDimensions.stripDepthOf(ScanBox(label = "BALCONY", printedSize = "1825 WIDE"))!!,
            0.01,
        )
    }

    @Test
    fun `a capacity or an index is never read as a size`() {
        assertNull(RoomDimensions.parse("LIFT (8 PERSON)"))
        assertNull(RoomDimensions.parse("BEDROOM-1"))
        assertNull(RoomDimensions.parse("TOILET-2"))
    }

    @Test
    fun `a plain caption has no size`() {
        assertNull(RoomDimensions.parse("LOBBY"))
        assertNull(RoomDimensions.parse("MASTER BEDROOM"))
        assertNull(RoomDimensions.parse("SER ROOM"))
        assertNull(RoomDimensions.parse(""))
    }

    // ── the property the whole feature turns on ──────────────────────────────────────────────────

    @Test
    fun `the owner's plan reads as four rooms deeper than they are wide`() {
        // ⭐ This is the defect in one assertion. Every one of these was drawn WIDER than deep, and
        // the plan says the opposite. A room's direction follows its shape, so this was moving the
        // score. `ratio` below 1 means "deeper than wide".
        val deeperThanWide = listOf(
            "LOBBY 10'-0\"X12'-9\"",
            "KITCHEN 6'-11\"X9'-7\"",
            "PASSAGE 2'-3\"X9'-6\"",
            "BED ROOM 10'-0\"X10'-6\"",
        )
        for (caption in deeperThanWide) {
            val size = RoomDimensions.parse(caption)!!
            assertTrue(size.ratio < 1.0, "\"$caption\" is printed deeper than it is wide (${size.ratio})")
        }
        // …and the two that really are wider than deep, so the rule is not simply "always tall".
        assertTrue(RoomDimensions.parse("W.C 5'-0\"X2'-11\"")!!.ratio > 1.0)
        assertTrue(RoomDimensions.parse("BATH 5'-0\"X3'-8½\"")!!.ratio > 1.0)
    }

    @Test
    fun `the printed sizes are internally consistent with the stated carpet area`() {
        // The sheet states 336 sq ft carpet + 96 sq ft balcony. Its dimensioned rooms total 353 sq ft
        // — a 5 % excess, which is the wall thickness. That agreement is why these numbers are
        // trusted over the reader's rectangles, so it is pinned rather than asserted in prose.
        val captions = listOf(
            "BED ROOM 10'-0\"X10'-6\"", "W.C 5'-0\"X2'-11\"", "BATH 5'-0\"X3'-8½\"",
            "KITCHEN 6'-11\"X9'-7\"", "LOBBY 10'-0\"X12'-9\"", "PASSAGE 2'-3\"X9'-6\"",
        )
        val sqFt = captions.sumOf { RoomDimensions.parse(it)!!.area } / (mmPerFoot * mmPerFoot)
        assertTrue(sqFt in 340.0..365.0, "expected ~353 sq ft of rooms, got $sqFt")
    }

    // ---- metres, and the field the reader now fills in ----------------------------------------

    @Test
    fun `⭐ metres are read, because the owner's own plan prints them`() {
        val living = RoomDimensions.parse("7.25m X 4.30m")!!
        assertEquals(7250.0, living.widthMm, 1e-6)
        assertEquals(4300.0, living.depthMm, 1e-6)
        // No spaces, brackets and a second part — exactly as his utility and his master toilet print.
        val utility = RoomDimensions.parse("(3.17mX0.90m)+(1.51mX0.40m)")!!
        assertEquals(3170.0, utility.widthMm, 1e-6)
        assertEquals(900.0, utility.depthMm, 1e-6)
    }

    @Test
    fun `⚠ a bare foot pair is never mistaken for metres`() {
        // The unit is required after BOTH numbers, and this is the whole safety of the metre rule:
        // a looser version that accepted "12 X 14 M" would read "12X14" — twelve feet by fourteen —
        // as a twelve-METRE room, i.e. a room four times too big in each direction.
        val feet = RoomDimensions.parse("BED ROOM 12X14")!!
        assertEquals(12 * mmPerFoot, feet.widthMm, 1e-6)
        assertEquals(14 * mmPerFoot, feet.depthMm, 1e-6)
        // …and a millimetre pair stays millimetres.
        val mm = RoomDimensions.parse("BEDROOM 6750X4350")!!
        assertEquals(6750.0, mm.widthMm, 1e-6)
    }

    @Test
    fun `⭐ the reader's own size field wins over the caption, and the caption still works`() {
        // The reply the owner's flat produces: a clean name, and the size in its own field.
        val split = ScanBox(label = "LIVING/DINING", printedSize = "7.25m X 4.30m ( 23'-9\" x 14'-1\" )")
        assertEquals(7.25 * 1000 / mmPerFoot, RoomDimensions.of(split)!!.widthMm / mmPerFoot, 0.05)
        // Every recorded reply from before that field existed carries the size inside the caption,
        // and those fixtures are the measurements this feature was built on — so they must keep
        // working exactly as they did.
        val inline = ScanBox(label = "BEDROOM 6750X4350")
        assertEquals(6750.0, RoomDimensions.of(inline)!!.widthMm, 1e-6)
        // A room with neither has no size, and guessing one would be worse than not trying.
        assertNull(RoomDimensions.of(ScanBox(label = "BALCONY")))
    }
}
