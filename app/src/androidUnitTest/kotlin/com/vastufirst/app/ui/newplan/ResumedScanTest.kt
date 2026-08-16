package com.vastufirst.app.ui.newplan

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.vastufirst.app.platform.AndroidPlanPhotoStore
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.VastuDatabaseFactory
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.scan.AssistReason
import com.vastufirst.shared.scan.ScanNotes
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * ⭐⭐ THE PHOTOGRAPH SURVIVES BEING LEFT (owner, 16 Aug 2026: *"Do A"*).
 *
 * WHAT HE REPORTED, and why the first answer was wrong. He said "Carry on" on an unfinished home was
 * still opening the manual grid. It was not: the routing added that morning was working, and he was
 * landing on the North dial exactly as intended. But a home read off a photograph had its photograph
 * thrown away the moment he walked off the screen — the picture lived in one in-memory slot and was
 * documented as never persisted — so the dial came up over our own redrawing of his rooms: coloured
 * squares on a grid. Visually that IS the builder's canvas he had asked to be kept out of the photo
 * flow, arriving on the very screen built to replace it. The bug was real; the diagnosis was one
 * screen off.
 *
 * AND THE SECOND THING HE FOUND, in the same message: leaving at the list of rooms we had just read
 * kept nothing at all. The hand-over into the home only happened on "use these rooms", so backing out
 * one screen earlier discarded a finished, already-paid-for read.
 *
 * These run against Robolectric's own SQLite through the app's real repository AND the real photo
 * store writing real files — a fake either side would be testing the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ResumedScanTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var repo: PlanRepository
    private lateinit var photos: AndroidPlanPhotoStore
    private val engine = VastuEngine()
    private var clock = 1_000L

    /** ONE dispatcher, shared by setMain and every runTest — see the note in UnfinishedHomeTest. */
    private val main = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        driver = AndroidSqliteDriver(VastuDatabase.Schema, RuntimeEnvironment.getApplication(), null)
        photos = AndroidPlanPhotoStore(RuntimeEnvironment.getApplication())
        // Robolectric hands each test its own files directory, but emptying it is what makes a
        // failure here mean "this test", not "the one before it left a picture behind".
        runBlocking { photos.deleteAll() }
        repo = PlanRepository(
            VastuDatabaseFactory.create(driver),
            Dispatchers.Unconfined,
            photos = photos,
        )
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    private fun newSession() = NewPlanViewModel(engine, repo, now = { clock++ }, compute = main)

    private val notes = ScanNotes(coverage = 0.42, areaVariation = 0.7, modelConfidence = 0.95)

    /** A read that PLACED its rooms — the path that goes to the North dial and never to the grid. */
    private fun placed() = ScanOutcome.Placed(
        cols = 8,
        rows = 8,
        rooms = listOf(
            ScannedRoom(RoomType.LIVING, "LIVING 12'-0\"X14'-6\"", CellRect(0, 0, 3, 2)),
            ScannedRoom(RoomType.KITCHEN, "KITCHEN 8'-0\"X7'-0\"", CellRect(4, 0, 2, 2)),
        ),
        notes = notes,
    )

    /** A read that identified rooms but could not place them — genuinely finished on the grid. */
    private fun assisted() = ScanOutcome.Assisted(
        rooms = listOf(ScannedRoom(RoomType.BEDROOM, "BEDROOM 1", null)),
        reason = AssistReason.TOO_MANY_ROOMS,
        notes = notes,
    )

    private val photoBytes = ByteArray(64) { it.toByte() }

    // ── the reported bug ─────────────────────────────────────────────────────────────────────────

    /**
     * ⭐⭐ THE ONE THAT MATTERS. A photographed home picked back up shows the reader THEIR plan.
     *
     * Without the stored picture the North dial has nothing to draw but our coloured squares, which
     * is the screen the owner reported as "Carry on takes it to Manual grid".
     */
    @Test
    fun `a photographed home comes back with its own photograph`() = runTest(main) {
        val vm = newSession()
        vm.acceptScan(placed(), photoBytes)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id

        val resumed = newSession()
        resumed.resumeDraft(id)
        advanceUntilIdle()

        assertTrue(
            "the home must still know it came off a photograph, or it opens the grid editor",
            resumed.fromScan,
        )
        assertTrue(
            "the North dial has nothing to draw but coloured squares without the picture itself",
            photoBytes.contentEquals(resumed.scanPhoto),
        )
        assertEquals(
            "and nothing can be drawn ON the picture without the boxes it was read from",
            2, resumed.scanRooms.size,
        )
        assertEquals(
            "the plan's own printed caption is what the report quotes back",
            "LIVING 12'-0\"X14'-6\"", resumed.scanRooms.first().label,
        )
    }

    /** A home drawn by hand has no photograph, and must not inherit one that is lying around. */
    @Test
    fun `a hand-drawn home comes back with no photograph`() = runTest(main) {
        val vm = newSession()
        vm.updateRooms(listOf(GridRoom("r1", RoomType.LIVING, 0, 0, 3, 2)))
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id

        val resumed = newSession()
        resumed.resumeDraft(id)
        advanceUntilIdle()
        assertFalse("nothing about this home was ever photographed", resumed.fromScan)
        assertNull("and a picture from somebody else's plan must not appear under it", resumed.scanPhoto)
        assertTrue(resumed.scanRooms.isEmpty())
    }

    // ── the second thing he found ────────────────────────────────────────────────────────────────

    /**
     * ⭐⭐ A finished read is kept the moment it lands, not when a button is pressed.
     *
     * The reader is still looking at the list of rooms we read; they have tapped nothing. Leaving
     * now used to discard the whole read — a real network call, really paid for.
     */
    @Test
    fun `the read is kept as soon as it succeeds, before use these rooms is tapped`() = runTest(main) {
        val vm = newSession()
        vm.acceptScan(placed(), photoBytes)      // exactly what the scan screen's effect does
        advanceUntilIdle()

        val row = repo.observeDrafts().first().singleOrNull()
        assertNotNull("leaving at the rooms list must not throw the reading away", row)
        assertEquals("the rooms we read are on it", 2, row!!.roomCount)
        assertTrue("and it is marked as a photographed home", row.draft.fromScan)
        assertNotNull("the picture is kept beside it", repo.loadDraftPhoto(row.id))
    }

    /** An unplaced read is worth keeping too — it is the same paid call, and the same rooms. */
    @Test
    fun `an unplaced read is kept as well, and still opens the grid it needs`() = runTest(main) {
        val vm = newSession()
        vm.acceptScan(assisted(), photoBytes)
        advanceUntilIdle()

        val row = repo.observeDrafts().first().single()
        assertEquals(1, row.roomCount)
        assertFalse(
            "the grid is the right screen for a read whose rooms nobody could place",
            row.draft.fromScan,
        )
    }

    // ── the guard that lets it be called twice ───────────────────────────────────────────────────

    /**
     * ⭐ Taking the same read twice must change nothing.
     *
     * The scan screen accepts the read when it lands AND when "use these rooms" is tapped, and it is
     * disposed and recomposed every time the reader walks forward and presses Back. Without the
     * guard, returning to the scanner would rebuild the rooms and re-read the front door over a door
     * the reader had placed with their own finger two screens later.
     */
    @Test
    fun `taking the same read twice does not move a door the reader has since placed`() = runTest(main) {
        val vm = newSession()
        val read = placed()
        vm.acceptScan(read, photoBytes)
        advanceUntilIdle()

        val chosen = GridDoor(DoorSide.S, 2)
        vm.updateDoor(chosen)
        advanceUntilIdle()

        vm.acceptScan(read, photoBytes)          // back to the scanner, same reading
        advanceUntilIdle()
        assertEquals("the reader's own door must survive going back a screen", chosen, vm.door)
    }

    // ── the picture never outlives its home ──────────────────────────────────────────────────────

    /** Throwing away an unfinished home must take its photograph with it. */
    @Test
    fun `throwing away an unfinished home takes its photograph with it`() = runTest(main) {
        val vm = newSession()
        vm.acceptScan(placed(), photoBytes)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id
        assertNotNull(repo.loadDraftPhoto(id))

        repo.clearDraft(id)
        assertNull(
            "a picture of somebody's home whose home no longer exists must not stay on the phone",
            repo.loadDraftPhoto(id),
        )
    }

    /**
     * "Delete all my data" promises every home on the device. A photograph of somebody's own floor
     * plan is the most personal thing this app holds — the wipe that leaves it behind is the defect
     * this project already shipped once, when the databases were emptied and the camera's folder
     * was not.
     */
    @Test
    fun `delete all my data removes the photographs too`() = runTest(main) {
        val vm = newSession()
        vm.acceptScan(placed(), photoBytes)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id
        assertNotNull(repo.loadDraftPhoto(id))

        repo.deleteAll()
        assertTrue(repo.observeDrafts().first().isEmpty())
        assertNull("every photograph goes with them", repo.loadDraftPhoto(id))
    }

    /** Finishing a home retires its unfinished row, and the picture kept for that row goes too. */
    @Test
    fun `finishing a home clears the photograph kept for its unfinished row`() = runTest(main) {
        val vm = newSession()
        vm.intent = com.vastufirst.shared.Intent.BUYING
        vm.acceptScan(placed(), photoBytes)
        advanceUntilIdle()
        val id = repo.observeDrafts().first().single().id
        assertNotNull(repo.loadDraftPhoto(id))

        vm.save()
        advanceUntilIdle()
        assertTrue("the unfinished row has done its job", repo.observeDrafts().first().isEmpty())
        assertNull("and nothing is left holding a picture for it", repo.loadDraftPhoto(id))
        assertTrue(
            "the report this save opens still draws the reader's own plan, from memory",
            photoBytes.contentEquals(vm.scanPhoto),
        )
    }
}
