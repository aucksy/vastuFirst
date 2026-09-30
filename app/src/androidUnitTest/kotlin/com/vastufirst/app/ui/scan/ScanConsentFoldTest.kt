package com.vastufirst.app.ui.scan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.designsystem.theme.VastuTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE PRIVACY CARD'S FIVE FACTS, FOLDED (owner, 30 Sep 2026): *"Put its five facts inside one folded
 * list, shut by default, headed 'What happens to your plan (5)' ... Keep every fact word for word inside
 * the fold."* The screen showed 172 words before scrolling; the facts were most of them.
 *
 * One test, and it holds all three promises at once: at first draw not one fact is on the screen; one
 * tap on the heading puts every one of them there, each title and each sentence exactly as it was
 * written; and the words that must never fold — the headline, the button that agrees, the line about
 * Settings and the way to draw instead — are there from the start.
 *
 * ⚠ The facts are written out here in full, on purpose. They are what a person agrees to, so a change
 * to any of them has to be a change somebody makes to this test as well, never a quiet one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ScanConsentFoldTest {

    private val facts = listOf(
        "What we send" to
            "The one picture or PDF you choose. Nothing else — no name, no phone number, no location.",
        "Who reads it" to
            "A relay called OpenRouter passes it to an AI model from OpenAI, which we ask twice and keep the " +
            "fuller reading — and sometimes a second model from Google for a second opinion. Their " +
            "computers are abroad.",
        "What we ask it" to
            "Only to read what is printed on your plan — the room names and sizes, and where each room " +
            "sits. It is never asked anything about Vastu.",
        "What we keep" to
            "Nothing. Your plan is not stored by us, and it stays in your phone's own storage.",
        "Who works out your score" to
            "Your phone does, on its own, exactly as it does for a home you draw by hand.",
    )

    /** What stays on the page whether the list is folded or not. */
    private val alwaysShown = listOf(
        "Your plan leaves this phone",
        "I agree — read my plan",
        "You can turn this off again at any time in Settings.",
        "Draw it on a grid instead",
    )

    @Test
    fun `the five facts are absent at first draw, and one tap shows every one word for word`() = runComposeUiTest {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-port-xhdpi")
        setContent { VastuTheme { ScanConsentScreen(onAgree = {}, onDrawInstead = {}, onBack = {}) } }

        alwaysShown.forEach { onAllNodesWithText(it).assertCountEquals(1) }
        facts.forEach { (title, body) ->
            onAllNodesWithText(title).assertCountEquals(0)
            onAllNodesWithText(body).assertCountEquals(0)
        }

        // The heading says what the list is and how many are in it. Scrolled to first: a tap below the
        // fold lands nowhere and raises no error.
        onNodeWithText("What happens to your plan (5)", ignoreCase = true).performScrollTo().performClick()

        facts.forEach { (title, body) ->
            onAllNodesWithText(title).assertCountEquals(1)
            onAllNodesWithText(body).assertCountEquals(1)
        }
        alwaysShown.forEach { onAllNodesWithText(it).assertCountEquals(1) }
    }
}
