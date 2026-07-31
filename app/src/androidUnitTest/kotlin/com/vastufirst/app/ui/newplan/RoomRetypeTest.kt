package com.vastufirst.app.ui.newplan

import com.vastufirst.app.ui.common.ALL_ROOM_TYPES
import com.vastufirst.app.ui.common.GRID_ROOM_TYPES
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Changing a room's KIND — the correction the editor had no answer to until now.
 *
 * Two things are being proven, and they pull in opposite directions:
 *
 *  1. **It changes nothing geometric.** Every id, position and size survives, the list order
 *     survives, and therefore no editor invariant can be broken by a retype. The fuzz harness
 *     (Suite D, `--inject=retype-readds` / `retype-spreads` / `retype-drops-door`) proves the same
 *     three ways an implementation could get this wrong.
 *  2. **It does change the score.** That is the entire point. A corridor carries no rule in the
 *     ruleset, so a lobby read as one is NOT_SCORED and drops out of the weighted average entirely
 *     — the largest room in the owner's Gurgaon flat was not counted at all. The last two tests run
 *     the real engine and assert the new kind reaches it and moves the number.
 */
class RoomRetypeTest {

    private val engine = VastuEngine()

    private val home = listOf(
        GridRoom("a", RoomType.CORRIDOR, 0, 0, 3, 3),
        GridRoom("b", RoomType.KITCHEN, 3, 0, 2, 2),
        GridRoom("c", RoomType.BEDROOM, 3, 2, 2, 3),
    )

    // ── it changes exactly one thing ─────────────────────────────────────────────────────────────

    @Test
    fun `only the named room changes kind`() {
        val out = retypeRoom(home, "a", RoomType.LIVING)
        assertEquals(RoomType.LIVING, out.first { it.id == "a" }.type)
        assertEquals("b must be untouched", RoomType.KITCHEN, out.first { it.id == "b" }.type)
        assertEquals("c must be untouched", RoomType.BEDROOM, out.first { it.id == "c" }.type)
    }

    @Test
    fun `every rectangle and the list order survive a retype`() {
        val out = retypeRoom(home, "a", RoomType.LIVING)
        assertEquals("no room may be added or dropped", home.size, out.size)
        home.forEachIndexed { i, before ->
            val after = out[i]
            assertEquals("order must hold", before.id, after.id)
            assertEquals(before.col, after.col)
            assertEquals(before.row, after.row)
            assertEquals(before.w, after.w)
            assertEquals(before.h, after.h)
        }
    }

    @Test
    fun `retyping every room in turn never moves anything`() {
        // The exhaustive version of the above: each room, each of the nineteen kinds.
        for (room in home) {
            for (type in ALL_ROOM_TYPES) {
                val out = retypeRoom(home, room.id, type)
                val geometryBefore = home.map { it.id to listOf(it.col, it.row, it.w, it.h) }
                val geometryAfter = out.map { it.id to listOf(it.col, it.row, it.w, it.h) }
                assertEquals("retyping ${room.id} to $type moved something", geometryBefore, geometryAfter)
            }
        }
    }

    // ── nothing happens when nothing should ──────────────────────────────────────────────────────

    @Test
    fun `an unknown id returns the very same list`() {
        // Identity, not equality: the state write is equality-skipped, so a no-op must not mark the
        // draft dirty and bump a saved home's "updated" time.
        assertSame(home, retypeRoom(home, "nope", RoomType.LIVING))
    }

    @Test
    fun `re-picking the kind a room already is returns the very same list`() {
        assertSame(home, retypeRoom(home, "b", RoomType.KITCHEN))
    }

    @Test
    fun `a duplicate id retypes only the first room`() {
        // Ids are unique by construction; if that ever breaks, one tap must not silently retype two
        // rooms and move the score twice.
        val dupes = listOf(
            GridRoom("x", RoomType.BEDROOM, 0, 0, 2, 2),
            GridRoom("x", RoomType.BEDROOM, 2, 0, 2, 2),
        )
        val out = retypeRoom(dupes, "x", RoomType.TOILET)
        assertEquals(RoomType.TOILET, out[0].type)
        assertEquals(RoomType.BEDROOM, out[1].type)
    }

    @Test
    fun `retyping in an empty home is harmless`() {
        assertSame(emptyList<GridRoom>(), retypeRoom(emptyList(), "a", RoomType.LIVING))
    }

    // ── the picker offers every kind the app can hold ────────────────────────────────────────────

    @Test
    fun `the picker offers all nineteen kinds, once each`() {
        // ⭐ The reason this control exists at all. The plan reader can produce every RoomType, but
        // the "add a room" palette offers only eleven — so a room read as one of the other eight
        // could be removed and never put back. If a kind is ever added to the engine, this fails
        // until it is offered here too.
        assertEquals("no duplicates", ALL_ROOM_TYPES.size, ALL_ROOM_TYPES.toSet().size)
        assertEquals(RoomType.entries.toSet(), ALL_ROOM_TYPES.toSet())
    }

    @Test
    fun `the picker starts with the palette, in palette order`() {
        assertEquals(GRID_ROOM_TYPES, ALL_ROOM_TYPES.take(GRID_ROOM_TYPES.size))
    }

    @Test
    fun `the palette alone cannot reach every kind`() {
        // Pins the gap this feature closes, so the claim in ALL_ROOM_TYPES' comment stays true or
        // this test tells us it stopped being true.
        val unreachable = RoomType.entries.toSet() - GRID_ROOM_TYPES.toSet()
        assertTrue("the palette gap has closed — update the comment", unreachable.isNotEmpty())
        assertTrue("a corridor must be unreachable from the palette", RoomType.CORRIDOR in unreachable)
    }

    // ── and it really does move the score ────────────────────────────────────────────────────────

    @Test
    fun `the engine scores the room as its NEW kind`() {
        // The direct claim, with no arithmetic to be lucky about: what reaches the engine is the kind
        // the user picked. This is what makes the retype a scoring operation rather than a caption.
        val door = GridDoor(DoorSide.N, 1)
        val out = retypeRoom(home, "a", RoomType.LIVING)
        val plan = buildEnginePlan(out, door, Intent.LIVING, PropertyType.FLAT, 0, "t")!!
        val result = engine.analyze(plan).roomResults.first { it.roomId == "a" }
        assertEquals(RoomType.LIVING, result.type)
        assertEquals("and it is weighted as a living room, not as circulation", 1.5, result.weight, 1e-9)
    }

    @Test
    fun `changing a room's kind changes the score`() {
        // ⚠ Driven from the app's own sample home, NOT from the three-room fixture above. My first
        // version used that one and the engine returned 0 for every kind — a plan too sparse to
        // assess comes back INSUFFICIENT rather than as a number (the no-error-state rule), so the
        // test was asking a question the engine had politely declined to answer. Same inputs as the
        // render fixtures, which are already known to produce a real analysis.
        val sample = SamplePlans.all.first()
        fun analysisFor(type: RoomType) = engine.analyze(
            buildEnginePlan(
                retypeRoom(sample.rooms, "s2", type),   // s2 is the sample's plain BEDROOM
                sample.door, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, sample.north, "t",
            )!!,
        )

        // Self-diagnosing: if this ever fails, the fixture stopped being scoreable and the assertion
        // below would be meaningless rather than wrong.
        val asDrawn = analysisFor(RoomType.BEDROOM)
        assertNotEquals(
            "the sample home must be scoreable or this test proves nothing",
            AnalysisQuality.INSUFFICIENT, asDrawn.quality,
        )

        val kinds = listOf(
            RoomType.BEDROOM,          // 1.5 — as drawn
            RoomType.MASTER_BEDROOM,   // 3.0 — the "which one is the master?" question, answerable now
            RoomType.CORRIDOR,         // unruled: NOT_SCORED, drops out of both sums entirely
            RoomType.TOILET,           // 2.5
        )
        val results = kinds.associateWith(::analysisFor)
        val bases = results.mapValues { it.value.base }
        val scores = results.mapValues { it.value.score }

        // Asserted on the PRE-ROUNDING weighted average: moving a room between 1.5, 3.0, 2.5 and
        // "excluded" must move it as a matter of arithmetic, whereas two rounded scores could in
        // principle collide by luck — and a test that fails for a reason that is not a defect costs
        // a cloud round-trip. The scores are carried in the message so a failure is readable.
        assertTrue(
            "a retype that cannot move the score is not worth building — bases $bases, scores $scores",
            bases.values.toSet().size > 1,
        )
    }

    @Test
    fun `a retype leaves the footprint the engine scores untouched`() {
        // Why the front door cannot move: the door is clamped to the rooms' bounding box, and a
        // retype cannot change that box. This is the Kotlin twin of the fuzz harness's RETYPE-DOOR.
        val door = GridDoor(DoorSide.N, 1)
        val out = retypeRoom(home, "a", RoomType.LIVING)
        assertEquals(clampDoorToRooms(door, home), clampDoorToRooms(door, out))
        val before = buildEnginePlan(home, door, Intent.LIVING, PropertyType.FLAT, 0, "t")!!
        val after = buildEnginePlan(out, door, Intent.LIVING, PropertyType.FLAT, 0, "t")!!
        assertEquals("the outline must be identical", before.levels.first().outline, after.levels.first().outline)
        assertEquals("the door must be identical", before.levels.first().doors, after.levels.first().doors)
    }
}
