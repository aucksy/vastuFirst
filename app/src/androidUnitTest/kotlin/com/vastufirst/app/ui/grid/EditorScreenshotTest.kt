package com.vastufirst.app.ui.grid

import android.app.Application
import com.vastufirst.app.render.captureAcrossMatrix
import com.vastufirst.app.render.writeManifestAcrossMatrix
import com.vastufirst.app.ui.newplan.SamplePlans
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
// Render from a PLAIN Application, not the app's VastuApp — the editor here is stateless fixture
// content and needs no DI. Booting VastuApp would call startKoin() once per test method in the
// shared JVM and throw KoinApplicationAlreadyStartedException on the second (UI-POLISH §6 harness).
@Config(application = Application::class)
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

    // A rectangular plot (8 wide × 5 deep) — proves the grid draws at the true aspect ratio with
    // square cells and per-axis third-bands, not a forced square (editor Build C, rectangular plots).
    @Test
    fun editor_wide() {
        captureAcrossMatrix("editor-wide") {
            GuidedGridContent(
                rooms = emptyList(),
                door = null,
                onRoomsChange = {},
                onDoorChange = {},
                onNext = {},
                cols = 8,
                rows = 5,
            )
        }
        writeManifestAcrossMatrix("editor-wide") {
            GuidedGridContent(emptyList(), null, {}, {}, {}, cols = 8, rows = 5)
        }
    }

    // L1 measurement manifests (semantics geometry) — the gate reads these to catch zero-size
    // nodes, clipped text, tiny touch targets and unreachable CTAs (UI-POLISH §6.5).
    @Test
    fun editor_manifest() {
        writeManifestAcrossMatrix("editor") {
            GuidedGridContent(sample.rooms, sample.door, {}, {}, {})
        }
    }

    @Test
    fun editor_empty_manifest() {
        writeManifestAcrossMatrix("editor-empty") {
            GuidedGridContent(emptyList(), null, {}, {}, {})
        }
    }
}
