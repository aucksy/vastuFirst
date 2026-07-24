package com.vastufirst.app.render

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.data.SavedPlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisNote
import com.vastufirst.shared.AnalysisQuality
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

    // --- score-driven screens (Mark North, Score, Report) ---
    // The bundled builder's-draft sample, converted the SAME way the app converts it (buildEnginePlan)
    // and scored by the REAL engine, so these screens render against a genuine Analysis — the zone
    // map colours, the ranked defects, the "already right" rows and the not-assessed list are all the
    // engine's actual output, not a hand-faked stand-in.
    private val sample = SamplePlans.all.first()

    val sampleRooms: List<GridRoom> = sample.rooms
    val sampleNorth: Int = sample.north
    val sampleIntent: Intent = Intent.BUILDING

    val sampleAnalysis: Analysis =
        VastuEngine().analyze(
            buildEnginePlan(
                rooms = sample.rooms,
                door = sample.door,
                intent = sampleIntent,
                propertyType = PropertyType.INDEPENDENT_HOUSE,
                north = sample.north,
                planId = "fixture",
            )!!,
        )

    /** The "plan too sparse to read" state — the app degrades to a friendly guidance card here, never
     *  a bare red 0 ([[vastufirst-no-error-states]]). Derived from the real analysis so every other
     *  field is valid; only the quality + note change. */
    val insufficientAnalysis: Analysis = sampleAnalysis.copy(
        quality = AnalysisQuality.INSUFFICIENT,
        notes = listOf(
            AnalysisNote(
                code = "FIXTURE_INSUFFICIENT",
                message = "Add a few rooms and your front door, and we'll read your home.",
            ),
        ),
    )
}
