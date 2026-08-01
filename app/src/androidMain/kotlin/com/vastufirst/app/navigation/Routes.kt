package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). The guided-grid path lives in a nested "newplan"
 * graph so every screen in it shares one [com.vastufirst.app.ui.newplan.NewPlanViewModel]:
 * Home → Welcome → Add home → (Guided grid | Sample) → Mark North → Score → Report.
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
    const val GUIDED_GRID = "guided_grid"
    const val MARK_NORTH = "mark_north"
    const val SCORE = "score"

    /**
     * The optional "a few more things" step — water tank, tree, the road outside. Reached from
     * the score rather than placed before it: the free score is meant to be quick, and forcing
     * four more questions on everyone would cost every user time to catch the minority who have
     * something to report. The score screen says what it did and did not look at instead.
     */
    const val MORE_DETAILS = "more_details"
    const val UNLOCK = "unlock"
    const val REPORT = "report"
}
