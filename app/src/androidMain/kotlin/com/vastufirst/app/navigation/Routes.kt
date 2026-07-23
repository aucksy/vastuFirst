package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). The guided-grid path lives in a nested "newplan"
 * graph so every screen in it shares one [com.vastufirst.app.ui.newplan.NewPlanViewModel]:
 * Home → Welcome → Add home → (Guided grid | Sample) → Mark North → Score → Report.
 */
object Routes {
    const val HOME = "home"

    const val NEWPLAN_GRAPH = "newplan"
    const val WELCOME = "welcome"
    const val ADD_HOME = "add_home"
    const val GUIDED_GRID = "guided_grid"
    const val MARK_NORTH = "mark_north"
    const val SCORE = "score"
    const val UNLOCK = "unlock"
    const val REPORT = "report"
}
