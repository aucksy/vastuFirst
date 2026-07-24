package com.vastufirst.app.ui.newplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.SavedPlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Plan
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.fitWithoutOverlap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** The DEFAULT guided grid is [GRID]×[GRID] cells; the plot can be resized to [MIN_GRID]..[MAX_GRID]
 *  cells per side (square cells, non-square plot) so a rectangular home is drawn true-to-life. The
 *  score is unaffected by the grid size — the engine scores the bounding box of the placed rooms
 *  (see docs/RECT-PLOT-RESEARCH.md) — so this is purely the shape of the drawing canvas. */
const val GRID = 8
const val MIN_GRID = 4
const val MAX_GRID = 10

/** A placed room: a cell rectangle (top-left col/row + size in cells) and its type. */
data class GridRoom(
    val id: String,
    val type: RoomType,
    val col: Int,
    val row: Int,
    val w: Int,
    val h: Int,
)

enum class DoorSide { N, E, S, W }

/** The front door: which outer wall it sits on, and how far along it (cell index). */
data class GridDoor(val side: DoorSide, val cell: Int)

/**
 * The draft home as the user builds it across the guided-grid flow (Welcome → Add home →
 * Guided grid → Mark North → Score → Report). Shared across those screens via a nav-graph-scoped
 * ViewModel so nothing has to be threaded through nav arguments.
 *
 * The engine runs OFF the main thread and North drags are DEBOUNCED to ≤50 ms (Product PRD
 * §4.5.3) — dragging stays smooth without demanding a 16 ms engine. The engine call is TOTAL
 * (never throws), so there is never an error state here.
 */
@OptIn(FlowPreview::class)
class NewPlanViewModel(
    private val engine: VastuEngine,
    private val repo: PlanRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    var language by mutableStateOf("en")
    var intent by mutableStateOf<Intent?>(null)
    var propertyType by mutableStateOf(PropertyType.INDEPENDENT_HOUSE)
    var rooms by mutableStateOf<List<GridRoom>>(emptyList())
        private set
    var door by mutableStateOf<GridDoor?>(null)
        private set
    // The plot's shape, in whole square cells. Default square; the user can set it to their real
    // proportions (e.g. 8 wide × 6 deep). Not persisted on the Plan — the engine needs only the
    // rooms — but re-derived from the rooms when a saved home is reopened (see load()).
    var gridCols by mutableStateOf(GRID)
        private set
    var gridRows by mutableStateOf(GRID)
        private set
    var north by mutableStateOf(0)
        private set
    var planId by mutableStateOf<String?>(null)
        private set
    var unlocked by mutableStateOf(false)
        private set

    private val _analysis = MutableStateFlow<Analysis?>(null)
    val analysis: StateFlow<Analysis?> = _analysis

    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            dirty.debounce(50).collectLatest {
                val plan = buildPlan() ?: run { _analysis.value = null; return@collectLatest }
                val result = withContext(Dispatchers.Default) { engine.analyze(plan) }
                _analysis.value = result
                // Autosave edits to an ALREADY-saved home (planId != null) so a reopen → Fix → edit →
                // Back never silently loses them, and the saved-plans list stays in sync. A brand-new
                // draft (planId == null) is still first persisted at Mark North's "Read my home", so
                // this never creates junk rows while the user is still drawing (E2E-ASSESSMENT §A3).
                planId?.let { id ->
                    val saved = SavedPlan(
                        id = id, name = defaultName(), intent = plan.intent, propertyType = plan.propertyType,
                        plan = plan, score = result.score, ruleSetVersion = engine.ruleSetVersion(),
                        unlocked = unlocked, createdAt = now(), updatedAt = now(),
                    )
                    // NonCancellable: leaving the flow (goHome pops the graph-scoped VM, cancelling
                    // this scope) must not drop the final save mid-write (E2E-ASSESSMENT §A3 / review F1).
                    withContext(NonCancellable) { repo.save(saved, now()) }
                }
            }
        }
    }

    // --- mutations (each nudges a debounced recompute) ---

    fun updateRooms(list: List<GridRoom>) { rooms = list; markDirty() }
    fun updateDoor(d: GridDoor?) { door = d; markDirty() }
    fun updateNorth(deg: Int) { north = ((deg % 360) + 360) % 360; markDirty() }

    /** Resize the drawing plot. Existing rooms are clamped to fit the new bounds (shrunk/moved, never
     *  dropped); a door on a wall that no longer exists is cleared. */
    fun updateGrid(cols: Int, rows: Int) {
        val c = cols.coerceIn(MIN_GRID, MAX_GRID)
        val r = rows.coerceIn(MIN_GRID, MAX_GRID)
        if (c == gridCols && r == gridRows) return
        // RE-PACK the rooms into the requested size so none overlap. Clamping each room to the new
        // bounds independently can push two onto the same cells (the editor never otherwise allows
        // this), which makes the engine score the buried room twice — a silently wrong score. If the
        // rooms can't all fit at the requested size (a plot smaller than they need), fitWithoutOverlap
        // returns null and we REFUSE the resize — the stepper simply won't go below the size the
        // current rooms require, rather than force an overlap (docs/E2E-ASSESSMENT §A1).
        val fitted = fitWithoutOverlap(rooms.map { CellRect(it.col, it.row, it.w, it.h) }, c, r) ?: return
        gridCols = c
        gridRows = r
        var changed = false
        val repacked = rooms.mapIndexed { i, room ->
            val f = fitted[i]
            room.copy(col = f.col, row = f.row, w = f.w, h = f.h)
        }
        if (repacked != rooms) { rooms = repacked; changed = true }
        door?.let { d ->
            val fits = when (d.side) {
                DoorSide.N, DoorSide.S -> d.cell in 0 until c
                DoorSide.E, DoorSide.W -> d.cell in 0 until r
            }
            if (!fits) { door = null; changed = true }
        }
        // Only a resize that moved a room or cleared the door changes the score — a pure grow that
        // shifts nothing leaves the analysis identical (grid size doesn't enter the score), so don't
        // recompute or bump the saved plan's updatedAt for it (review F2). The canvas still resizes:
        // gridCols/gridRows are state, so the editor and zone map recompose regardless.
        if (changed) markDirty()
    }

    private fun markDirty() { dirty.tryEmit(Unit) }

    /** True once the plan has enough to score (at least one room). */
    fun canScore(): Boolean = rooms.isNotEmpty()

    // --- persistence ---

    fun save() {
        // Assign the id BEFORE building, so the serialized Plan.id matches its row id.
        val id = planId ?: "plan-${now()}"
        planId = id
        val plan = buildPlan() ?: return
        viewModelScope.launch {
            // NonCancellable: "Read my home" saves then immediately navigates to Score; if the user
            // taps on to "See all my plans" (goHome pops this graph-scoped VM), the save must still
            // land — otherwise the home they just made is missing from the list (review F1).
            withContext(NonCancellable) {
                // Score the EXACT plan being persisted (not the debounced cache, which can lag or be
                // null): guarantees the stored list-view score equals what a reopen recomputes.
                val a = withContext(Dispatchers.Default) { engine.analyze(plan) }
                _analysis.value = a
                val saved = SavedPlan(
                    id = id,
                    name = defaultName(),
                    intent = plan.intent,
                    propertyType = plan.propertyType,
                    plan = plan,
                    score = a.score,
                    ruleSetVersion = engine.ruleSetVersion(),
                    unlocked = unlocked,
                    createdAt = now(),
                    updatedAt = now(),
                )
                repo.save(saved, now())
            }
        }
    }

    fun unlock() {
        unlocked = true
        val id = planId ?: return
        viewModelScope.launch { repo.setUnlocked(id, true, now()) }
    }

    /** Reopen a saved home by id (from the saved-plans list). */
    fun loadById(id: String) {
        viewModelScope.launch { repo.getPlan(id)?.let(::load) }
    }

    /** Load an existing saved home into the flow (reopen from the saved-plans list). */
    fun load(saved: SavedPlan) {
        planId = saved.id
        intent = saved.intent
        propertyType = saved.propertyType
        north = saved.plan.northOffsetDegrees
        unlocked = saved.unlocked
        // Rebuild the grid draft from the stored Plan so the zone map (and any further edit) has
        // its rooms/door back — the inverse of buildPlan(), exact for the integer grid geometry.
        rooms = gridRoomsFromPlan(saved.plan)
        door = gridDoorFromPlan(saved.plan, rooms)
        // The plot shape isn't stored on the Plan (the engine doesn't need it); re-derive the
        // tightest grid that encloses the reopened rooms so a further edit keeps its proportions.
        gridCols = (rooms.maxOfOrNull { it.col + it.w } ?: GRID).coerceIn(MIN_GRID, MAX_GRID)
        gridRows = (rooms.maxOfOrNull { it.row + it.h } ?: GRID).coerceIn(MIN_GRID, MAX_GRID)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { engine.analyze(saved.plan) }
            _analysis.value = result
            // If the ruleset changed since this home was saved, refresh the stored score + version
            // instead of leaving the list showing a number computed under an older ruleset (§5).
            if (saved.ruleSetVersion != engine.ruleSetVersion()) {
                repo.save(
                    saved.copy(score = result.score, ruleSetVersion = engine.ruleSetVersion(), updatedAt = now()),
                    now(),
                )
            }
        }
    }

    private fun defaultName(): String = "My home"

    /**
     * Convert the placed grid rooms + door into the engine's [Plan]. The maths lives in the pure
     * [buildEnginePlan] (PlanConversion.kt) so the screenshot harness can build the exact same input
     * — this delegate is the ViewModel's binding of it to the live draft state.
     */
    fun buildPlan(): Plan? =
        buildEnginePlan(rooms, door, intent, propertyType, north, planId ?: "draft")

    /** Rebuild the placed grid rooms from a stored engine [Plan] — the exact inverse of the
     *  buildPlan() flip (engine y = GRID − row), so a reopened home shows its rooms again. */
    private fun gridRoomsFromPlan(plan: Plan): List<GridRoom> {
        val level = plan.levels.firstOrNull() ?: return emptyList()
        return level.rooms.mapNotNull { room ->
            if (room.polygon.isEmpty()) return@mapNotNull null
            val xs = room.polygon.map { it.x }
            val ys = room.polygon.map { it.y }
            val x0 = xs.min(); val x1 = xs.max()
            val yTop = ys.max(); val yBottom = ys.min()
            GridRoom(
                id = room.id,
                type = room.type,
                col = x0.roundToInt(),
                row = (GRID - yTop).roundToInt(),
                w = (x1 - x0).roundToInt().coerceAtLeast(1),
                h = (yTop - yBottom).roundToInt().coerceAtLeast(1),
            )
        }
    }

    /** Rebuild the placed door from a stored [Plan], classifying its wall from the footprint edges. */
    private fun gridDoorFromPlan(plan: Plan, rooms: List<GridRoom>): GridDoor? {
        val level = plan.levels.firstOrNull() ?: return null
        val d = level.doors.firstOrNull { it.isMainEntrance } ?: return null
        if (rooms.isEmpty()) return null
        val minC = rooms.minOf { it.col }
        val maxC = rooms.maxOf { it.col + it.w }
        val minR = rooms.minOf { it.row }
        val maxR = rooms.maxOf { it.row + it.h }
        val yNorth = (GRID - minR).toDouble()   // ey(minR)
        val ySouth = (GRID - maxR).toDouble()   // ey(maxR)
        val xEast = maxC.toDouble()
        val xWest = minC.toDouble()
        val eps = 1e-6
        val horizontal = abs(d.wallStart.y - d.wallEnd.y) < eps
        return when {
            horizontal && abs(d.centre.y - yNorth) < eps -> GridDoor(DoorSide.N, (d.centre.x - 0.5).roundToInt())
            horizontal && abs(d.centre.y - ySouth) < eps -> GridDoor(DoorSide.S, (d.centre.x - 0.5).roundToInt())
            abs(d.centre.x - xEast) < eps -> GridDoor(DoorSide.E, ((GRID - d.centre.y) - 0.5).roundToInt())
            abs(d.centre.x - xWest) < eps -> GridDoor(DoorSide.W, ((GRID - d.centre.y) - 0.5).roundToInt())
            else -> null
        }
    }
}
