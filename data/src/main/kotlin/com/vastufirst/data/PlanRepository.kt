package com.vastufirst.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vastufirst.data.db.PlanEntity
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Plan
import com.vastufirst.shared.PropertyType
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
 * The one door to local persistence. Serialises the [Plan] input to JSON so a home can be
 * reopened and re-run fully offline; reads are exposed as cold [Flow]s so the saved-plans
 * screen updates itself. All DB work is pushed onto [io] off the main thread.
 */
class PlanRepository(
    private val db: VastuDatabase,
    private val io: CoroutineDispatcher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val queries get() = db.planQueries

    fun observePlans(): Flow<List<SavedPlan>> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> rows.map(::toDomain) }

    fun observePlan(id: String): Flow<SavedPlan?> =
        queries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.let(::toDomain) }

    suspend fun getPlan(id: String): SavedPlan? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.let(::toDomain)
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

    suspend fun deleteAll(): Unit = withContext(io) { queries.deleteAll() }

    private fun toDomain(e: PlanEntity): SavedPlan = SavedPlan(
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
}
