package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). The guided-grid path lives in a nested "newplan"
 * graph so every screen in it shares one [com.vastufirst.app.ui.newplan.NewPlanViewModel]:
 * Home → Welcome → Add home → (Guided grid | Scan) → Mark North → Report.
 *
 * ⛔ THERE IS NO SCORE SCREEN, since 10 Aug 2026 (owner: "After the North is marked, jump straight
 * to Report screen"). The free score used to sit between North and the report, showing the number,
 * a zone map and the top three problems, and then selling the report. The report now opens on the
 * number itself and reads the entrance, kitchen and toilets in full for free, so the middle screen
 * was asking a reader to look at a summary of a document they were about to be handed. Everything
 * only it had — the zone map, the way back into North and the front door, the "what this covers"
 * caveat and the extra site questions — moved into the report; nothing was dropped.
 */
object Routes {
    // A one-frame decider (start destination): sends a returning user to their saved plans, and a
    // first-time user straight into the flow — never a "No plans yet" dead-end on a fresh install.
    const val LAUNCH = "launch"

    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LEGAL = "legal"
    const val PRIVACY = "privacy"

    const val NEWPLAN_GRAPH = "newplan"
    const val WELCOME = "welcome"
    const val ADD_HOME = "add_home"

    /**
     * The privacy gate in front of [SCAN]. A separate destination rather than a dialog, so the
     * ordering is structural: the only route to the scanner passes through it, and it cannot be
     * skipped by a state that forgot to check a flag.
     */
    const val SCAN_CONSENT = "scan_consent"
    const val SCAN = "scan"

    /**
     * ⭐ The ON-PHOTO review (owner request, 4 Aug 2026; Settings toggle). The user's own scanned
     * picture stays on screen and the extracted rooms are a tappable checklist over it — the
     * alternative to confirming on the guided grid, reached only when the toggle is on AND the
     * scan placed its rooms; every other scan still confirms on the grid, which remains the only
     * surface that can FIX a wrong read.
     */
    const val SCAN_REVIEW = "scan_review"

    /**
     * ⭐ THE FRONT DOOR, MARKED ON THE PHOTO (owner, 6 Aug 2026: *"marking the Door and north should
     * happen only on this actual floor plan and not on the floor plan builder and modifier
     * screen"*). It replaces the old hop into the editor's door mode. Reached only when the plan did
     * not name its own entrance — when it did, [com.vastufirst.app.ui.newplan.frontDoorFromEntrance]
     * has already answered and the review screen states the answer instead of asking the question.
     */
    const val SCAN_DOOR = "scan_door"
    const val GUIDED_GRID = "guided_grid"
    const val MARK_NORTH = "mark_north"

    /**
     * ⭐ The one door back into an unfinished home (v0.6.6). The editor's destination takes an
     * optional id: arriving WITHOUT it means "start a fresh home", which is what every other way in
     * now does; arriving WITH one means "the user tapped this unfinished home on the saved-homes
     * screen, put it back". Before this, the app restored whatever draft it found by itself, so
     * choosing "draw it on a grid" handed back a half-finished plan the user had not asked for.
     *
     * The ids are the app's own (`draft-<millis>`, plus the single `current` row older builds wrote),
     * so they contain nothing a route would have to escape.
     */
    const val ARG_DRAFT_ID = "draftId"

    /**
     * ⭐ The editor opened straight on its DOOR step (audit B2). The on-photo review uses it: the
     * front door is the heaviest single input the engine weighs, and the on-photo flow used to go
     * from the review straight to North without ever asking for it — the classic grid flow asked,
     * the photo flow silently scored doorless. The rooms are already on the grid (populated
     * identically in both flows), so this lands on them with "tap the wall where your door is".
     */
    const val ARG_DOOR_MODE = "doorMode"
    const val GUIDED_GRID_ROUTE =
        "$GUIDED_GRID?$ARG_DRAFT_ID={$ARG_DRAFT_ID}&$ARG_DOOR_MODE={$ARG_DOOR_MODE}"
    fun guidedGridForDraft(draftId: String) = "$GUIDED_GRID?$ARG_DRAFT_ID=$draftId"
    fun guidedGridForDoor() = "$GUIDED_GRID?$ARG_DOOR_MODE=true"

    /**
     * ⭐ North, opened from an already-read home rather than from the end of the drawing flow
     * (v0.6.6). The flag decides only the way OUT: back to the report it came from, instead of
     * pushing a second report on top of the first. Until v0.6.6 a saved home's North could not be
     * changed at all — the only thing on offer was renaming it.
     *
     * ⚠ Named `fromScore` until 10 Aug 2026, when the score screen it referred to was removed. A
     * flag that names a screen the app no longer has is how the next session reinstates it.
     */
    const val ARG_FROM_REPORT = "fromReport"

    /**
     * ⭐ North, marked on the SCANNED PHOTO rather than on our redrawing of it (owner, 6 Aug 2026).
     * The compass ring turns around the picture exactly as it turns around the drawn rooms — the
     * plan itself never rotates — so the only difference is which image sits under the ring.
     *
     * A flag on the route rather than "show a photo whenever one is lying around": this screen is
     * also reached by drawing a home by hand and by reopening a saved one, and a picture left over
     * from an earlier scan appearing under someone else's home would be a lie about whose plan is
     * being read.
     */
    const val ARG_FROM_SCAN = "fromScan"
    const val MARK_NORTH_ROUTE =
        "$MARK_NORTH?$ARG_FROM_REPORT={$ARG_FROM_REPORT}&$ARG_FROM_SCAN={$ARG_FROM_SCAN}"
    fun markNorthFromReport() = "$MARK_NORTH?$ARG_FROM_REPORT=true"
    fun markNorthFromScan() = "$MARK_NORTH?$ARG_FROM_SCAN=true"

    /**
     * The optional "a few more things" step — water tank, tree, the road outside. Reached from the
     * report rather than placed before it: getting to a reading is meant to be quick, and forcing
     * four more questions on everyone would cost every user time to catch the minority who have
     * something to report. The report's "what this covers" section says what it did and did not
     * look at instead, and offers this.
     */
    const val MORE_DETAILS = "more_details"
    const val UNLOCK = "unlock"

    /**
     * ⭐ Where the flow now LANDS, straight off the North dial (10 Aug 2026). The optional id is how
     * an already-saved home is reopened from the saved-homes list; arriving without one means the
     * home is already in the shared draft, and passing an id then would pull a different home over
     * the top of it.
     */
    const val REPORT = "report"
    const val ARG_PLAN_ID = "planId"
    const val REPORT_ROUTE = "$REPORT?$ARG_PLAN_ID={$ARG_PLAN_ID}"
    fun reportForPlan(planId: String) = "$REPORT?$ARG_PLAN_ID=$planId"
}
