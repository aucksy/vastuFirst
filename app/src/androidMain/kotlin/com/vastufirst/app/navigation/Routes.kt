package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). The guided-grid path lives in a nested "newplan"
 * graph so every screen in it shares one [com.vastufirst.app.ui.newplan.NewPlanViewModel].
 *
 * ⭐⭐ SHORTER, SINCE 30 SEP 2026 (owner: *"decreasing the number of screens and text to read.. its
 * just too much … let the user get to the point quickly"*). The paths now are:
 *
 *  · **Scanned** — Welcome → Add a home → (the privacy card, first scan only) → the phone's own
 *    picker → reading → **"We read N rooms"**, which lists the rooms (folded) and asks which way is
 *    North on the reader's own photograph → the front door *(only when the plan did not name its own
 *    entrance)* → Report.
 *  · **Drawn by hand** — Welcome → Add a home → Guided grid (rooms, then the front door) → Mark North
 *    → Report. The sample goes from Add a home straight to Mark North.
 *
 * ⛔ THERE IS NO "CHECK WHAT WE READ" SCREEN any more (owner, 30 Sep 2026). It listed the scanned
 * rooms a second time, between North and the report, and the report lists them a third. Each of its
 * jobs moved on purpose — see the note at the top of ScanPicture.kt — and nothing in this graph
 * reaches it. Do not bring it back under another name.
 *
 * ⛔ THERE IS NO SCORE SCREEN, since 10 Aug 2026 (owner: "After the North is marked, jump straight
 * to Report screen"). Whatever the last step is, it goes straight to the report.
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
     * ⭐ Which door a scan was opened through — the phone's file picker or its camera — carried from
     * Add a home, through the privacy card when it is shown, to the scan screen, which opens that
     * picker the moment it appears (30 Sep 2026). The value is a [com.vastufirst.app.ui.scan.ScanStart]
     * name.
     */
    const val ARG_START = "start"

    /**
     * The privacy gate in front of [SCAN]. A separate destination rather than a dialog, so the
     * ordering is structural: the only route to the scanner passes through it, and it cannot be
     * skipped by a state that forgot to check a flag.
     */
    const val SCAN_CONSENT = "scan_consent"
    const val SCAN_CONSENT_ROUTE = "$SCAN_CONSENT?$ARG_START={$ARG_START}"
    fun scanConsentThen(start: String) = "$SCAN_CONSENT?$ARG_START=$start"

    /**
     * The scan: the picker, the wait, and the result. ⭐ For a read whose rooms were placed, the result
     * also asks which way is North, on the reader's own photograph — the North dial is not a separate
     * step on this path any more.
     */
    const val SCAN = "scan"
    const val SCAN_ROUTE = "$SCAN?$ARG_START={$ARG_START}"
    fun scanStarting(start: String) = "$SCAN?$ARG_START=$start"

    /**
     * ⭐ THE FRONT DOOR, MARKED ON THE PHOTO (owner, 6 Aug 2026: *"marking the Door and north should
     * happen only on this actual floor plan and not on the floor plan builder and modifier
     * screen"*). Reached in the flow only when the plan did not name its own entrance — when it did,
     * [com.vastufirst.app.ui.newplan.frontDoorFromEntrance] has already answered, the flow goes
     * straight to the report, and the report says where the door was read from.
     */
    const val SCAN_DOOR = "scan_door"
    const val GUIDED_GRID = "guided_grid"
    const val MARK_NORTH = "mark_north"

    /**
     * ⭐ The one door back into an unfinished home (v0.6.6). The editor's destination takes an
     * optional id: arriving WITHOUT it means "start a fresh home", which is what every other way in
     * now does; arriving WITH one means "the user tapped this unfinished home on the saved-homes
     * screen, put it back".
     *
     * The ids are the app's own (`draft-<millis>`, plus the single `current` row older builds wrote),
     * so they contain nothing a route would have to escape.
     */
    const val ARG_DRAFT_ID = "draftId"

    /**
     * ⭐ The editor opened straight on its DOOR step — how a report of a home drawn by hand changes its
     * front door. The rooms are already on the grid, so this lands on them with "tap the wall where
     * your door is".
     */
    const val ARG_DOOR_MODE = "doorMode"
    const val GUIDED_GRID_ROUTE =
        "$GUIDED_GRID?$ARG_DRAFT_ID={$ARG_DRAFT_ID}&$ARG_DOOR_MODE={$ARG_DOOR_MODE}"
    fun guidedGridForDraft(draftId: String) = "$GUIDED_GRID?$ARG_DRAFT_ID=$draftId"
    fun guidedGridForDoor() = "$GUIDED_GRID?$ARG_DOOR_MODE=true"

    /**
     * ⭐ North, opened from an already-read home rather than from the end of the drawing flow
     * (v0.6.6). The flag decides the way OUT — back to the report it came from, instead of pushing a
     * second report on top of the first — and what the button says.
     *
     * ⚠ Named `fromScore` until 10 Aug 2026, when the score screen it referred to was removed. A
     * flag that names a screen the app no longer has is how the next session reinstates it — which
     * is also why the `fromScan` flag went with "Check what we read" on 30 Sep 2026.
     */
    const val ARG_FROM_REPORT = "fromReport"
    const val MARK_NORTH_ROUTE =
        "$MARK_NORTH?$ARG_FROM_REPORT={$ARG_FROM_REPORT}&$ARG_DRAFT_ID={$ARG_DRAFT_ID}"
    fun markNorthFromReport() = "$MARK_NORTH?$ARG_FROM_REPORT=true"

    /**
     * ⭐⭐ CARRYING ON WITH AN UNFINISHED **SCANNED** HOME (owner, 17 Aug 2026: *"'Carry On' option
     * from Home Screen is taking user to manual grid… ensure this does not come except in scan
     * flow"*). A scanned home resumes at North, on its own photograph, and goes on to its report —
     * the rooms, their kinds and the front door all survive, which is everything the score is made of.
     */
    fun markNorthForDraft(draftId: String) = "$MARK_NORTH?$ARG_DRAFT_ID=$draftId"

    /**
     * ⭐ The on-photo door screen opened from a FINISHED REPORT ("Change front door") rather than from
     * the middle of the flow. Exactly [ARG_FROM_REPORT] on North, for exactly the same reason: the flag
     * decides the way OUT, and what the button at the bottom is allowed to SAY.
     */
    const val SCAN_DOOR_ROUTE = "$SCAN_DOOR?$ARG_FROM_REPORT={$ARG_FROM_REPORT}"
    fun scanDoorFromReport() = "$SCAN_DOOR?$ARG_FROM_REPORT=true"

    /**
     * The optional "a few more things" step — water tank, tree, the road outside. Offered on the
     * report: getting to a reading is meant to be quick, and forcing four more questions on everyone
     * would cost every user time to catch the minority who have something to report.
     *
     * ⚠ It was also offered at the end of "Check what we read" from 17 Aug 2026; that screen is gone,
     * so the report — which always carried the same offer — is its one place now, and "Done" always
     * returns there.
     */
    const val MORE_DETAILS = "more_details"

    const val UNLOCK = "unlock"

    /**
     * ⭐ Where every path LANDS. The optional id is how an already-saved home is reopened from the
     * saved-homes list; arriving without one means the home is already in the shared draft, and
     * passing an id then would pull a different home over the top of it.
     */
    const val REPORT = "report"
    const val ARG_PLAN_ID = "planId"
    const val REPORT_ROUTE = "$REPORT?$ARG_PLAN_ID={$ARG_PLAN_ID}"
    fun reportForPlan(planId: String) = "$REPORT?$ARG_PLAN_ID=$planId"
}
