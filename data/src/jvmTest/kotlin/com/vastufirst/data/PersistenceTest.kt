package com.vastufirst.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.Cell
import com.vastufirst.shared.editor.DraftDoor
import com.vastufirst.shared.editor.DraftRoom
import com.vastufirst.shared.editor.DraftSnapshot
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.scan.ScannedRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ⭐ THE TWO WAYS A USER LOSES DATA, both proven against a REAL SQLite database rather than a mock.
 *
 *  1. **One bad row used to empty the whole list.** Every read went through `Intent.valueOf`,
 *     `PropertyType.valueOf` and a JSON decode, all inside the saved-homes flow. Any one of them
 *     throwing — a rule rename, a field a newer build wrote, a row half-written by a phone that
 *     died mid-save — took down every home on the screen. Appearing to have deleted all of
 *     somebody's homes is the end of their trust in an app they paid for.
 *
 *  2. **A half-drawn home lived only in memory.** Android reclaims a backgrounded app whenever it
 *     wants the RAM; on a cheap phone, taking a call is enough.
 *
 * And the migration path, which had never existed, is exercised here for real: a database created
 * at the OLD schema version, with homes in it, is upgraded and every home is still there.
 */
class PersistenceTest {

    private lateinit var driver: SqlDriver
    private lateinit var repo: PlanRepository

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VastuDatabase.Schema.create(driver)
        repo = PlanRepository(VastuDatabaseFactory.create(driver), Dispatchers.Unconfined)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun plan(id: String) = Plan(
        id = id,
        propertyType = PropertyType.INDEPENDENT_HOUSE,
        intent = Intent.BUILDING,
        levels = listOf(
            Level(
                index = 0,
                outline = listOf(Point(0.0, 0.0), Point(8.0, 0.0), Point(8.0, 8.0), Point(0.0, 8.0)),
            ),
        ),
        northOffsetDegrees = 0,
    )

    private fun saved(id: String, name: String) = SavedPlan(
        id = id, name = name, intent = Intent.BUILDING, propertyType = PropertyType.INDEPENDENT_HOUSE,
        plan = plan(id), score = 42, ruleSetVersion = "t", unlocked = false, createdAt = 1L, updatedAt = 1L,
    )

    /** Write a row straight past the repository, so it can hold something the repository would never write. */
    private fun writeRawRow(id: String, intent: String, propertyType: String, planJson: String) {
        driver.execute(
            null,
            """
            INSERT OR REPLACE INTO planEntity
              (id, name, intent, propertyType, northOffset, planJson, score, ruleSetVersion, unlocked, createdAt, updatedAt)
            VALUES ('$id', 'Broken home', '$intent', '$propertyType', 0, '$planJson', 10, 't', 0, 1, 1)
            """.trimIndent(),
            0,
        )
    }

    // ── re-scoring a home after a Vastu ruling moves ───────────────────────────────────────────

    /**
     * ⭐ When we change a Vastu ruling, every home saved under the old rules is re-run and the owner
     * is shown the old number, the new number and the reason — and only then is anything written
     * back. This is that write.
     *
     * ⚠ `updatedAt` must NOT move. The saved-homes list is ordered by it and shows "updated today"
     * next to each row. WE changed the rules; the user did not touch their home, and telling them
     * they edited it — and shuffling their list — would be a second small dishonesty on top of a
     * number they never asked to move.
     */
    @Test
    fun `re-scoring after a ruling updates the score and the rule version, and nothing else`() = runTest {
        repo.save(saved("h1", "Dwarka flat"), now = 1_000L)
        val before = assertNotNull(repo.getPlan("h1"))

        repo.setRescored("h1", score = 55, ruleSetVersion = "2026.08.01-1")

        val after = assertNotNull(repo.getPlan("h1"))
        assertEquals(55, after.score, "the new score is stored")
        assertEquals("2026.08.01-1", after.ruleSetVersion, "and the version it was scored under")
        assertEquals(before.updatedAt, after.updatedAt, "we changed the rules — the user did not edit this home")
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(before.name, after.name)
        assertEquals(before.unlocked, after.unlocked)
        assertEquals(before.plan, after.plan, "the home itself is untouched — only how we read it changed")
    }

    // ── one bad row must not take the others with it ───────────────────────────────────────────

    @Test
    fun `a row with an unknown intent is quarantined, and the good homes still load`() = runTest {
        repo.save(saved("good-1", "Kitchen flat"), now = 10L)
        repo.save(saved("good-2", "Site plot"), now = 20L)
        writeRawRow("bad", intent = "RENOVATING_LATER", propertyType = "INDEPENDENT_HOUSE", planJson = "{}")

        val result = repo.observePlans().first()
        assertEquals(2, result.plans.size, "the readable homes must still be there")
        assertEquals(1, result.unreadable.size, "and the app must know one is missing, so it can say so")
        assertEquals(setOf("good-1", "good-2"), result.plans.map { it.id }.toSet())
    }

    @Test
    fun `a row with unreadable plan JSON is quarantined`() = runTest {
        repo.save(saved("good", "Fine"), now = 10L)
        writeRawRow("bad", intent = "BUILDING", propertyType = "FLAT", planJson = "not json at all {{{")
        val result = repo.observePlans().first()
        assertEquals(1, result.plans.size)
        assertEquals(1, result.unreadable.size)
    }

    @Test
    fun `a quarantined row keeps its NAME, so the screen can say which home it is`() = runTest {
        // ⭐ What broke the row is its plan JSON — the name is a different column and still reads.
        // "Broken home can't be opened" gives the user a handle; "1 home can't be opened" gave them
        // a count and nothing to act on (audit B7: the only delete anywhere was delete-ALL-data).
        writeRawRow("bad", intent = "BUILDING", propertyType = "FLAT", planJson = "{")
        val quarantined = repo.observePlans().first().unreadable.single()
        assertEquals("bad", quarantined.id, "the id is the handle remove-this-home needs")
        assertEquals("Broken home", quarantined.name)
    }

    @Test
    fun `removing an unreadable home deletes THAT row and touches nothing else`() = runTest {
        // The escape audit B7 asked for: per-home, not delete-everything — and it must not be able
        // to take a healthy neighbour with it.
        repo.save(saved("good", "Fine"), now = 10L)
        writeRawRow("bad", intent = "BUILDING", propertyType = "FLAT", planJson = "{")

        repo.delete("bad")

        val after = repo.observePlans().first()
        assertEquals(0, after.unreadable.size, "the broken row is gone")
        assertEquals("good", after.plans.single().id, "and the healthy home is untouched")
    }

    @Test
    fun `a row with an unknown property type is quarantined`() = runTest {
        repo.save(saved("good", "Fine"), now = 10L)
        writeRawRow("bad", intent = "BUILDING", propertyType = "HOUSEBOAT", planJson = "{}")
        assertEquals(1, repo.observePlans().first().unreadable.size)
    }

    @Test
    fun `a plan written with an unknown extra field still opens`() = runTest {
        // ⭐ The forward-compatibility half: a home saved by a NEWER build must open in an older one
        // rather than being declared corrupt, or every user who downgrades loses everything.
        val json = """
            {"id":"future","propertyType":"FLAT","intent":"BUYING",
             "levels":[{"index":0,"outline":[{"x":0.0,"y":0.0},{"x":4.0,"y":0.0},{"x":4.0,"y":4.0},{"x":0.0,"y":4.0}],
             "rooms":[],"doors":[],"fixtures":[]}],
             "northOffsetDegrees":0,"somethingWeHaveNotInventedYet":{"a":1}}
        """.trimIndent().replace("\n", " ")
        writeRawRow("future", intent = "BUYING", propertyType = "FLAT", planJson = json)
        val result = repo.observePlans().first()
        assertEquals(0, result.unreadable.size, "an unknown field must not make a home unreadable")
        assertEquals("future", result.plans.single().id)
    }

    @Test
    fun `opening a single bad home by id returns nothing instead of throwing`() = runTest {
        writeRawRow("bad", intent = "BUILDING", propertyType = "FLAT", planJson = "{")
        assertNull(repo.getPlan("bad"))
        assertNull(repo.observePlan("bad").first())
    }

    @Test
    fun `a quarantined row is never deleted`() = runTest {
        writeRawRow("bad", intent = "NOPE", propertyType = "FLAT", planJson = "{}")
        repo.observePlans().first()
        val stillThere = driver.executeQuery(
            null, "SELECT COUNT(*) FROM planEntity WHERE id = 'bad'", { c -> app.cash.sqldelight.db.QueryResult.Value(if (c.next().value) c.getLong(0) else 0L) }, 0,
        ).value
        assertEquals(1L, stillThere, "a home we cannot read today may be readable after an update — never delete it")
    }

    // ── the unfinished home survives being killed ──────────────────────────────────────────────

    private val draft = DraftSnapshot(
        name = "Home 3",
        intent = Intent.BUYING,
        propertyType = PropertyType.FLAT,
        north = 137,
        gridCols = 9,
        gridRows = 6,
        rooms = listOf(
            DraftRoom("r1", RoomType.KITCHEN, 0, 0, 2, 2),
            DraftRoom("r2", RoomType.POOJA, 3, 1, 1, 1),
        ),
        door = DraftDoor("S", 4),
        cutOut = listOf(Cell(7, 0), Cell(8, 0)),
        kept = listOf(Cell(5, 5)),
        // ⭐ The rooms as the READER saw them on the page, which is a different thing from the rooms
        // above — see DraftSnapshot.scanRooms. They are in this fixture rather than in a test of
        // their own so that "every part of the draft must survive" keeps meaning every part: the
        // printed caption lives only here, and losing it silently is what made a resumed home's paid
        // report call "MASTER BEDROOM 1" plain "Master".
        fromScan = true,
        scanRooms = listOf(
            ScannedRoom(RoomType.KITCHEN, "KITCHEN 8'-0\"X7'-0\"", CellRect(0, 0, 2, 2)),
            ScannedRoom(RoomType.POOJA, "POOJA", CellRect(3, 1, 1, 1)),
        ),
    )

    @Test
    fun `a half-drawn home comes back exactly as it was`() = runTest {
        repo.saveDraft("d1", draft, now = 99L)
        assertEquals(draft, repo.loadDraft("d1"), "every part of the draft must survive, not just the rooms")
    }

    @Test
    fun `there is no draft until one is written, and none after it is cleared`() = runTest {
        assertNull(repo.loadDraft("d1"))
        repo.saveDraft("d1", draft, now = 1L)
        assertNotNull(repo.loadDraft("d1"))
        repo.clearDraft("d1")
        assertNull(repo.loadDraft("d1"), "finishing a home must leave no draft behind to be restored later")
    }

    @Test
    fun `writing the same unfinished home again replaces it rather than piling up`() = runTest {
        repo.saveDraft("d1", draft, now = 1L)
        repo.saveDraft("d1", draft.copy(name = "Home 4", north = 5), now = 2L)
        assertEquals("Home 4", repo.loadDraft("d1")?.name)
        assertEquals(5, repo.loadDraft("d1")?.north)
        assertEquals(1, repo.observeDrafts().first().size, "one home being drawn is one row, not two")
    }

    /**
     * ⭐ THE OWNER'S FIX (v0.6.6). Starting a second home must not overwrite the first one's only
     * copy. Under the old single-row draft it did — silently — and the first home's work was gone
     * with nothing on screen to say so.
     */
    @Test
    fun `starting a second home does not overwrite the first unfinished one`() = runTest {
        repo.saveDraft("d1", draft, now = 1L)
        repo.saveDraft("d2", draft.copy(name = "Home 9", north = 42), now = 2L)
        assertEquals("Home 3", repo.loadDraft("d1")?.name, "the first unfinished home must survive")
        assertEquals("Home 9", repo.loadDraft("d2")?.name)
        val listed = repo.observeDrafts().first()
        assertEquals(2, listed.size, "both unfinished homes must be offered back")
        assertEquals(listOf("d2", "d1"), listed.map { it.id }, "newest first, like the finished homes")
        assertEquals(2, listed.first().roomCount, "the list must say how much is on the grid")
    }

    /** An empty grid is not work anybody would recognise; it must never be offered back as a home. */
    @Test
    fun `an unfinished home with nothing drawn in it is not listed`() = runTest {
        repo.saveDraft("empty", DraftSnapshot(name = "Home 1"), now = 1L)
        repo.saveDraft("real", draft, now = 2L)
        assertEquals(listOf("real"), repo.observeDrafts().first().map { it.id })
    }

    /**
     * ⭐ "Delete all my data" promises every home on the device. An unfinished home holds the same
     * room layout a finished one does, so the wipe must cover drafts too. Found leaking in the
     * 4 Aug 2026 audit: the button cleared the saved homes and left every draft behind.
     */
    @Test
    fun `delete all really deletes the unfinished homes too`() = runTest {
        repo.save(saved("p1", "Home 1"), now = 1L)
        repo.saveDraft("d1", draft, now = 2L)
        repo.deleteAll()
        assertTrue(repo.observePlans().first().plans.isEmpty(), "every finished home gone")
        assertTrue(repo.observeDrafts().first().isEmpty(), "every unfinished home gone too")
        assertNull(repo.loadDraft("d1"), "and not loadable by id either")
    }

    @Test
    fun `an unreadable draft is treated as no draft, never as a crash`() = runTest {
        driver.execute(null, "INSERT INTO draftEntity(id, draftJson, updatedAt) VALUES ('current','{{{',1)", 0)
        assertNull(repo.loadDraft("current"), "a draft is a convenience; a broken one must not stop a new home")
        assertTrue(
            repo.observeDrafts().first().isEmpty(),
            "and one broken row must not take the whole list down with it",
        )
    }

    /**
     * ⭐ The single `current` row older builds wrote is a perfectly good id, so an unfinished home
     * drawn on v0.6.5 appears in the new list on first launch instead of being stranded. No migration
     * — that is the whole reason the id column was already a free-form key.
     */
    @Test
    fun `an unfinished home left by an older build is offered back, not stranded`() = runTest {
        repo.saveDraft("current", draft, now = 7L)
        val listed = repo.observeDrafts().first()
        assertEquals(listOf("current"), listed.map { it.id })
        assertEquals(draft, listed.single().draft)
    }

    @Test
    fun `a draft written by an older build still loads`() = runTest {
        // Every field has a default precisely so this works. A draft holding only rooms — all that an
        // early version wrote — must still come back rather than being thrown away.
        driver.execute(
            null,
            """INSERT INTO draftEntity(id, draftJson, updatedAt) VALUES ('current',
               '{"rooms":[{"id":"r1","type":"KITCHEN","col":1,"row":1,"w":2,"h":2}]}', 1)""".trimIndent(),
            0,
        )
        val loaded = assertNotNull(repo.loadDraft("current"))
        assertEquals(1, loaded.rooms.size)
        assertEquals(0, loaded.north)
        assertNull(loaded.door)
    }

    /**
     * ⭐ A scan nobody has placed yet must come back still parked. The flag was added to this file
     * format in v0.6.6; every field carries a default precisely so adding one cannot strand a draft
     * an older build wrote.
     */
    @Test
    fun `an unplaced scan survives the round trip, and an older draft defaults to placed`() = runTest {
        repo.saveDraft("parked", draft.copy(roomsUnplaced = true), now = 1L)
        assertEquals(true, repo.loadDraft("parked")?.roomsUnplaced)

        driver.execute(
            null,
            """INSERT INTO draftEntity(id, draftJson, updatedAt) VALUES ('old',
               '{"rooms":[{"id":"r1","type":"KITCHEN","col":1,"row":1,"w":2,"h":2}]}', 1)""".trimIndent(),
            0,
        )
        assertEquals(
            false, repo.loadDraft("old")?.roomsUnplaced,
            "a draft written before the flag existed is a home the user was drawing, not a parking row",
        )
    }

    @Test
    fun `an empty draft knows it is empty`() {
        assertTrue(DraftSnapshot().isEmpty, "nothing drawn means nothing worth restoring")
        assertTrue(!draft.isEmpty)
    }

    // ── ⭐ THE UPGRADE PATH, which until now did not exist ─────────────────────────────────────

    @Test
    fun `the schema is versioned, so a future change has somewhere to go`() {
        assertEquals(
            2L, VastuDatabase.Schema.version,
            "the numbered migration is not being picked up — without it, the next schema change has " +
                "no upgrade path and the usual shortcut is to drop the table, which erases every " +
                "home the user has saved",
        )
    }

    @Test
    fun `a phone holding the old database upgrades without losing a single home`() = runTest {
        val old = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            // The version-1 schema, written out exactly as it shipped — NOT generated from today's
            // .sq files, which would make this test agree with itself no matter what changed.
            old.execute(
                null,
                """
                CREATE TABLE planEntity (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, intent TEXT NOT NULL,
                    propertyType TEXT NOT NULL, northOffset INTEGER NOT NULL, planJson TEXT NOT NULL,
                    score INTEGER NOT NULL, ruleSetVersion TEXT NOT NULL,
                    unlocked INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
            val before = PlanRepository(VastuDatabaseFactory.create(old), Dispatchers.Unconfined)
            before.save(saved("keep-1", "Gurgaon flat"), now = 1L)
            before.save(saved("keep-2", "Site in Jaipur"), now = 2L)

            VastuDatabase.Schema.migrate(old, 1L, VastuDatabase.Schema.version)

            val after = PlanRepository(VastuDatabaseFactory.create(old), Dispatchers.Unconfined)
            val plans = after.observePlans().first()
            assertEquals(2, plans.plans.size, "the upgrade must not lose a saved home")
            assertEquals(0, plans.unreadable.size)
            assertEquals(
                setOf("Gurgaon flat", "Site in Jaipur"), plans.plans.map { it.name }.toSet(),
                "and it must not quietly rewrite them either",
            )
            // The thing the upgrade added actually works afterwards.
            after.saveDraft("d1", draft, now = 3L)
            assertEquals(draft, after.loadDraft("d1"), "the new draft table must exist after the upgrade")
        } finally {
            old.close()
        }
    }
}
