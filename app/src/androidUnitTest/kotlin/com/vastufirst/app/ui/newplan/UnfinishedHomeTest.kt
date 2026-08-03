package com.vastufirst.app.ui.newplan

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.VastuDatabaseFactory
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.RoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ⭐⭐ THE OWNER'S FIRST FIX (v0.6.6), pinned end to end against a REAL database.
 *
 * What he reported: a scan he had not finished placing came back on its own. Entering the drawing
 * flow for ANY reason — "add a home", "draw it on a grid", "upload a plan" — silently reloaded
 * whatever half-finished work was on disk, so a fresh start was not a fresh start. What he asked for:
 * the unfinished work is kept, listed, and loaded ONLY when he picks it.
 *
 * The four promises, each with its own test below:
 *  1. work in progress is saved by itself, with nothing to press;
 *  2. a NEW session does not pick it up — this is the fix, and it is the one a comment cannot prove;
 *  3. asking for it by id brings it back exactly as it was;
 *  4. finishing a home takes its unfinished row away, so it is never listed twice.
 *
 * Run against Robolectric's own SQLite through the app's real driver and real repository — a fake
 * repository here would be testing the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UnfinishedHomeTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var repo: PlanRepository
    private val engine = VastuEngine()
    private var clock = 1_000L

    /**
     * ⚠ ONE dispatcher, shared by `Dispatchers.setMain` and by every `runTest` below (each test
     * passes it in). Two `UnconfinedTestDispatcher()`s would carry two different virtual clocks, and
     * `advanceUntilIdle` would silently advance the wrong one — the autosave here is debounced, so
     * the tests would pass or fail on timing rather than on behaviour.
     */
    private val main = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // A null database name is an in-memory database — fresh for every test, nothing on disk.
        driver = AndroidSqliteDriver(VastuDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        repo = PlanRepository(VastuDatabaseFactory.create(driver), Dispatchers.Unconfined)
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    /**
     * A ViewModel wired exactly as the app wires it, on a clock the test controls — and with scoring
     * pinned to the test dispatcher, so "save the home, then drop its unfinished row" completes
     * inside the test rather than on a background thread the assertions would race.
     */
    private fun newSession() = NewPlanViewModel(engine, repo, now = { clock++ }, compute = main)

    private val someRooms = listOf(
        GridRoom("r1", RoomType.LIVING, 0, 0, 3, 2),
        GridRoom("r2", RoomType.KITCHEN, 4, 0, 2, 2),
    )

    @Test
    fun `work in progress saves itself, with nothing to press`() = runTest(main) {
        val vm = newSession()
        vm.intent = Intent.BUYING
        vm.updateRooms(someRooms)
        advanceUntilIdle()

        val listed = repo.observeDrafts().first()
        assertEquals("the half-drawn home must be on disk without anyone saving it", 1, listed.size)
        assertEquals(2, listed.single().roomCount)
    }

    /**
     * ⭐ THE FIX. A brand-new session is what "add a home", "draw it on a grid" and "upload a plan"
     * all create, and none of them may adopt the last unfinished home.
     */
    @Test
    fun `starting a new home does not bring back the unfinished one`() = runTest(main) {
        val first = newSession()
        first.updateRooms(someRooms)
        advanceUntilIdle()
        assertEquals(1, repo.observeDrafts().first().size)

        val fresh = newSession()
        advanceUntilIdle()
        assertTrue(
            "a new home must start empty — this is the whole of the owner's report",
            fresh.rooms.isEmpty(),
        )
        assertTrue("and it must not claim to have restored anything", !fresh.restoredFromDraft)
        assertEquals(
            "and the earlier work must still be safe on disk, waiting to be chosen",
            1, repo.observeDrafts().first().size,
        )
    }

    /** And drawing in that fresh session must not overwrite the first home's only copy. */
    @Test
    fun `a second unfinished home sits beside the first, not on top of it`() = runTest(main) {
        newSession().apply { updateRooms(someRooms) }
        advanceUntilIdle()
        newSession().apply { updateRooms(listOf(GridRoom("x", RoomType.BEDROOM, 0, 0, 2, 2))) }
        advanceUntilIdle()

        val listed = repo.observeDrafts().first()
        assertEquals("both unfinished homes must survive", 2, listed.size)
        assertEquals(setOf(1, 2), listed.map { it.roomCount }.toSet())
    }

    @Test
    fun `picking the unfinished home brings it back exactly as it was`() = runTest(main) {
        val first = newSession()
        first.updateRooms(someRooms)
        first.updateNorth(137)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id

        val resumed = newSession()
        resumed.resumeDraft(id)
        advanceUntilIdle()

        assertEquals(someRooms.map { it.id }, resumed.rooms.map { it.id })
        assertEquals(137, resumed.north)
        assertTrue("the editor must say it brought work back", resumed.restoredFromDraft)
    }

    /** A home that is finished is a saved home, and must never also be listed as unfinished. */
    @Test
    fun `finishing a home takes its unfinished row away`() = runTest(main) {
        val vm = newSession()
        vm.intent = Intent.BUYING
        vm.updateRooms(someRooms)
        vm.updateDoor(GridDoor(DoorSide.N, 2))
        advanceUntilIdle()
        assertEquals(1, repo.observeDrafts().first().size)

        vm.save()
        advanceUntilIdle()

        assertTrue(
            "a finished home must not still be offered as unfinished work",
            repo.observeDrafts().first().isEmpty(),
        )
        assertEquals("and it must be in the saved homes", 1, repo.observePlans().first().plans.size)
    }

    /**
     * ⭐ A scan whose rooms could not be PLACED comes back still parked, not pretending to be a plan.
     *
     * ⚠ This is the defect that resuming-on-purpose created. The "these are parked, drag them" flag
     * was never stored, on the grounds that a home restored after a background kill is whatever the
     * user last left. Once the user resumes deliberately, that leaves an untouched parking row coming
     * back under the heading "Place your rooms" — and then being asked whether the gaps between the
     * squares are part of their home. That is the exact screen the owner was handed for his own flat.
     */
    @Test
    fun `a scan nobody has placed yet comes back still parked`() = runTest(main) {
        val vm = newSession()
        vm.updateRooms(someRooms)
        vm.markRoomsUnplaced(true)          // exactly what the scan hand-over does, in that order
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id

        val resumed = newSession()
        resumed.resumeDraft(id)
        advanceUntilIdle()
        assertTrue(
            "an untouched parking row must not come back looking like a finished plan",
            resumed.roomsUnplaced,
        )
    }

    /** And the moment one room is moved it is a plan, and must never be re-parked afterwards. */
    @Test
    fun `once a room has been moved the home is never parked again`() = runTest(main) {
        val vm = newSession()
        vm.updateRooms(someRooms)
        vm.markRoomsUnplaced(true)
        vm.updateRooms(someRooms.map { if (it.id == "r1") it.copy(col = 1) else it })
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id

        val resumed = newSession()
        resumed.resumeDraft(id)
        advanceUntilIdle()
        assertTrue(
            "arranging the rooms makes them the user's layout — re-parking would forget their work",
            !resumed.roomsUnplaced,
        )
    }

    /** Throwing it away means throwing it away — the row goes, not just the rooms on screen. */
    @Test
    fun `starting this home again removes the row it came from`() = runTest(main) {
        val vm = newSession()
        vm.updateRooms(someRooms)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id
        assertNotNull(repo.loadDraft(id))

        vm.startAgain()
        advanceUntilIdle()

        assertNull("the thrown-away home must not come back", repo.loadDraft(id))
        assertTrue(repo.observeDrafts().first().isEmpty())
    }

    /** Passing through the flow without drawing anything must leave nothing behind to be offered. */
    @Test
    fun `a session that draws nothing leaves no unfinished home`() = runTest(main) {
        val vm = newSession()
        vm.intent = Intent.LIVING
        vm.updateNorth(90)
        advanceUntilIdle()
        assertTrue(
            "an empty grid is not work anybody would recognise as their home",
            repo.observeDrafts().first().isEmpty(),
        )
    }
}
