package com.vastufirst.app.navigation

/**
 * The Phase 2 screen graph (Product PRD §6). Routes are added as each screen lands; the
 * guided-grid path is: Home → Welcome → Add home → (Guided grid | Sample) → Mark North →
 * Score → Report.
 */
object Routes {
    const val HOME = "home"          // saved plans (start destination)
    const val WELCOME = "welcome"    // language + intent
    // Add home, guided grid, mark north, score, report — land in Block C.
}
