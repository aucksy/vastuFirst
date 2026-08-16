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
 * ⭐ HOW TWO HOME NAMES ARE COMPARED FOR "THE SAME NAME" — the one definition, used everywhere.
 *
 * Trimmed, runs of blanks squeezed to one, and lower-cased. The test is not "are these the same
 * string" but "would a person scanning the saved-homes list be able to tell these two rows apart",
 * and "Dwarka flat", "dwarka flat" and "Dwarka  flat" fail that test identically.
 *
 * ⚠ NO REGULAR EXPRESSION, on purpose. This module is unit-tested on the JVM but runs on Android,
 * whose regex engine treats character classes differently — a difference that has already cost this
 * project five releases. Splitting on the two blank characters by hand cannot behave differently on
 * the two platforms. `lowercase()` with no argument is locale-invariant for the same reason.
 */
fun homeNameKey(name: String): String =
    name.trim().split(' ', '\t').filter { it.isNotEmpty() }.joinToString(" ").lowercase()

/**
 * ⭐ The next auto name no existing home already answers to.
 *
 * ⚠ [nextHomeNumber] alone is NOT enough, and the hole is easy to fall into: its pattern is exact
 * and case-sensitive, so a user who renames a home to "home 4" is invisible to it — it hands the
 * next new home the name "Home 4", and the list then carries two rows nobody can tell apart. This
 * keeps stepping until the name is genuinely free under [homeNameKey].
 */
fun freshHomeName(existingNames: List<String>): String {
    val taken = existingNames.map(::homeNameKey).toSet()
    var n = nextHomeNumber(existingNames)
    while (homeNameKey("Home $n") in taken) n++
    return "Home $n"
}

/**
 * True when [candidate] is already the name of some other home. [otherNames] must NOT contain the
 * name of the home being renamed, so keeping its own name — or only fixing its capitals — is always
 * allowed. Pure, so it is unit-tested.
 */
fun homeNameAlreadyTaken(candidate: String, otherNames: List<String>): Boolean {
    val key = homeNameKey(candidate)
    return key.isNotEmpty() && otherNames.any { homeNameKey(it) == key }
}

/**
 * The next free "Home N" number, given the existing home names: one past the highest number that
 * already appears as "Home <n>", or 1 when there are none. Using max+1 (not count+1) means deleting
 * a home never makes the next new one collide with a surviving name. Pure, so it is unit-tested.
 *
 * ⚠ Callers naming a NEW home want [freshHomeName], not this — see the hole described there.
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
 * A saved row this build could not decode — surfaced BY NAME, not folded into a count.
 *
 * The name is trustworthy even when nothing else is: what breaks a row is its plan JSON or an enum
 * value from another build, and those live in different columns. [name] and [updatedAt] are plain
 * text and a number, so "Home 3 · 12 Jul" can be said about a home whose contents are gone —
 * which is what lets the screen offer removing *that one home* instead of a count with no handle
 * on it (audit B7: the only delete in the app was Settings → delete ALL data).
 */
data class UnreadableHome(
    val id: String,
    val name: String,
    val updatedAt: Long,
)

/**
 * The saved homes, plus any rows that could not be read.
 *
 * ⚠ The unreadable list is not a diagnostic — it is a promise to the user. A home that silently
 * disappears from the list looks exactly like a home the app deleted on its own, which is the worst
 * thing a paid app that holds your data can appear to do. Saying "Home 3 can't be opened" is honest,
 * and the row is left in the database rather than cleaned up, so a later build can still rescue it —
 * unless the user chooses to remove it, which is theirs to choose, not ours.
 */
data class SavedPlans(
    val plans: List<SavedPlan> = emptyList(),
    val unreadable: List<UnreadableHome> = emptyList(),
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
    /**
     * ⭐ Where an unfinished home's plan photograph is kept — see [PlanPhotoStore].
     *
     * ⚠ It is threaded through the REPOSITORY rather than used directly by the screens, and that is
     * the whole safety of it: the two places a home stops existing ([clearDraft] and [deleteAll])
     * are the two places its picture must stop existing, and going through here means a caller
     * cannot delete one and forget the other. A photograph of somebody's home left behind by a
     * "delete everything" button is the failure this project has already shipped once.
     */
    private val photos: PlanPhotoStore = NoPlanPhotoStore,
) {
    private val queries get() = db.planQueries
    private val draftQueries get() = db.draftQueries

    fun observePlans(): Flow<SavedPlans> =
        queries.selectAll().asFlow().mapToList(io).map { rows ->
            val decoded = rows.map { it to toDomainOrNull(it) }
            SavedPlans(
                plans = decoded.mapNotNull { it.second },
                // The identity columns still read on a row whose plan JSON does not — see
                // [UnreadableHome] for why that is the difference between a count and a handle.
                unreadable = decoded.filter { it.second == null }
                    .map { (e, _) -> UnreadableHome(e.id, e.name, e.updatedAt) },
            )
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

    /**
     * The name to give a brand-new home: the next "Home N" that no saved home already answers to,
     * however it is capitalised or spaced. See [freshHomeName] for the hole this closes.
     */
    suspend fun nextHomeName(): String = withContext(io) {
        freshHomeName(queries.selectNames().executeAsList())
    }

    suspend fun delete(id: String): Unit = withContext(io) { queries.deleteById(id) }

    /** Every home on the device — finished AND unfinished. "Delete all my data" promises both.
     *
     *  ⭐ AND EVERY PLAN PHOTOGRAPH WITH THEM. A picture of somebody's own floor plan is the most
     *  personal thing this app holds; a wipe that leaves it on the phone is the defect this project
     *  already shipped once, when the databases were emptied and the camera's folder was not. */
    suspend fun deleteAll(): Unit = withContext(io) {
        queries.deleteAll()
        draftQueries.deleteAllDrafts()
        photos.deleteAll()
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

    /** Drop one draft — the moment it becomes a real saved home, or the user throws it away.
     *
     *  ⭐ Its photograph goes with it, in the same call, for the same reason "delete all" takes them:
     *  a picture whose home no longer exists can never again be shown to anybody, so keeping it is
     *  storage spent on holding a photograph of somebody's home for no reason at all. */
    suspend fun clearDraft(id: String): Unit = withContext(io) {
        runCatching { draftQueries.deleteDraft(id) }
        photos.delete(id)
        Unit
    }

    /**
     * ⭐ Keep the photograph this unfinished home was read off, so "Carry on" can put the reader
     * back on their own plan instead of on our redrawing of it.
     *
     * Total, like [saveDraft] beside it: a picture that cannot be written must not fail the save of
     * the home it belongs to. The home is the product; the picture is how it is shown.
     */
    suspend fun saveDraftPhoto(id: String, jpeg: ByteArray): Unit = withContext(io) {
        runCatching { photos.save(id, jpeg) }
        Unit
    }

    /** The photograph of one unfinished home, or null — which every caller must treat as "show the
     *  zone map instead", exactly as the app behaved before photographs were kept at all. */
    suspend fun loadDraftPhoto(id: String): ByteArray? = withContext(io) {
        runCatching { photos.load(id) }.getOrNull()
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
