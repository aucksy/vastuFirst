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
        // Was "WIDE BALCONY", which only half-matched and so arrived asking the user to check a
        // caption that could not be clearer. WIDE is a descriptor, not part of a room's name.
        assertEquals("BALCONY", RoomLabels.clean("5'-0\" WIDE BALCONY"))
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

        // "ATT. TOILET" is in the table verbatim, so it is exact. This one is not: it resolves only
        // because TOILET appears inside it, and MASTER is excluded from substring matching precisely
        // so that a master toilet cannot be read as a master bedroom (3.0 against 2.5).
        val loose = RoomLabels.resolve("MASTER TOILET")
        assertIs<LabelMatch.Room>(loose)
        assertEquals(RoomType.TOILET, loose.type)
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

    // ---- the Gurgaon 2BHK the owner scanned, caption by caption ---------------------------------

    /**
     * Every printed caption from a real plan a real user put through the real reader, and the three
     * defects it exposed. This is the regression test for that scan: if any line here changes, a plan
     * that already went through the app reads differently than it did.
     */
    @Test
    fun `every caption on the Green Court plan resolves correctly`() {
        assertEquals(RoomType.BALCONY, type("BALCONY 5'-0\" WIDE"))
        assertEquals(RoomType.BEDROOM, type("BED ROOM 10'-9 1/2\"X11'-0\""))
        assertEquals(RoomType.TOILET, type("TOILET 8'-2\"X5'-0\""))
        assertEquals(RoomType.KITCHEN, type("KITCHEN 8'-2\"X7'-10\""))
        assertEquals(RoomType.BEDROOM, type("BED ROOM 10'-6\"X10'-0\""))
        // The two that were wrong on the owner's scan:
        assertEquals(RoomType.TOILET, type("W.C 4'-11\"X6'-4 1/2\""))
        assertEquals(RoomType.LIVING, type("LOBBY 10'-3 1/2\"X14'-10 1/2\""))
    }

    @Test
    fun `an abbreviation full stop does not split a name in half`() {
        // ⭐ THE BUG: the stop became a space, so `W.C` cleaned to "W C", matched nothing, and the
        // flat's second toilet was dropped as an unrecognised caption. A toilet is weighted 2.5, so
        // this quietly changed a score somebody pays for.
        assertEquals("WC", RoomLabels.clean("W.C"))
        assertEquals(RoomType.TOILET, type("W.C"))
        assertEquals(RoomType.TOILET, type("W.C."))
        assertEquals(RoomType.TOILET, type("w.c 4'-11\"x6'-4\""))
        // …while a stop that already had a space after it still behaves exactly as before.
        assertEquals("ATT TOILET", RoomLabels.clean("ATT. TOILET 1350X2250"))
        assertEquals(RoomType.TOILET, type("ATT. TOILET 1350X2250"))
    }

    @Test
    fun `a lobby is the living room only when the plan names no other one`() {
        // ⭐ The fix the owner's scan asked for, and the correction the corpus forced on it.
        //
        // Calling every lobby a corridor was wrong on his plan. Calling every lobby a living room
        // would have been wrong on FOUR of the six corpus plans that print one, because they also
        // print a real living room — `LOBBY 5100X1800` beside `LIVING 3925X5000` is a 5.1 m × 1.8 m
        // passage. What separates the two cases is the rest of the plan, not the caption.

        // His plan: two bedrooms, kitchen, two toilets, balcony, lobby — and no living room named.
        val greenCourt = RoomLabels.contextOf(
            listOf(
                "BALCONY 5'-0\" WIDE", "BED ROOM 10'-9 1/2\"X11'-0\"", "TOILET 8'-2\"X5'-0\"",
                "KITCHEN 8'-2\"X7'-10\"", "W.C 4'-11\"X6'-4 1/2\"", "BED ROOM 10'-6\"X10'-0\"",
                "LOBBY 10'-3 1/2\"X14'-10 1/2\"",
            ),
        )
        assertFalse(greenCourt.hasDedicatedLivingRoom)
        val livingRoom = RoomLabels.resolve("LOBBY 10'-3 1/2\"X14'-10 1/2\"", greenCourt)
        assertIs<LabelMatch.Room>(livingRoom)
        assertEquals(RoomType.LIVING, livingRoom.type)

        // A corpus plan that names both: the lobby there really is circulation.
        val bothNamed = RoomLabels.contextOf(
            listOf("LOBBY 5100X1800", "LOUNGE 3300X3200", "LIVING 3925X5000", "KITCHEN 2400X3000"),
        )
        assertTrue(bothNamed.hasDedicatedLivingRoom)
        val passage = RoomLabels.resolve("LOBBY 5100X1800", bothNamed)
        assertIs<LabelMatch.Room>(passage)
        assertEquals(RoomType.CORRIDOR, passage.type)

        // ⚠ Either way it is flagged: the word is ambiguous even to a person reading the drawing.
        assertTrue(livingRoom.loose)
        assertTrue(passage.loose)

        // "LIVING RM." and "DRAWING ROOM" must both count as naming a living room.
        assertTrue(RoomLabels.contextOf(listOf("LOBBY", "LIVING RM.")).hasDedicatedLivingRoom)
        assertTrue(RoomLabels.contextOf(listOf("LOBBY", "DRAWING ROOM")).hasDedicatedLivingRoom)
        assertTrue(RoomLabels.contextOf(listOf("LOBBY", "HALL")).hasDedicatedLivingRoom)
        // ⚠ A lobby must not count as its own living room, or the rule could never fire.
        assertFalse(RoomLabels.contextOf(listOf("LOBBY", "LOBBY")).hasDedicatedLivingRoom)
    }

    @Test
    fun `the lobbies that are not living rooms are unaffected`() {
        // A service core drops, whatever else the plan says.
        assertIs<LabelMatch.NotHabitable>(RoomLabels.resolve("LIFT LOBBY"))
        assertIs<LabelMatch.NotHabitable>(
            RoomLabels.resolve("LIFT LOBBY", RoomLabels.contextOf(listOf("LIFT LOBBY", "LIVING"))),
        )
        // An entrance lobby is a foyer and matches before the ambiguous rule is consulted.
        assertEquals(RoomType.ENTRANCE, type("ENTRANCE LOBBY"))
        assertEquals(RoomType.ENTRANCE, type("ENTRY LOBBY"))
        // Seen in the corpus with an abbreviation stop, which the cleaner now removes.
        assertEquals(RoomType.ENTRANCE, type("ENT. LOBBY 1650X1500"))
        // A combined caption names the more specific room; "DINING" is longer, so it wins the
        // substring race before "LOBBY" is ever reached. Both corpus plans print it this way.
        assertEquals(RoomType.DINING, type("LOBBY/DINING"))
        assertEquals(RoomType.DINING, type("LOBBY/DINING 17'1\"X9'10\""))

        // An unambiguous corridor caption is still a corridor, and is NOT flagged.
        val corridor = RoomLabels.resolve("PASSAGE")
        assertIs<LabelMatch.Room>(corridor)
        assertEquals(RoomType.CORRIDOR, corridor.type)
        assertFalse(corridor.loose)
    }

    @Test
    fun `a descriptor word does not make a clear caption look doubtful`() {
        // "BALCONY 5'-0" WIDE" was matching through the substring path, so a perfectly clear caption
        // arrived with a "CHECK" against it and asked the user to verify something obvious.
        assertEquals("BALCONY", RoomLabels.clean("BALCONY 5'-0\" WIDE"))
        val balcony = RoomLabels.resolve("BALCONY 5'-0\" WIDE")
        assertIs<LabelMatch.Room>(balcony)
        assertEquals(RoomType.BALCONY, balcony.type)
        assertFalse(balcony.loose, "nothing about this caption is uncertain")

        // Both ways round, and with a unit suffix — all three are printed in the corpus.
        assertEquals("BALCONY", RoomLabels.clean("5'-0\" WIDE BALCONY"))
        assertEquals("BALCONY", RoomLabels.clean("BALCONY 1500MM WIDE"))
        assertFalse(assertIs<LabelMatch.Room>(RoomLabels.resolve("BALCONY 1500MM WIDE")).loose)

        // ⚠ AREA is deliberately not treated as a descriptor — these are real room names.
        assertEquals(RoomType.DINING, type("DINING AREA"))
        assertEquals(RoomType.UTILITY, type("WASH AREA"))
        // ⚠ And a measurement token must not eat a room whose name contains a letter+digit mix.
        assertEquals(RoomType.BEDROOM, type("BEDROOM-1"))
        assertEquals(RoomType.MASTER_BEDROOM, type("MASTER BEDROOM 12'0\"X14'6\""))
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
