package com.vastufirst.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vastufirst.data.db.PlanEntity
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Plan
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.editor.DraftSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** A saved home as the app sees it — the engine INPUT plus its list-view summary. */
data class SavedPlan(
    val id: String,
    val name: String,
    val intent: Intent,
    val propertyType: PropertyType,
    val plan: Plan,               // the full engine input, ready to re-run
    val score: Int,               // last computed score (list view)
    val ruleSetVersion: String,
    val unlocked: Boolean,        // local entitlement (Phase 2 stub; payments = Phase 5)
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * The next free "Home N" number, given the existing home names: one past the highest number that
 * already appears as "Home <n>", or 1 when there are none. Using max+1 (not count+1) means deleting
 * a home never makes the next new one collide with a surviving name. Pure, so it is unit-tested.
 */
fun nextHomeNumber(existingNames: List<String>): Int {
    val highest = existingNames
        .mapNotNull { name ->
            val m = Regex("""^Home (\d+)$""").matchEntire(name.trim())
            m?.groupValues?.get(1)?.toIntOrNull()
        }
        .maxOrNull() ?: 0
    return highest + 1
}

/**
 * The saved homes, plus a count of any rows that could not be read.
 *
 * ⚠ The count is not a diagnostic — it is a promise to the user. A home that silently disappears
 * from the list looks exactly like a home the app deleted on its own, which is the worst thing a
 * paid app that holds your data can appear to do. Showing "1 home couldn't be opened" is honest, and
 * the row is left in the database rather than cleaned up, so a later build can still rescue it.
 */
data class SavedPlans(
    val plans: List<SavedPlan> = emptyList(),
    val unreadable: Int = 0,
)

/**
 * A home that was started and never finished, as the saved-homes screen shows it.
 *
 * ⭐ It carries no score, and that is the point: nothing here has been through the engine. It is the
 * drawing exactly as the user left it, waiting to be picked up — and it is picked up ONLY when the
 * user taps this row (v0.6.6). Before that, the app restored the leftover work by itself the moment
 * anyone started a new home, so "draw it on a grid" quietly handed back an old half-finished plan.
 */
data class SavedDraft(
    val id: String,
    val draft: DraftSnapshot,
    val updatedAt: Long,
) {
    /** How much is actually on the grid — the only honest thing to put in front of the user. */
    val roomCount: Int get() = draft.rooms.size
}

/**
 * The one door to local persistence. Serialises the [Plan] input to JSON so a home can be
 * reopened and re-run fully offline; reads are exposed as cold [Flow]s so the saved-plans
 * screen updates itself. All DB work is pushed onto [io] off the main thread.
 *
 * ⭐ EVERY READ IS TOLERANT. A saved row is JSON plus three enum names, and any of them can stop
 * being readable — a rule rename, a field the app no longer knows, a half-written row from a phone
 * that died mid-save. Before this, ONE such row threw inside the list's flow and took down the whole
 * saved-homes screen: every home gone, for everybody, because of one. Now a row that cannot be read
 * is quarantined and counted, the other homes load, and nothing is deleted.
 */
class PlanRepository(
    private val db: VastuDatabase,
    private val io: CoroutineDispatcher,
    // ignoreUnknownKeys so a plan written by a NEWER build still opens in an older one, rather than
    // the older build treating the whole home as corrupt.
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val queries get() = db.planQueries
    private val draftQueries get() = db.draftQueries

    fun observePlans(): Flow<SavedPlans> =
        queries.selectAll().asFlow().mapToList(io).map { rows ->
            val decoded = rows.map(::toDomainOrNull)
            SavedPlans(plans = decoded.filterNotNull(), unreadable = decoded.count { it == null })
        }

    fun observePlan(id: String): Flow<SavedPlan?> =
        queries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.let(::toDomainOrNull) }

    suspend fun getPlan(id: String): SavedPlan? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.let(::toDomainOrNull)
    }

    /** Insert or update a saved home. [now] is supplied by the caller (keeps this layer clock-free). */
    suspend fun save(saved: SavedPlan, now: Long): Unit = withContext(io) {
        val existing = queries.selectById(saved.id).executeAsOneOrNull()
        queries.upsert(
            id = saved.id,
            name = saved.name,
            intent = saved.intent.name,
            propertyType = saved.propertyType.name,
            northOffset = saved.plan.northOffsetDegrees.toLong(),
            planJson = json.encodeToString(Plan.serializer(), saved.plan),
            score = saved.score.toLong(),
            ruleSetVersion = saved.ruleSetVersion,
            unlocked = if (saved.unlocked) 1L else 0L,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    suspend fun setUnlocked(id: String, unlocked: Boolean, now: Long): Unit = withContext(io) {
        queries.setUnlocked(if (unlocked) 1L else 0L, now, id)
    }

    /**
     * Write back a home's score under a NEWER ruleset — called only after the user has been shown
     * what changed and why. `updatedAt` is left alone on purpose: we moved the rules, they did not
     * edit their home, and reordering their list as though they had would be a second small
     * dishonesty on top of a number they did not ask to move.
     */
    suspend fun setRescored(id: String, score: Int, ruleSetVersion: String): Unit = withContext(io) {
        queries.setScoreAndRuleSetVersion(score.toLong(), ruleSetVersion, id)
    }

    /** Rename a saved home. Trims; a blank name is ignored (the row keeps its current name). */
    suspend fun rename(id: String, name: String): Unit = withContext(io) {
        val clean = name.trim()
        if (clean.isNotEmpty()) queries.setName(clean, id)
    }

    /** The number for the next auto-named "Home N" — one past the highest existing "Home N", so a
     *  delete never causes a duplicate. Reads only the names (cheap). */
    suspend fun nextHomeNumber(): Int = withContext(io) {
        nextHomeNumber(queries.selectNames().executeAsList())
    }

    suspend fun delete(id: String): Unit = withContext(io) { queries.deleteById(id) }

    /** Every home on the device — finished AND unfinished. "Delete all my data" promises both. */
    suspend fun deleteAll(): Unit = withContext(io) {
        queries.deleteAll()
        draftQueries.deleteAllDrafts()
    }

    // --- the unfinished homes (one row each, keyed by the draft's own id) ---

    /**
     * Persist the home currently being drawn, so a background kill cannot lose it. Deliberately
     * total: a draft that cannot be written is not worth crashing the editor over, and the user is
     * still holding the real thing on screen.
     */
    suspend fun saveDraft(id: String, draft: DraftSnapshot, now: Long): Unit = withContext(io) {
        runCatching {
            draftQueries.upsertDraft(id, json.encodeToString(DraftSnapshot.serializer(), draft), now)
        }
        Unit
    }

    /**
     * Every home that was started and never finished, newest first.
     *
     * ⚠ Drafts with nothing drawn in them are filtered out, not shown. An empty grid is not work the
     * user would recognise as "the home I was in the middle of", and offering it back would make the
     * list longer and less true at the same time. A row that can no longer be DECODED is skipped for
     * the same reason it always was: a draft is a convenience, and one bad row must never stop the
     * screen listing the good ones. (Saved homes are treated differently on purpose — those are
     * COUNTED and reported, because a finished home vanishing without a word is unforgivable.)
     */
    fun observeDrafts(): Flow<List<SavedDraft>> =
        draftQueries.selectAllDrafts().asFlow().mapToList(io).map { rows ->
            rows.mapNotNull { row ->
                val snapshot = runCatching {
                    json.decodeFromString(DraftSnapshot.serializer(), row.draftJson)
                }.getOrNull() ?: return@mapNotNull null
                if (snapshot.isEmpty) null else SavedDraft(row.id, snapshot, row.updatedAt)
            }
        }

    /**
     * One unfinished home, or null if there isn't one — or if what is stored can no longer be read,
     * which is treated exactly the same way. A draft is a convenience; failing to decode one must
     * never stop the user starting a home.
     */
    suspend fun loadDraft(id: String): DraftSnapshot? = withContext(io) {
        val row = runCatching { draftQueries.selectDraft(id).executeAsOneOrNull() }.getOrNull()
            ?: return@withContext null
        runCatching { json.decodeFromString(DraftSnapshot.serializer(), row.draftJson) }.getOrNull()
    }

    /** Drop one draft — the moment it becomes a real saved home, or the user throws it away. */
    suspend fun clearDraft(id: String): Unit = withContext(io) {
        runCatching { draftQueries.deleteDraft(id) }
        Unit
    }

    /**
     * A stored row → a [SavedPlan], or **null when it cannot be read**.
     *
     * ⚠ Deliberately catches everything. The three `valueOf` calls throw on any enum value this
     * build does not know, and `decodeFromString` throws on anything malformed — and all three used
     * to run inside the list's flow, where a single bad row emptied the entire saved-homes screen.
     * Losing one home is a bug; appearing to have deleted all of somebody's homes is the end of
     * their trust in a paid app.
     */
    private fun toDomainOrNull(e: PlanEntity): SavedPlan? = try {
        SavedPlan(
            id = e.id,
            name = e.name,
            intent = Intent.valueOf(e.intent),
            propertyType = PropertyType.valueOf(e.propertyType),
            plan = json.decodeFromString(Plan.serializer(), e.planJson),
            score = e.score.toInt(),
            ruleSetVersion = e.ruleSetVersion,
            unlocked = e.unlocked == 1L,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
        )
    } catch (t: Throwable) {
        // Swallowed on purpose, and nothing is deleted: the row stays on disk so a later build that
        // understands it can still bring the home back.
        null
    }
}
