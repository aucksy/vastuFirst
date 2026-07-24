package com.vastufirst.app.ui.grid

import com.vastufirst.app.render.captureAcrossMatrix
import com.vastufirst.app.ui.newplan.SamplePlans
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The floor-plan editor (GuidedGridScreen / GuidedGridContent) rendered across the whole §6.4
 * matrix — the FIRST time any machine in this project has drawn this screen (EDITOR-REWORK-PLAN.md
 * Build B item 10). It renders two states that matter most:
 *
 *  - `editor`       — the sample home placed, so rooms, door, chip, selected-room tools and the
 *                     direction labels are all on screen at once.
 *  - `editor-empty` — the empty grid. This is the state the user lands in every time, and the one
 *                     that shipped INVISIBLE in v0.2.1 because the grid measured to zero height. If
 *                     this render shows a square grid with a caption, the original defect is dead.
 *
 * NATIVE graphics mode is mandatory — LEGACY renders a blank canvas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {

    private val sample = SamplePlans.all.first()

    @Test
    fun editor_withRooms() {
        captureAcrossMatrix("editor") {
            GuidedGridContent(
                rooms = sample.rooms,
                door = sample.door,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
            )
        }
    }

    @Test
    fun editor_empty() {
        captureAcrossMatrix("editor-empty") {
            GuidedGridContent(
                rooms = emptyList(),
                door = null,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
            )
        }
    }
}
