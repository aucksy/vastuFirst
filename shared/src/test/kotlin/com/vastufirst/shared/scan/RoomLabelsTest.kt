package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The synonym table, against captions read VERBATIM off the 30 real Indian plans in the corpus
 * (docs/SCAN-PLAN-READING-PLAN.md §3h) — `BEDROOM 6750X4350`, `ATT. TOILET 1350X2250`,
 * `LIFT 1850X1850 (8 PERSON)`, `5'-0" WIDE BALCONY`, `SER ROOM`, `WALK IN CLOSET`, `PUJA`.
 *
 * Only 74 % of real captions mapped to a `RoomType` before this table existed. No new room types are
 * added — a new type needs a Vastu weight plus ideal/prohibited zones, which is an expert ruling.
 */
class RoomLabelsTest {

    private fun type(raw: String): RoomType? =
        (RoomLabels.resolve(raw) as? LabelMatch.Room)?.type

    // ---- cleaning: printed dimensions, feet/inch marks, parentheticals, index suffixes ----------

    @Test
    fun `strips printed dimensions from real captions`() {
        assertEquals("BEDROOM", RoomLabels.clean("BEDROOM 6750X4350"))
        assertEquals("ATT TOILET", RoomLabels.clean("ATT. TOILET 1350X2250"))
        assertEquals("KITCHEN", RoomLabels.clean("KITCHEN 2950X4200"))
        assertEquals("BED ROOM", RoomLabels.clean("BED ROOM 12'1\"X11'0\""))
        assertEquals("BED ROOM", RoomLabels.clean("BED ROOM 12'-0\" X 10'-0\""))
    }

    @Test
    fun `strips parentheticals and index suffixes`() {
        assertEquals("LIFT", RoomLabels.clean("LIFT 1850X1850 (8 PERSON)"))
        assertEquals("BEDROOM", RoomLabels.clean("BEDROOM-1"))
        assertEquals("TOILET", RoomLabels.clean("TOILET-1"))
        assertEquals("BEDROOM", RoomLabels.clean("BEDROOM 2"))
        assertEquals("WIDE BALCONY", RoomLabels.clean("5'-0\" WIDE BALCONY"))
    }

    @Test
    fun `a caption that is only a number cleans to nothing and is never guessed`() {
        assertEquals("", RoomLabels.clean("1"))
        assertEquals("", RoomLabels.clean("15"))
        assertIs<LabelMatch.Unknown>(RoomLabels.resolve("7"))
    }

    // ---- the mapping itself ---------------------------------------------------------------------

    @Test
    fun `real captions from the corpus map to room types`() {
        assertEquals(RoomType.BEDROOM, type("BEDROOM 6750X4350"))
        assertEquals(RoomType.TOILET, type("ATT. TOILET 1350X2250"))
        assertEquals(RoomType.BALCONY, type("5'-0\" WIDE BALCONY"))
        assertEquals(RoomType.POOJA, type("PUJA"))
        assertEquals(RoomType.POOJA, type("PUJA SPACE"))
        assertEquals(RoomType.MASTER_BEDROOM, type("MASTER BEDROOM"))
        assertEquals(RoomType.LIVING, type("LIVING & DINING"))
        assertEquals(RoomType.ENTRANCE, type("FOYER"))
        assertEquals(RoomType.BALCONY, type("VERANDAH"))
        assertEquals(RoomType.CORRIDOR, type("PASSAGE"))
    }

    @Test
    fun `owner decision D1 — the synonym table, in full`() {
        assertEquals(RoomType.BALCONY, type("PORCH"))
        assertEquals(RoomType.BALCONY, type("SIT OUT"))
        assertEquals(RoomType.ENTRANCE, type("VESTIBULE"))
        assertEquals(RoomType.ENTRANCE, type("FOYER"))
        assertEquals(RoomType.BEDROOM, type("SER ROOM"))
        assertEquals(RoomType.STUDY, type("AV ROOM"))
        assertEquals(RoomType.STUDY, type("OFFICE"))
        assertEquals(RoomType.LIVING, type("LOUNGE"))
        assertEquals(RoomType.LIVING, type("DRAWING ROOM"))
        assertEquals(RoomType.TOILET, type("POWDER ROOM"))
    }

    @Test
    fun `owner decision D1 — dressing areas and service shafts are dropped, not typed`() {
        for (caption in listOf(
            "DRESS", "DRESSING", "DRESS AREA", "DRESSING ROOM",
            "WALK IN CLOSET", "WALK-IN WARDROBE", "WARDROBE",
            "DUCT", "ELECTRICAL DUCT", "LIFT", "LIFT LOBBY", "SHAFT",
        )) {
            assertIs<LabelMatch.NotHabitable>(RoomLabels.resolve(caption), "expected $caption to drop")
        }
    }

    @Test
    fun `an unrecognised caption is never guessed`() {
        assertIs<LabelMatch.Unknown>(RoomLabels.resolve("SOMETHING ELSE"))
        assertIs<LabelMatch.Unknown>(RoomLabels.resolve("2 BHK"))
        assertIs<LabelMatch.Unknown>(RoomLabels.resolve(""))
    }

    // ---- the traps ------------------------------------------------------------------------------

    @Test
    fun `MASTER never eats a longer caption — MASTER TOILET is a toilet`() {
        // MASTER_BEDROOM weighs 3.0 against TOILET's 2.5, so a substring match here would move the
        // score. `MASTER` is therefore an exact-only key.
        assertEquals(RoomType.TOILET, type("MASTER TOILET"))
        assertEquals(RoomType.BATHROOM, type("MASTER BATH"))
        assertEquals(RoomType.MASTER_BEDROOM, type("MASTER BEDROOM"))
        assertEquals(RoomType.MASTER_BEDROOM, type("MASTER BED ROOM"))
    }

    @Test
    fun `longest synonym wins, so BEDROOM never swallows MASTER BEDROOM`() {
        assertEquals(RoomType.MASTER_BEDROOM, type("MASTER BEDROOM WITH BALCONY"))
        assertEquals(RoomType.GUEST_BEDROOM, type("GUEST BEDROOM"))
        assertEquals(RoomType.ENTRANCE, type("ENTRANCE HALL"))
        assertEquals(RoomType.CORRIDOR, type("HALLWAY"))
        assertEquals(RoomType.LIVING, type("HALL"))
    }

    @Test
    fun `an exact room name is never eaten by a drop substring`() {
        assertEquals(RoomType.STAIRCASE, type("STAIRCASE"))
        assertEquals(RoomType.STAIRCASE, type("STAIR CASE"))
    }

    @Test
    fun `whole-caption matches are not flagged loose, substring matches are`() {
        val exact = RoomLabels.resolve("KITCHEN")
        assertIs<LabelMatch.Room>(exact)
        assertFalse(exact.loose)

        // "ATT. TOILET" is in the table verbatim, so it is exact. This one is not.
        val loose = RoomLabels.resolve("5'-0\" WIDE BALCONY")
        assertIs<LabelMatch.Room>(loose)
        assertTrue(loose.loose)
    }

    // ---- multi-unit sheets ----------------------------------------------------------------------

    @Test
    fun `unit captions are recognised — plan-001 of the real corpus`() {
        // Its five captions were UNIT-1, UNIT-2, UNIT-3, UNIT-4 and LIFT / STAIRCASE.
        assertTrue(RoomLabels.isUnitLabel("UNIT-1"))
        assertTrue(RoomLabels.isUnitLabel("UNIT 2"))
        assertTrue(RoomLabels.isUnitLabel("FLAT B"))
        assertTrue(RoomLabels.isUnitLabel("TYPE A"))
        assertFalse(RoomLabels.isUnitLabel("KITCHEN"))
        assertFalse(RoomLabels.isUnitLabel("PLOT AREA STATEMENT"))
        assertFalse(RoomLabels.isUnitLabel(""))
    }

    // ---- regex discipline (the lesson that cost five releases elsewhere) -------------------------

    @Test
    fun `cleaning is case-insensitive and locale-invariant`() {
        assertEquals("KITCHEN", RoomLabels.clean("kitchen"))
        assertEquals("KITCHEN", RoomLabels.clean("Kitchen"))
        assertEquals(RoomType.KITCHEN, type("kitchen 2950x4200"))
        // Non-ASCII text has no ASCII letters left to match, so it lands on Unknown rather than on a
        // wrong room — the graceful landing owner decision D4 relies on.
        assertIs<LabelMatch.Unknown>(RoomLabels.resolve("शयनकक्ष"))
    }
}
