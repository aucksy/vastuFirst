package com.vastufirst.app.render

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.designsystem.theme.VastuTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C13 — a TalkBack user must be able to *set* North, not just hear it. Google's ATF a11y gate checks
 * contrast / touch targets / labels but NOT whether an adjustable control exposes a set-value action,
 * so this proves it directly: both Mark-North controls (the dial and the slider) must carry a
 * `SetProgress` semantics action, and invoking it must actually change North. Headless — no device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class MarkNorthA11yTest {

    @Test
    fun both_controls_are_adjustable_by_talkback() = runComposeUiTest {
        var lastNorth = -1
        setContent {
            VastuTheme {
                MarkNorthContent(
                    rooms = RenderFixtures.sampleRooms,
                    north = RenderFixtures.sampleNorth,
                    analysis = RenderFixtures.sampleAnalysis,
                    onNorthChange = { lastNorth = it },
                    onRead = {},
                    onBack = {},
                )
            }
        }

        // The slider: has a set-value action, and using it sets North to that bearing.
        onNodeWithContentDescription("North bearing", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        assertEquals("slider setProgress must set North", 90, lastNorth)

        // The dial: same contract. (It wraps mod 360, so 180 stays 180.)
        onNodeWithContentDescription("Floor plan compass", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(180f) }
        assertEquals("dial setProgress must set North", 180, lastNorth)
    }
}
