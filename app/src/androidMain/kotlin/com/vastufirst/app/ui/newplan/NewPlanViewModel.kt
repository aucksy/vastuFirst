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
import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The guided grid is [GRID]×[GRID] cells; rooms and the door are placed on it. */
const val GRID = 8

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
            }
        }
    }

    // --- mutations (each nudges a debounced recompute) ---

    fun setRooms(list: List<GridRoom>) { rooms = list; markDirty() }
    fun setDoor(d: GridDoor?) { door = d; markDirty() }
    fun setNorth(deg: Int) { north = ((deg % 360) + 360) % 360; markDirty() }

    private fun markDirty() { dirty.tryEmit(Unit) }

    /** True once the plan has enough to score (at least one room). */
    fun canScore(): Boolean = rooms.isNotEmpty()

    // --- persistence ---

    fun save() {
        val plan = buildPlan() ?: return
        val a = _analysis.value
        val id = planId ?: "plan-${now()}"
        planId = id
        val saved = SavedPlan(
            id = id,
            name = defaultName(),
            intent = plan.intent,
            propertyType = plan.propertyType,
            plan = plan,
            score = a?.score ?: 0,
            ruleSetVersion = engine.ruleSetVersion(),
            unlocked = unlocked,
            createdAt = now(),
            updatedAt = now(),
        )
        viewModelScope.launch { repo.save(saved, now()) }
    }

    fun unlock() {
        unlocked = true
        val id = planId ?: return
        viewModelScope.launch { repo.setUnlocked(id, true, now()) }
    }

    /** Load an existing saved home into the flow (reopen from the saved-plans list). */
    fun load(saved: SavedPlan) {
        planId = saved.id
        intent = saved.intent
        propertyType = saved.propertyType
        north = saved.plan.northOffsetDegrees
        unlocked = saved.unlocked
        // The engine re-runs from the stored Plan; the grid editor is not repopulated in Phase 2.
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { engine.analyze(saved.plan) }
            _analysis.value = result
        }
    }

    private fun defaultName(): String = "My home"

    /**
     * Convert the placed grid rooms + door into the engine's [Plan] (Product PRD §4.1).
     * Grid rows increase DOWNWARD; engine Y increases NORTH (up), so rows are flipped. Cell units
     * are used directly as (arbitrary, self-consistent) plan units — the engine is scale-free.
     */
    fun buildPlan(): Plan? {
        val theIntent = intent ?: return null
        if (rooms.isEmpty()) return null

        fun ex(col: Int) = col.toDouble()                 // east grows with column
        fun ey(row: Int) = (GRID - row).toDouble()        // north grows as row decreases

        val engineRooms = rooms.map { r ->
            val x0 = ex(r.col); val x1 = ex(r.col + r.w)
            val yTop = ey(r.row); val yBottom = ey(r.row + r.h)
            Room(
                id = r.id,
                type = r.type,
                polygon = listOf(
                    Point(x0, yBottom), Point(x1, yBottom), Point(x1, yTop), Point(x0, yTop),
                ),
            )
        }

        // Footprint = bounding box of the placed rooms.
        val minC = rooms.minOf { it.col }
        val maxC = rooms.maxOf { it.col + it.w }
        val minR = rooms.minOf { it.row }
        val maxR = rooms.maxOf { it.row + it.h }
        val outline = listOf(
            Point(ex(minC), ey(maxR)), Point(ex(maxC), ey(maxR)),
            Point(ex(maxC), ey(minR)), Point(ex(minC), ey(minR)),
        )

        val doors = door?.let { d ->
            val (centre, ws, we) = doorGeometry(d, minC, maxC, minR, maxR)
            listOf(Door(id = "door-main", centre = centre, wallStart = ws, wallEnd = we, isMainEntrance = true))
        } ?: emptyList()

        return Plan(
            id = planId ?: "draft",
            propertyType = propertyType,
            intent = theIntent,
            levels = listOf(Level(index = 0, outline = outline, rooms = engineRooms, doors = doors)),
            northOffsetDegrees = north,
        )
    }

    /** The door centre + wall span on the footprint perimeter for the chosen side/cell. */
    private fun doorGeometry(d: GridDoor, minC: Int, maxC: Int, minR: Int, maxR: Int): Triple<Point, Point, Point> {
        fun ex(col: Double) = col
        fun ey(row: Double) = (GRID - row)
        val alongCol = (d.cell + 0.5).coerceIn(minC + 0.5, maxC - 0.5)
        val alongRow = (d.cell + 0.5).coerceIn(minR + 0.5, maxR - 0.5)
        return when (d.side) {
            DoorSide.N -> Triple(
                Point(ex(alongCol), ey(minR.toDouble())),
                Point(ex(minC.toDouble()), ey(minR.toDouble())), Point(ex(maxC.toDouble()), ey(minR.toDouble())),
            )
            DoorSide.S -> Triple(
                Point(ex(alongCol), ey(maxR.toDouble())),
                Point(ex(minC.toDouble()), ey(maxR.toDouble())), Point(ex(maxC.toDouble()), ey(maxR.toDouble())),
            )
            DoorSide.E -> Triple(
                Point(ex(maxC.toDouble()), ey(alongRow)),
                Point(ex(maxC.toDouble()), ey(minR.toDouble())), Point(ex(maxC.toDouble()), ey(maxR.toDouble())),
            )
            DoorSide.W -> Triple(
                Point(ex(minC.toDouble()), ey(alongRow)),
                Point(ex(minC.toDouble()), ey(minR.toDouble())), Point(ex(minC.toDouble()), ey(maxR.toDouble())),
            )
        }
    }
}
