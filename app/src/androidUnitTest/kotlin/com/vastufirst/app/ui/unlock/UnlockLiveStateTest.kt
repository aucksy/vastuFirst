package com.vastufirst.app.ui.unlock

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.billing.PENDING_PAYMENT_MESSAGE
import com.vastufirst.app.billing.PurchaseResult
import com.vastufirst.designsystem.theme.VastuTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐ THE UNLOCK SCREEN FOLLOWS THE STORE, LIVE (14 Oct 2026). Payments are still switched off, so no
 * buyer has met these, but each was found by reading the code and each would have met the first one:
 *
 *  1. The screen copied the billing state ONCE, when it opened. Google Play answers a moment after the
 *     app starts, so the screen kept "can't reach Google Play" and "Try again" for the whole visit.
 *  2. A payment the store has not confirmed yet (cash at a shop counter) came back as "cancelled", and
 *     the screen said nothing at all — as if the tap had done nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class UnlockLiveStateTest {

    private class FakeStore(start: BillingState, private val answer: PurchaseResult) : Billing {
        val flow = MutableStateFlow(start)
        override val state: BillingState get() = flow.value
        override val states: StateFlow<BillingState> = flow
        override suspend fun purchase(): PurchaseResult = answer
        override suspend fun restore(): PurchaseResult = PurchaseResult.Cancelled
    }

    @Test fun the_store_answering_after_the_screen_opens_reaches_the_screen() = runComposeUiTest {
        val store = FakeStore(BillingState(mode = BillingMode.UNAVAILABLE), PurchaseResult.Cancelled)
        setContent { VastuTheme { UnlockScreen(onUnlocked = {}, billing = store) } }
        onNodeWithText("Try again").performScrollTo().assertExists()

        // Google Play answers: reachable, with its own price.
        store.flow.value = BillingState(mode = BillingMode.READY, price = "₹699.00")
        waitForIdle()
        onNodeWithText("Pay ₹699.00 and unlock").performScrollTo().assertExists()

        // And an account that already owns the report is told so, without leaving the screen.
        store.flow.value = BillingState(mode = BillingMode.READY, price = "₹699.00", owned = true)
        waitForIdle()
        onNodeWithText("See the full report").performScrollTo().assertExists()
    }

    @Test fun a_payment_waiting_for_the_store_is_said_not_swallowed() = runComposeUiTest {
        val store = FakeStore(BillingState(mode = BillingMode.READY, price = "₹699.00"), PurchaseResult.Pending)
        var unlocked = false
        setContent { VastuTheme { UnlockScreen(onUnlocked = { unlocked = true }, billing = store) } }
        onNodeWithText("Pay ₹699.00 and unlock").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(PENDING_PAYMENT_MESSAGE).performScrollTo().assertExists()
        kotlin.test.assertFalse(unlocked, "a payment that is only waiting must not unlock the report")
    }
}
