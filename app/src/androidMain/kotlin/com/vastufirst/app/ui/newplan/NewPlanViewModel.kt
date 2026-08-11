package com.vastufirst.app.ui.newplan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.CoroutineDispatcher
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
    /**
     * ⭐ What the scanned plan PRINTS beside this room — "MASTER BEDROOM 1" — or empty for a room
     * drawn by hand. Two things read it and nothing else does: the report, so its room list says
     * what the sheet says, and [com.vastufirst.app.ui.newplan.frontDoorFromEntrance], so a plan
     * that captions a porch can have its front door read without that porch being re-typed.
     *
     * ⚠ It never reaches the score. The engine is a function of type and shape.
     */
    val label: String = "",
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
    /**
     * Where scoring runs — off the main thread in the app, and injectable so a test can make the
     * whole save path deterministic. Without this seam the only way to prove "finishing a home
     * removes its unfinished row" is to poll and hope, which is how a flaky test enters a codebase.
     */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    /**
     * ⭐ Survives process death when the ViewModel does not. Holds only the two ids — which
     * unfinished-home row this session owns, and which saved home is open — because everything
     * else is already on disk under those ids. Without this, the OS killing the app mid-flow
     * brought the user back to their restored screen with a BLANK draft behind it: the score
     * span "Reading your home…" forever, and continuing to draw silently wrote a draft missing
     * its intent (4 Aug 2026 audit, B1).
     */
    private val handle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // No `language` field. It existed to back the welcome screen's picker, was never read by
    // anything, and the app is English only, permanently (CLAUDE.md §2e). Do not reintroduce it.
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
     * fresh — so the editor can say so, and offer to start again. The user asked for this one by
     * tapping it on the saved-homes screen (see [resumeDraft]); saying so anyway is what keeps
     * "carry on" and "throw it away and start this home again" both one tap apart.
     */
    var restoredFromDraft by mutableStateOf(false)
        private set

    /**
     * ⭐⭐ Which unfinished-home row this session owns, or null when nothing has been drawn yet.
     *
     * ⚠ It is minted on the FIRST real edit, never on entry. That is the whole of the owner's fix:
     * a session that is only passing through — Welcome, "add a home", a look at the scanner — must
     * leave no row behind and must not adopt anybody else's. And because each session owns its own
     * id, starting a second home can no longer overwrite the first one's only copy, which the single
     * 'current' row did silently.
     */
    private var draftId: String? = null

    /**
     * ⭐ True when the rooms on the grid came off a scan that could not work out WHERE they go, so
     * they are parked in a row of equal squares rather than drawn as a plan.
     *
     * The editor needs to know because a parking row is not a home: it must not be titled "Place
     * your rooms" as though it were finished, and the app must not ask shape questions about the box
     * around it. See `GuidedGridContent`'s `roomsUnplaced`.
     *
     * ⭐ It IS persisted with the draft as of v0.6.6, having deliberately not been before. The old
     * reasoning — "re-parking a home a day later would be the app forgetting work they had done" —
     * stopped holding the moment resuming became something the user does ON PURPOSE from the
     * saved-homes list. It can only ever be true when nothing has been moved (any edit clears it),
     * so restoring it never forgets work; leaving it out, an unplaced scan came back titled "Place
     * your rooms" over a parking row and then asked whether the leftovers of that row were part of
     * the home.
     */
    var roomsUnplaced by mutableStateOf(false)
        private set

    /**
     * Set by the scan flow as it hands its rooms over. Cleared by [startAgain].
     *
     * ⚠ It nudges the save. The scan flow sets the rooms and THEN sets this, so relying on the
     * debounce window to catch the second change would make "does an unplaced scan come back
     * unplaced?" a question about timing rather than about behaviour.
     */
    fun markRoomsUnplaced(unplaced: Boolean) { roomsUnplaced = unplaced; markDirty() }

    init {
        // ⭐⭐ NOTHING IS RESTORED HERE, and that is the fix (v0.6.6). This used to load the leftover
        // draft the moment the flow was entered — so tapping "add a home" and choosing "draw it on a
        // grid" or "upload a plan" handed back yesterday's half-finished home instead of a clean
        // start, and the user had to notice a card and press "start this home again" to get the
        // blank grid they had just asked for. An unfinished home is now listed on the saved-homes
        // screen and comes back ONLY when its own row is tapped — see [resumeDraft].
        viewModelScope.launch {
            dirty.debounce(50).collectLatest {
                // ⚠ The draft is written BEFORE the engine runs, not after. Scoring is the slow part,
                // and the whole point of this row is to survive being killed at an arbitrary moment —
                // including during that computation.
                if (planId == null) {
                    withContext(NonCancellable) { persistDraft() }
                }
                val plan = buildPlan() ?: run { _analysis.value = null; return@collectLatest }
                val result = withContext(compute) { engine.analyze(plan) }
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

        // ⭐ Process-death recovery. A fresh entry has an empty handle, so this changes nothing
        // about v0.6.6's "nothing is restored on entry" rule — the handle only carries ids across
        // an OS kill of THIS session. A saved home wins over a draft: owning both means the draft
        // became that home.
        val killedWithPlan = handle.get<String>(KEY_PLAN_ID)
        val killedWithDraft = handle.get<String>(KEY_DRAFT_ID)
        when {
            killedWithPlan != null -> loadById(killedWithPlan)
            killedWithDraft != null -> resumeDraft(killedWithDraft)
        }
    }

    // --- mutations (each nudges a debounced recompute) ---

    fun updateRooms(list: List<GridRoom>) {
        // ⭐ Any change to the rooms' GEOMETRY means the user has started arranging them, so they
        // are no longer parked in the row a scan left them in — and every question the editor asks
        // about the shape of what is on screen becomes a fair one.
        //
        // ⚠ GEOMETRY, not any change (audit B4). Correcting a room's KIND — "that's a study, not a
        // bedroom" — moves nothing, but it used to clear this flag too: the screen flipped to
        // "Place your rooms" and started asking shape questions about the parking row, the exact
        // state the flag was built to prevent. A retype keeps every id, position and size, so
        // comparing those is precisely "did the user arrange anything?".
        //
        // ⚠ This is only safe because the scan flow calls [markRoomsUnplaced] AFTER seeding the
        // rooms, not before. Living in the ViewModel is what makes it survive a rotation, which the
        // first version — remembered inside the editor — did not.
        val geometryUnchanged = list.size == rooms.size &&
            list.zip(rooms).all { (a, b) ->
                a.id == b.id && a.col == b.col && a.row == b.row && a.w == b.w && a.h == b.h
            }
        if (!geometryUnchanged) roomsUnplaced = false
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

    /**
     * Write (or remove) this session's unfinished-home row.
     *
     * Two rules, both of them about what the saved-homes screen is allowed to offer back:
     *  - a session with nothing on the grid owns no row — so passing through the flow leaves nothing
     *    behind, and clearing the grid takes the row away rather than leaving an empty home listed;
     *  - the id is minted here, on the first edit, so it belongs to this home and no other.
     */
    private suspend fun persistDraft() {
        val snap = snapshot()
        if (snap.isEmpty) {
            draftId?.let { repo.clearDraft(it) }
            draftId = null
            handle.remove<String>(KEY_DRAFT_ID)
            return
        }
        val id = draftId ?: "draft-${now()}".also {
            draftId = it
            handle[KEY_DRAFT_ID] = it
        }
        repo.saveDraft(id, snap, now())
    }

    /** True once the plan has enough to score (at least one room). */
    fun canScore(): Boolean = rooms.isNotEmpty()

    // --- persistence ---

    fun save() {
        // ⚠ Build FIRST, claim the id only once the plan exists. Claiming it before a failed build
        // flipped this session into "already saved" mode with no row saved: the autosave loop
        // (`if (planId == null) persistDraft()`) stopped writing drafts forever after that one tap
        // (4 Aug 2026 audit, B1). The id still reaches the serialized Plan directly, so Plan.id
        // matches its row id exactly as before.
        val id = planId ?: "plan-${now()}"
        val plan = buildEnginePlan(rooms, door, intent, propertyType, north, id, cutOutCells, siteAnswers)
            ?: return
        planId = id
        handle[KEY_PLAN_ID] = id
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
                val a = withContext(compute) { engine.analyze(plan) }
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
                // ⭐ This home is now a real saved row, so its unfinished-home row has done its job.
                // Dropping it here is what makes "there is a draft" mean exactly "you never finished
                // this one" — so the saved-homes screen never lists the same home twice, once as a
                // finished home and once as an unfinished one.
                draftId?.let { repo.clearDraft(it) }
                draftId = null
                handle.remove<String>(KEY_DRAFT_ID)
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
        roomsUnplaced = roomsUnplaced,
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
        // ⚠ LAST, and it has to be. Assigning `rooms` above does not run through updateRooms(), so
        // nothing has cleared this — but any future edit here that does would silently wipe it, and
        // an unplaced scan would come back pretending to be a finished plan.
        roomsUnplaced = d.roomsUnplaced
    }

    /**
     * ⭐ Bring back one unfinished home, because the user tapped its row on the saved-homes screen.
     *
     * This is the ONLY way a half-finished home returns to the screen (v0.6.6). Everything else —
     * "add a home", "draw it on a grid", "upload a plan" — starts from nothing, which is what the
     * owner asked for and what those words already promised.
     *
     * Tolerant: a row that has gone missing or can no longer be read leaves the user on a clean
     * empty grid rather than on an error.
     */
    fun resumeDraft(id: String) {
        if (draftId == id) return                 // already showing it (a recomposition, a rotation)
        viewModelScope.launch {
            val draft = repo.loadDraft(id) ?: return@launch
            if (draft.isEmpty) return@launch
            applyDraft(draft)
            draftId = id
            handle[KEY_DRAFT_ID] = id
            restoredFromDraft = true
            markDirty()
        }
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
        // Drop the row this session owned and forget it, so the next thing drawn starts a new
        // unfinished home rather than resurrecting the one just thrown away.
        val discarded = draftId
        draftId = null
        handle.remove<String>(KEY_DRAFT_ID)
        handle.remove<String>(KEY_PLAN_ID)
        viewModelScope.launch { withContext(NonCancellable) { discarded?.let { repo.clearDraft(it) } } }
    }

    /** Load an existing saved home into the flow (reopen from the saved-plans list). */
    fun load(saved: SavedPlan) {
        planId = saved.id
        handle[KEY_PLAN_ID] = saved.id
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
            val result = withContext(compute) { engine.analyze(saved.plan) }
            _analysis.value = result
            // ⚠ Deliberately NO write-back here when the ruleset has moved since this home was
            // saved. The score-change card on the home screen owns that write, and only after the
            // user has read the old number, the new number and the reason and tapped through it
            // (HomeViewModel's contract). Writing from here on open bypassed the card: the home
            // vanished from the notice unacknowledged and jumped the list as "Updated today" for an
            // edit the user never made. The screen the user is looking at shows the live number
            // either way. (4 Aug 2026 audit.)
        }
    }

    private companion object {
        // Defensive only — every persistence path assigns a real "Home N" name before writing, so
        // this should never actually reach the DB. Kept so a SavedPlan can never be built with null.
        const val FALLBACK_NAME = "My home"

        // SavedStateHandle keys — the two ids that let a process-killed session find its own work.
        const val KEY_DRAFT_ID = "newplan.draftId"
        const val KEY_PLAN_ID = "newplan.planId"
    }

    /**
     * Convert the placed grid rooms + door into the engine's [Plan]. The maths lives in the pure
     * [buildEnginePlan] (PlanConversion.kt) so the screenshot harness can build the exact same input
     * — this delegate is the ViewModel's binding of it to the live draft state.
     */
    fun buildPlan(): Plan? =
        buildEnginePlan(rooms, door, intent, propertyType, north, planId ?: "draft", cutOutCells, siteAnswers)
}
