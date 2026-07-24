package com.vastufirst.app.render

import com.vastufirst.data.SavedPlan
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType

/**
 * Static fixture state for the screenshot harness — never a real ViewModel (which needs DI, a
 * repository and a Main dispatcher, all fragile headless; UI-POLISH §6, stateless-content).
 *
 * These are the render-only inputs for the ViewModel-backed screens' stateless `…Content` seams.
 * The [Plan] inside a [SavedPlan] is intentionally minimal: the saved-plans list only reads
 * id/name/intent/score, so the fixture proves the row layout, not the engine.
 */
object RenderFixtures {

    private fun savedPlan(id: String, name: String, intent: Intent, score: Int) = SavedPlan(
        id = id,
        name = name,
        intent = intent,
        propertyType = PropertyType.INDEPENDENT_HOUSE,
        plan = Plan(
            id = id,
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = intent,
            levels = listOf(Level(index = 0, outline = squareOutline())),
            northOffsetDegrees = 0,
        ),
        score = score,
        ruleSetVersion = "fixture",
        unlocked = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun squareOutline() = listOf(
        Point(0.0, 0.0), Point(8.0, 0.0), Point(8.0, 8.0), Point(0.0, 8.0),
    )

    /** Two saved homes — enough to prove the row layout, the score pill and side-by-side spacing. */
    val savedPlans: List<SavedPlan> = listOf(
        savedPlan("p1", "Builder's draft — 2BHK", Intent.BUILDING, 31),
        savedPlan("p2", "Compact 2BHK flat", Intent.BUYING, 68),
    )
}
