package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). The guided-grid path lives in a nested "newplan"
 * graph so every screen in it shares one [com.vastufirst.app.ui.newplan.NewPlanViewModel].
 *
 * There are two endings and they differ only in where the checking happens:
 *
 *  · **Drawn by hand** — Home → Welcome → Add home → Guided grid → Mark North → Report.
 *  · **⭐ Scanned** — Home → Welcome → Add home → Scan → **Mark North** → **Check what we read** →
 *    front door *(only when the plan did not name its own entrance)* → Report.
 *
 * ⭐⭐ NORTH MOVED IN FRONT OF THE CHECK SCREEN ON 11 AUG 2026, and it is not a preference. The
 * owner's original list asks every row of "Check what we read" to carry the room's one-word result
 * and the direction it sits in — and a room HAS no direction until North is marked. With North
 * behind that screen there was nothing true to print, which is the whole reason those two pills had
 * never been built. Whatever the last step turns out to be, it goes straight to the report: there is
 * still no free score screen anywhere in this graph.
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
     * ⭐ The ON-PHOTO review (owner request, 4 Aug 2026). The user's own scanned picture stays on
     * screen and the extracted rooms are a tappable checklist over it. Reached only when the scan
     * PLACED its rooms; a scan that could not place them goes to the grid, which remains the only
     * surface that can fix where a room sits.
     *
     * ⭐ It now comes AFTER [MARK_NORTH] (11 Aug 2026), so each row can carry the room's direction
     * and its one-word result — neither of which exists before North is marked.
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
        "$MARK_NORTH?$ARG_FROM_REPORT={$ARG_FROM_REPORT}&$ARG_FROM_SCAN={$ARG_FROM_SCAN}" +
            "&$ARG_DRAFT_ID={$ARG_DRAFT_ID}"
    fun markNorthFromReport() = "$MARK_NORTH?$ARG_FROM_REPORT=true"
    fun markNorthFromScan() = "$MARK_NORTH?$ARG_FROM_SCAN=true"

    /**
     * ⭐⭐ CARRYING ON WITH AN UNFINISHED **SCANNED** HOME (owner, 17 Aug 2026: *"'Carry On' option
     * from Home Screen is taking user to manual grid… ensure this does not come except in scan
     * flow"*).
     *
     * A scan that is left part-way through — the reader backs out at the North dial, or the phone
     * takes the app away — still leaves its rooms in the unfinished list, which is right: that is
     * real work. But "Carry on" sent EVERY unfinished home into the grid editor, and the grid editor
     * is precisely the screen the owner removed from the scan flow on 6 August. Somebody who had
     * photographed a plan was handed a builder's canvas full of squares they never drew.
     *
     * So a scanned home resumes at North instead, and goes on to its report from there. The
     * photograph itself is gone (it is never written to disk, by design), so the review screen has
     * nothing to show and is skipped — the rooms, their kinds and the front door all survive, which
     * is everything the score is made of.
     */
    fun markNorthForDraft(draftId: String) = "$MARK_NORTH?$ARG_DRAFT_ID=$draftId"

    /**
     * ⭐ The on-photo door screen opened from a FINISHED REPORT ("change where the front door is")
     * rather than from the middle of the flow. Exactly [ARG_FROM_REPORT] on North, for exactly the
     * same reason: the flag decides the way OUT — back to the report it came from, instead of
     * pushing a second report on top of the first.
     *
     * It also decides what the button at the bottom is allowed to SAY — "back to my report" for a
     * reader who came from one, and "read my home" when this is the last step of the flow. A control
     * naming a screen it does not open is a defect this project has logged twice.
     */
    const val SCAN_DOOR_ROUTE = "$SCAN_DOOR?$ARG_FROM_REPORT={$ARG_FROM_REPORT}"
    fun scanDoorFromReport() = "$SCAN_DOOR?$ARG_FROM_REPORT=true"

    /**
     * The optional "a few more things" step — water tank, tree, the road outside. Reached from the
     * report rather than placed before it: getting to a reading is meant to be quick, and forcing
     * four more questions on everyone would cost every user time to catch the minority who have
     * something to report. The report's "what this covers" section says what it did and did not
     * look at instead, and offers this.
     */
    const val MORE_DETAILS = "more_details"

    /**
     * ⭐ It is now offered in TWO places (owner, 17 Aug 2026), so it has to know where it will send
     * the reader back to. Offered at the END of "Check what we read" it returns to that checklist;
     * offered on the report it returns to the report. The flag decides the words on its one button,
     * which must always name the screen it actually opens.
     */
    const val MORE_DETAILS_ROUTE = "$MORE_DETAILS?$ARG_FROM_REPORT={$ARG_FROM_REPORT}"
    fun moreDetailsFromReport() = "$MORE_DETAILS?$ARG_FROM_REPORT=true"

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
