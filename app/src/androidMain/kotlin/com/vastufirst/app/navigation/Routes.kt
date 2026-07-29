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

    const val NEWPLAN_GRAPH = "newplan"
    const val WELCOME = "welcome"
    const val ADD_HOME = "add_home"
    const val SCAN = "scan"
    const val GUIDED_GRID = "guided_grid"
    const val MARK_NORTH = "mark_north"
    const val SCORE = "score"
    const val UNLOCK = "unlock"
    const val REPORT = "report"
}
