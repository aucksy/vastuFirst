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
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone
import com.vastufirst.shared.editor.Cell
import com.vastufirst.shared.editor.DraftDoor
import com.vastufirst.shared.editor.DraftRoom
import com.vastufirst.shared.editor.DraftSnapshot
import com.vastufirst.shared.editor.Footprint
import com.vastufirst.shared.editor.Gap
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
    // ⭐ The cells of the home's bounding box that are NOT part of the home — the missing corner of an
    // L, a notch. Empty means "a full rectangle", which is exactly what every home scored as before
    // this existed. NOT stored as a column of its own: it is re-derived from the saved outline on
    // reopen (see load()), so no saved home needs migrating and an older build reading a newer row
    // still gets a valid plan.
    var cutOutCells by mutableStateOf<Set<Cell>>(emptySet())
        private set
    // Cells the user has explicitly confirmed ARE part of their home, so the app stops asking about
    // that gap. Purely a record of answered questions — it never reaches the engine.
    var keptCells by mutableStateOf<Set<Cell>>(emptySet())
        private set
    // The optional extras — water tank, tree, road outside — that let the rest of the engine's rules
    // run instead of sitting permanently in "couldn't check these yet".
    var siteAnswers by mutableStateOf(SiteAnswers())
        private set
    var planId by mutableStateOf<String?>(null)
        private set
    // The home's display name. Null until the draft is first saved, when it's assigned the next free
    // "Home N" (see save()); a reopened home carries its stored name (see load()). Held here so
    // autosave persists the REAL name, never a constant (E2E-ASSESSMENT review F3).
    var name by mutableStateOf<String?>(null)
        private set
    var unlocked by mutableStateOf(false)
        private set

    private val _analysis = MutableStateFlow<Analysis?>(null)
    val analysis: StateFlow<Analysis?> = _analysis

    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * ⭐ True when what is on screen was brought back from a half-finished draft rather than started
     * fresh — so the editor can say so, and offer to start again. Silently restoring work is right;
     * silently restoring it without saying so is how a user ends up editing a home they thought they
     * had abandoned.
     */
    var restoredFromDraft by mutableStateOf(false)
        private set

    /**
     * ⭐ True when the rooms on the grid came off a scan that could not work out WHERE they go, so
     * they are parked in a row of equal squares rather than drawn as a plan.
     *
     * The editor needs to know because a parking row is not a home: it must not be titled "Place
     * your rooms" as though it were finished, and the app must not ask shape questions about the box
     * around it. See `GuidedGridContent`'s `roomsUnplaced`.
     *
     * ⚠ Deliberately NOT persisted with the draft. A home brought back after Android reclaimed the
     * app is whatever the user last left, and re-parking it a day later would be the app forgetting
     * work they had done.
     */
    var roomsUnplaced by mutableStateOf(false)
        private set

    /** Set by the scan flow as it hands its rooms over. Cleared by [startAgain]. */
    fun markRoomsUnplaced(unplaced: Boolean) { roomsUnplaced = unplaced }

    init {
        // Bring back the home that was being drawn when Android last reclaimed the app. A draft row
        // only ever exists for a home that was NEVER saved (it is deleted the moment one becomes a
        // real saved home), so "there is a draft" always means "you were in the middle of this", and
        // restoring it is the obviously right answer rather than a guess.
        viewModelScope.launch {
            val draft = repo.loadDraft() ?: return@launch
            if (draft.isEmpty || rooms.isNotEmpty() || planId != null) return@launch
            applyDraft(draft)
            restoredFromDraft = true
            markDirty()
        }

        viewModelScope.launch {
            dirty.debounce(50).collectLatest {
                // ⚠ The draft is written BEFORE the engine runs, not after. Scoring is the slow part,
                // and the whole point of this row is to survive being killed at an arbitrary moment —
                // including during that computation.
                if (planId == null) {
                    withContext(NonCancellable) { repo.saveDraft(snapshot(), now()) }
                }
                val plan = buildPlan() ?: run { _analysis.value = null; return@collectLatest }
                val result = withContext(Dispatchers.Default) { engine.analyze(plan) }
                _analysis.value = result
                // Autosave edits to an ALREADY-saved home (planId != null) so a reopen → Fix → edit →
                // Back never silently loses them, and the saved-plans list stays in sync. A brand-new
                // draft (planId == null) is still first persisted at Mark North's "Read my home", so
                // this never creates junk rows while the user is still drawing (E2E-ASSESSMENT §A3).
                planId?.let { id ->
                    if (name == null) name = "Home ${repo.nextHomeNumber()}"
                    val saved = SavedPlan(
                        id = id, name = name ?: FALLBACK_NAME, intent = plan.intent, propertyType = plan.propertyType,
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

    fun updateRooms(list: List<GridRoom>) {
        rooms = list
        // Keep the door on the new footprint (or clear it if the last room went) so it can never be
        // displayed in one place but scored/reloaded in another after a room edit (UAT F4). No-op when
        // the door already sits inside the footprint, so normal editing doesn't nudge it.
        val clampedDoor = clampDoorToRooms(door, list)
        if (clampedDoor != door) door = clampedDoor
        // A room edit can invalidate a cut — the corner is now covered by a room, or outside the new
        // outline, or the shape it leaves is no longer one solid home. Prune rather than carry a
        // shape the app cannot draw (and re-ask about anything that lapsed).
        cutOutCells = pruneCutOut(list, door, cutOutCells, gridCols, gridRows)
        keptCells = keptCells.intersect(Footprint.boundingCells(list.map { it.cellRect() }))
        markDirty()
    }

    fun updateDoor(d: GridDoor?) {
        door = d
        cutOutCells = pruneCutOut(rooms, d, cutOutCells, gridCols, gridRows)
        markDirty()
    }

    /** The user says a gap is NOT part of their home — the missing corner of an L. */
    fun cutOutGap(cells: Set<Cell>) {
        val next = pruneCutOut(rooms, door, cutOutCells + cells, gridCols, gridRows)
        if (next == cutOutCells) return          // refused (it would not leave one solid home)
        cutOutCells = next
        keptCells = keptCells - cells
        markDirty()
    }

    /** The user says a gap IS part of their home — stop asking about it. Changes no geometry. */
    fun keepGap(cells: Set<Cell>) {
        cutOutCells = pruneCutOut(rooms, door, cutOutCells - cells, gridCols, gridRows)
        keptCells = keptCells + cells
        markDirty()
    }

    /** The user says where one of the optional extras is. Re-scores like any other change. */
    fun setSiteAnswer(item: SiteItem, zone: Zone) {
        siteAnswers = siteAnswers.copy(
            answers = siteAnswers.answers + (item to zone),
            declined = siteAnswers.declined - item,
        )
        markDirty()
    }

    /** The user says there isn't one. A real answer, not a skip — it lets the rule report "clear". */
    fun declineSiteItem(item: SiteItem) {
        siteAnswers = siteAnswers.copy(
            answers = siteAnswers.answers - item,
            declined = siteAnswers.declined + item,
        )
        markDirty()
    }

    /** Forget every answer about the home's shape and go back to a full rectangle. */
    fun resetShape() {
        if (cutOutCells.isEmpty() && keptCells.isEmpty()) return
        cutOutCells = emptySet()
        keptCells = emptySet()
        markDirty()
    }

    /** Gaps still waiting on an answer — the questions the editor puts to the user, in a fixed order. */
    fun undecidedGaps(): List<Gap> =
        gapsFor(rooms, door, gridCols, gridRows)
            .filter { gap -> gap.cells.none { it in keptCells || it in cutOutCells } }
    fun updateNorth(deg: Int) { north = ((deg % 360) + 360) % 360; markDirty() }

    /** Resize the drawing plot. Existing rooms are re-packed to fit the new bounds (shrunk/moved,
     *  never dropped, never overlapped); an infeasible shrink is refused; a door on a wall that no
     *  longer exists is cleared. All the arithmetic is the pure [resolveGridResize] so it is tested.
     *
     *  Returns **false when the request could not be honoured** — the rooms can't fit at that size, or
     *  the plot is already at its MIN_GRID/MAX_GRID limit. The editor turns that into the same "no"
     *  buzz an overlapping move gets, so a plot key that cannot act never just looks broken. */
    fun updateGrid(cols: Int, rows: Int): Boolean {
        val res = resolveGridResize(rooms, door, gridCols, gridRows, cols, rows) ?: return false
        gridCols = res.cols
        gridRows = res.rows
        // res.rooms / res.door are the SAME instances when nothing moved, so these are equality-skipped
        // state writes (no spurious recompute) — a pure grow leaves the score untouched (review F2).
        rooms = res.rooms
        door = res.door
        cutOutCells = pruneCutOut(res.rooms, res.door, cutOutCells, res.cols, res.rows)
        keptCells = keptCells.intersect(Footprint.boundingCells(res.rooms.map { it.cellRect() }))
        if (res.changed) markDirty()
        return res.honoured
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
                // First save of a new draft: give it the next free "Home N" so no two homes share a
                // name (defeats the compare feature — E2E-ASSESSMENT B12). A reopened home already
                // has its name from load(), so this only fires once, at creation.
                if (name == null) name = "Home ${repo.nextHomeNumber()}"
                // Score the EXACT plan being persisted (not the debounced cache, which can lag or be
                // null): guarantees the stored list-view score equals what a reopen recomputes.
                val a = withContext(Dispatchers.Default) { engine.analyze(plan) }
                _analysis.value = a
                val saved = SavedPlan(
                    id = id,
                    name = name ?: FALLBACK_NAME,
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
                // ⭐ This home is now a real saved row, so the draft has done its job. Dropping it
                // here is what makes "a draft exists" mean exactly "you never finished this one" —
                // and therefore what makes restoring it on the next launch unambiguously right.
                repo.clearDraft()
                restoredFromDraft = false
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

    // --- the in-progress draft ---

    /** The current draft, in the shape that goes to disk. */
    fun snapshot(): DraftSnapshot = DraftSnapshot(
        name = name,
        intent = intent,
        propertyType = propertyType,
        north = north,
        gridCols = gridCols,
        gridRows = gridRows,
        rooms = rooms.map { DraftRoom(it.id, it.type, it.col, it.row, it.w, it.h) },
        door = door?.let { DraftDoor(it.side.name, it.cell) },
        cutOut = cutOutCells.toList(),
        kept = keptCells.toList(),
        siteAnswers = siteAnswers.answers.mapKeys { it.key.name },
        siteDeclined = siteAnswers.declined.map { it.name },
    )

    /**
     * Put a stored draft back on screen. Tolerant on purpose: a door whose wall name this build no
     * longer knows is dropped rather than taking the whole home down with it, and the cut-out cells
     * are re-pruned so a draft can never restore a shape the editor would refuse to create.
     */
    private fun applyDraft(d: DraftSnapshot) {
        name = d.name
        intent = d.intent
        propertyType = d.propertyType
        north = ((d.north % 360) + 360) % 360
        gridCols = d.gridCols.coerceIn(MIN_GRID, MAX_GRID)
        gridRows = d.gridRows.coerceIn(MIN_GRID, MAX_GRID)
        rooms = d.rooms.map { GridRoom(it.id, it.type, it.col, it.row, it.w, it.h) }
        door = d.door?.let { stored ->
            DoorSide.entries.firstOrNull { it.name == stored.side }?.let { GridDoor(it, stored.cell) }
        }
        door = clampDoorToRooms(door, rooms)
        cutOutCells = pruneCutOut(rooms, door, d.cutOut.toSet(), gridCols, gridRows)
        keptCells = d.kept.toSet()
        // A key this build no longer knows is dropped rather than taking the draft down with it.
        siteAnswers = SiteAnswers(
            answers = d.siteAnswers.mapNotNull { (k, v) ->
                SiteItem.entries.firstOrNull { it.name == k }?.let { it to v }
            }.toMap(),
            declined = d.siteDeclined.mapNotNull { k -> SiteItem.entries.firstOrNull { it.name == k } }.toSet(),
        )
    }

    /**
     * Throw away the restored draft and start this home from nothing. The only way back to an empty
     * grid once a draft has been brought back — without it, "we kept your home" would be a trap.
     */
    fun startAgain() {
        rooms = emptyList()
        door = null
        cutOutCells = emptySet()
        keptCells = emptySet()
        siteAnswers = SiteAnswers()
        name = null
        planId = null
        gridCols = GRID
        gridRows = GRID
        restoredFromDraft = false
        roomsUnplaced = false
        _analysis.value = null
        viewModelScope.launch { withContext(NonCancellable) { repo.clearDraft() } }
    }

    /** Load an existing saved home into the flow (reopen from the saved-plans list). */
    fun load(saved: SavedPlan) {
        planId = saved.id
        name = saved.name
        intent = saved.intent
        propertyType = saved.propertyType
        north = saved.plan.northOffsetDegrees
        unlocked = saved.unlocked
        // Rebuild the grid draft from the stored Plan so the zone map (and any further edit) has
        // its rooms/door back — the inverse of buildPlan(), exact for the integer grid geometry.
        rooms = gridRoomsFromPlan(saved.plan)
        door = gridDoorFromPlan(saved.plan, rooms)
        // The home's SHAPE comes back from the outline that was saved with it, so a home the user told
        // us is L-shaped reopens L-shaped and re-scores identically. Every cell of a reopened home is
        // an answered question, so the editor does not interrogate them about it a second time.
        cutOutCells = cutOutFromPlan(saved.plan, rooms)
        siteAnswers = siteAnswersFromSavedPlan(saved.plan)
        keptCells = Footprint.boundingCells(rooms.map { it.cellRect() }) - cutOutCells
        // The plot shape isn't stored on the Plan (the engine doesn't need it); re-derive the
        // tightest grid that encloses the reopened rooms so a further edit keeps its proportions
        // (pure gridSizeForRooms — see its ⚠ KNOWN LIMITATION note about lost outer margin, UAT S2).
        val (dc, dr) = gridSizeForRooms(rooms)
        gridCols = dc
        gridRows = dr
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

    private companion object {
        // Defensive only — every persistence path assigns a real "Home N" name before writing, so
        // this should never actually reach the DB. Kept so a SavedPlan can never be built with null.
        const val FALLBACK_NAME = "My home"
    }

    /**
     * Convert the placed grid rooms + door into the engine's [Plan]. The maths lives in the pure
     * [buildEnginePlan] (PlanConversion.kt) so the screenshot harness can build the exact same input
     * — this delegate is the ViewModel's binding of it to the live draft state.
     */
    fun buildPlan(): Plan? =
        buildEnginePlan(rooms, door, intent, propertyType, north, planId ?: "draft", cutOutCells, siteAnswers)
}
