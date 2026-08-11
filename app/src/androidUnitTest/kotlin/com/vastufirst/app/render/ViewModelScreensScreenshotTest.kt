package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import com.vastufirst.app.ui.home.DiscardDraftDialogContent
import com.vastufirst.app.ui.home.HomeContent
import com.vastufirst.app.ui.home.RemoveUnreadableDialogContent
import com.vastufirst.app.ui.home.RenameDialogContent
import com.vastufirst.app.ui.settings.SettingsContent
import com.vastufirst.app.ui.welcome.WelcomeContent
import com.vastufirst.shared.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first batch of ViewModel-backed screens — Welcome, Home, Settings — rendered + measured
 * across the §6.4 matrix for the FIRST time (UI-POLISH §6). Each is driven through its new stateless
 * `…Content(state, callbacks)` seam with a fixture and no-op callbacks; the ViewModel is never
 * constructed here (it needs DI + a Main dispatcher, fragile headless — stateless-content template).
 *
 * `home-empty` is rendered as its own screen because the empty state is the one that most often ships
 * broken (a list container that measures to nothing with no children), and it is what a first-time
 * user lands on.
 *
 * NATIVE graphics mode is mandatory (LEGACY renders a blank canvas); a PLAIN Application skips the
 * app's startKoin() so a second test in the shared JVM does not throw KoinApplicationAlreadyStarted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ViewModelScreensScreenshotTest {

    @Test
    fun welcome() {
        val content: @Composable () -> Unit = {
            WelcomeContent(intent = Intent.BUILDING, onIntentChange = {}, onContinue = {})
        }
        captureAcrossMatrix("welcome", content)
        writeManifestAcrossMatrix("welcome", content)
    }

    /**
     * ⭐ THE VERY FIRST SCREEN, IN THE STATE IT ACTUALLY OPENS IN — nothing chosen yet and Continue
     * greyed out. It had never been rendered. The only picture of Welcome showed an answer already
     * selected, which is a state no first-time user is ever in: the app starts with no intent and
     * the button disabled until one is picked. The screen every single customer sees before any
     * other was therefore the one screen no reviewer could look at.
     */
    @Test
    fun welcome_first_launch() {
        val content: @Composable () -> Unit = {
            WelcomeContent(intent = null, onIntentChange = {}, onContinue = {})
        }
        captureAcrossMatrix("welcome-first", content)
        writeManifestAcrossMatrix("welcome-first", content)
    }

    @Test
    fun home() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
            )
        }
        captureAcrossMatrix("home", content)
        writeManifestAcrossMatrix("home", content)
    }

    @Test
    fun home_empty() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = emptyList(), onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
            )
        }
        captureAcrossMatrix("home-empty", content)
        writeManifestAcrossMatrix("home-empty", content)
    }

    /**
     * ⭐ A saved home this build cannot read. Until now one such row threw inside the list's flow and
     * emptied the whole screen; the other homes now load and the missing one is SAID rather than
     * silently skipped — because a home that vanishes without a word looks exactly like a home the
     * app deleted by itself. Now named, with its own remove (audit B7) — the name column reads even
     * when the contents don't. No screenshot could ever reach this state by tapping, so it gets one.
     */
    @Test
    fun home_unreadable() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                unreadable = listOf(RenderFixtures.unreadableHome),
            )
        }
        captureAcrossMatrix("home-unreadable", content)
        writeManifestAcrossMatrix("home-unreadable", content)
    }

    /**
     * The are-you-sure behind that row's Remove. Its body carries the one fact the discard dialog's
     * doesn't: what is being given up is the CHANCE of a future rescue, not visible rooms.
     */
    @Test
    fun home_remove_unreadable() {
        val content: @Composable () -> Unit = {
            RemoveUnreadableDialogContent(name = "Home 3", onCancel = {}, onRemove = {})
        }
        captureAcrossMatrix("home-remove", content)
        writeManifestAcrossMatrix("home-remove", content)
    }

    /**
     * ⭐ "WE CHANGED A RULE, AND HERE IS WHAT IT DID TO YOUR NUMBER."
     *
     * The card shown when a Vastu ruling re-scores a home that was already saved — the thing that
     * stops a score moving behind somebody's back. It carries the most text of anything on this
     * screen (a heading, a paragraph of explanation, one line per home and a button), so it is
     * exactly the kind of block that has pushed its own button off the bottom of a 320 dp phone at
     * 200 % font before. No screenshot could reach this state by tapping, so it gets its own.
     *
     * One home moved and one did not, on purpose: both lines are on record in one picture.
     */
    @Test
    fun home_score_changed() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                scoreChanges = RenderFixtures.scoreChangeNotice,
            )
        }
        captureAcrossMatrix("home-scorechange", content)
        writeManifestAcrossMatrix("home-scorechange", content)
    }

    /**
     * ⭐⭐ THE UNFINISHED HOMES, LISTED (v0.6.6) — the screen the owner's first fix is really about.
     *
     * Until this release a half-finished home was invisible: the app quietly reloaded it the moment
     * anybody entered the drawing flow, so "draw it on a grid" handed back yesterday's work and there
     * was no list to choose from. Now they are rows above the finished homes, and this is the picture
     * that proves the two groups read as two groups — a headed section, a room count and a time where
     * a finished home carries a score, and a nameless home rendering properly (the ordinary case: a
     * home is not named until it is first saved).
     *
     * Rendered WITH finished homes below, because the risk is exactly that the two look alike.
     */
    @Test
    fun home_with_unfinished() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                drafts = RenderFixtures.savedDrafts, onOpenDraft = {}, onDiscardDraft = {},
            )
        }
        captureAcrossMatrix("home-unfinished", content)
        writeManifestAcrossMatrix("home-unfinished", content)
    }

    /**
     * ⭐ The same screen when the ONLY home is an unfinished one — a first-time user who was
     * interrupted. It must not fall through to "No plans yet", which would tell them their work was
     * gone while it sat safely on disk one row below.
     */
    @Test
    fun home_only_unfinished() {
        val content: @Composable () -> Unit = {
            HomeContent(
                plans = emptyList(), onAddHome = {}, onOpenPlan = {}, onSettings = {},
                onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                drafts = RenderFixtures.savedDrafts.take(1), onOpenDraft = {}, onDiscardDraft = {},
            )
        }
        captureAcrossMatrix("home-unfinished-only", content)
        writeManifestAcrossMatrix("home-unfinished-only", content)
    }

    /**
     * ⭐ "Throw away this unfinished home?" — the one destructive action on this screen, and one no
     * screenshot can reach by tapping. It names what is being lost rather than asking "are you sure",
     * and its two buttons have to stay side by side and reachable at 200 % font on a 320 dp phone.
     */
    @Test
    fun home_discard_unfinished() {
        val content: @Composable () -> Unit = {
            DiscardDraftDialogContent(roomCount = 5, onCancel = {}, onDiscard = {})
        }
        captureAcrossMatrix("home-discard", content)
        writeManifestAcrossMatrix("home-discard", content)
    }

    @Test
    fun home_rename() {
        // The rename box (B12) rendered as its own screen so it is actually SEEN before shipping
        // (UI-POLISH §6) — pre-filled with a home's current name.
        val content: @Composable () -> Unit = {
            RenameDialogContent(currentName = "Compact 2BHK flat", onCancel = {}, onSave = {})
        }
        captureAcrossMatrix("home-rename", content)
        writeManifestAcrossMatrix("home-rename", content)
    }

    @Test
    fun settings() {
        val content: @Composable () -> Unit = {
            SettingsContent(onLegal = {}, onBack = {}, onDeleteAll = {})
        }
        captureAcrossMatrix("settings", content)
        writeManifestAcrossMatrix("settings", content)
    }

    /**
     * ⭐⭐ SETTINGS AS A RELEASED BUILD ACTUALLY SHOWS IT. The picture above is taken with no
     * plan-reading key, which HIDES a whole section — the reader picker, headed "Which AI reads a
     * plan · testing", with a chip per model and a paragraph about second opinions. Every shipped
     * build has that key, so every customer sees that section and no golden has ever contained it:
     * not at 320 dp, not at 200 % font, not dark, not right-to-left, and the accessibility pass has
     * never seen it either. A section that ships to everyone and is drawn for nobody is exactly what
     * this harness exists to catch.
     */
    @Test
    fun settings_with_reader_choice() {
        val content: @Composable () -> Unit = {
            SettingsContent(
                onLegal = {}, onBack = {}, onDeleteAll = {},
                readerOptions = listOf("openai/gpt-5.6-luna", "google/gemini-3.1-pro-preview"),
                chosenReader = null,
            )
        }
        captureAcrossMatrix("settings-reader", content)
        writeManifestAcrossMatrix("settings-reader", content)
    }
}
