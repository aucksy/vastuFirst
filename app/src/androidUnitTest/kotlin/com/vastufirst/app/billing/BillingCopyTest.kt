package com.vastufirst.app.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐ THE WORDS ABOUT MONEY.
 *
 * This is the only feature in VastuFirst where getting a sentence wrong could actually mislead
 * somebody about being charged. The owner's rule is explicit and absolute: **never ship a screen
 * that looks like it takes payment but does not.** Payments are built in full and shipped switched
 * OFF, which is exactly the situation in which that could happen by accident.
 *
 * So every sentence the unlock screen shows comes from one of two pure functions, and both are
 * pinned here — including the negative cases, which are the ones that matter. A test that only
 * checked the "on" wording would pass happily while the "off" screen promised a charge.
 */
class BillingCopyTest {

    private val off = BillingState(mode = BillingMode.DISABLED)
    private val on = BillingState(mode = BillingMode.READY, price = "₹699.00")
    private val down = BillingState(mode = BillingMode.UNAVAILABLE)
    private val owned = BillingState(mode = BillingMode.READY, price = "₹699.00", owned = true)

    // ── payments OFF: it must say so, and must not imply a charge ─────────────────────────────

    @Test fun with_payments_off_the_notice_says_no_payment_is_taken() {
        val text = billingNotice(off).lowercase()
        assertTrue("the notice must say no payment is taken: '$text'", text.contains("no payment is taken"))
        assertTrue("and that it is free: '$text'", text.contains("free"))
    }

    @Test fun with_payments_off_the_button_never_promises_a_charge() {
        val label = billingActionLabel(off)
        assertFalse("the button must not say 'pay': '$label'", label.lowercase().contains("pay"))
        assertFalse("nor show a price: '$label'", label.contains("₹"))
        assertTrue("it must say the unlock is free: '$label'", label.lowercase().contains("free"))
    }

    // ── payments ON: it must be honest the other way round ────────────────────────────────────

    @Test fun with_payments_on_the_button_shows_the_stores_own_price() {
        assertEquals("Pay ₹699.00 and unlock", billingActionLabel(on))
    }

    @Test fun with_payments_on_the_notice_says_it_is_a_one_off() {
        val text = billingNotice(on).lowercase()
        assertTrue("it must say nothing renews: '$text'", text.contains("nothing renews"))
        assertFalse("and must not still claim it is free: '$text'", text.contains("free"))
    }

    @Test fun the_price_falls_back_to_the_owners_decided_price_never_a_blank() {
        val noPriceYet = BillingState(mode = BillingMode.READY, price = null)
        assertTrue(
            "with no answer from the store the button must still name a price: ${billingActionLabel(noPriceYet)}",
            billingActionLabel(noPriceYet).contains(FALLBACK_PRICE),
        )
        assertEquals("the fallback is the owner's decided price", "₹699", FALLBACK_PRICE)
    }

    // ── a price published from the Control Room ───────────────────────────────────────────────

    /**
     * ⭐⭐ THREE PRICES, AND THE ORDER BETWEEN THEM IS THE ONE THING HERE THAT COULD TAKE SOMEBODY'S
     * MONEY WRONGLY.
     *
     *   1. Google's own, whenever there is one — it is the number actually charged.
     *   2. What we published from the Control Room.
     *   3. The one built into this build, which is never deleted.
     *
     * Getting 1 and 2 the wrong way round would put OUR number in the largest type on a screen
     * where Google was about to charge a different one. That is the failure these pin.
     */
    @Test fun the_stores_own_price_always_beats_the_one_we_published() {
        val both = BillingState(mode = BillingMode.READY, price = "₹699.00", publishedPrice = "₹849")
        assertEquals("₹699.00", both.shownPrice)
        assertEquals("Pay ₹699.00 and unlock", billingActionLabel(both))
    }

    @Test fun a_published_price_shows_while_the_store_has_not_answered() {
        val waiting = BillingState(mode = BillingMode.READY, price = null, publishedPrice = "₹849")
        assertEquals("₹849", waiting.shownPrice)
        assertEquals("Pay ₹849 and unlock", billingActionLabel(waiting))
    }

    @Test fun with_nothing_published_the_price_built_into_the_app_still_shows() {
        val nothing = BillingState(mode = BillingMode.READY, price = null, publishedPrice = null)
        assertEquals(FALLBACK_PRICE, nothing.shownPrice)
    }

    @Test fun a_published_price_never_makes_a_switched_off_screen_look_like_it_charges() {
        // ⚠ The one a published price could genuinely break: a number arrives from the Control Room
        // and the screen starts reading like a live offer while payments are still switched off.
        val off = BillingState(mode = BillingMode.DISABLED, publishedPrice = "₹849")
        val label = billingActionLabel(off)
        assertFalse("the button must not say 'pay': '$label'", label.lowercase().contains("pay"))
        assertFalse("nor show a price: '$label'", label.contains("₹"))
        assertTrue("it must still say the unlock is free: '$label'", label.lowercase().contains("free"))
        assertTrue(billingNotice(off).lowercase().contains("no payment is taken"))
    }

    @Test fun a_published_price_never_survives_having_already_bought_it() {
        val bought = BillingState(mode = BillingMode.READY, publishedPrice = "₹849", owned = true)
        assertEquals("See the full report", billingActionLabel(bought))
        assertTrue(billingNotice(bought).contains("already have this report"))
    }

    @Test fun the_off_implementation_carries_a_published_price_without_inventing_a_store_one() {
        val billing = NoBilling(publishedPrice = "₹849")
        assertEquals("₹849", billing.state.publishedPrice)
        assertEquals("there is no store in this mode, so there is no store price", null, billing.state.price)
        assertEquals(BillingMode.DISABLED, billing.state.mode)
    }

    // ── the store is unreachable: never quietly give it away ──────────────────────────────────

    @Test fun an_unreachable_store_says_nothing_has_been_charged() {
        val text = billingNotice(down).lowercase()
        assertTrue("it must reassure that no money moved: '$text'", text.contains("nothing has been charged"))
        assertFalse(
            "⚠ it must NOT quietly become free — that is a paid product giving itself away: '$text'",
            text.contains("free"),
        )
        assertEquals("and the button must offer a retry, not an unlock", "Try again", billingActionLabel(down))
    }

    // ── already bought ────────────────────────────────────────────────────────────────────────

    @Test fun an_owned_report_promises_no_further_charge() {
        assertEquals("See the full report", billingActionLabel(owned))
        assertTrue(billingNotice(owned).contains("Nothing further will be charged"))
    }

    @Test fun owning_it_wins_over_every_other_state() {
        // A customer who has paid must never be shown a payment prompt again, whatever the store or
        // the flag happens to be doing.
        for (mode in BillingMode.entries) {
            val state = BillingState(mode = mode, owned = true)
            assertEquals("mode $mode", "See the full report", billingActionLabel(state))
            assertTrue("mode $mode", billingNotice(state).contains("already have this report"))
        }
    }

    // ── the switched-off implementation ───────────────────────────────────────────────────────

    @Test fun the_off_implementation_unlocks_locally_and_reports_exactly_that() = kotlinx.coroutines.runBlocking {
        val billing = NoBilling()
        assertEquals(BillingMode.DISABLED, billing.state.mode)
        assertEquals(
            "with payments off, unlocking must succeed — the report is free, not broken",
            PurchaseResult.Purchased, billing.purchase(),
        )
        assertEquals(PurchaseResult.AlreadyOwned, billing.restore())
        assertEquals("and it must never invent a price", null, billing.state.price)
    }

    @Test fun the_product_id_matches_what_the_store_setup_document_tells_the_owner_to_create() {
        // ⚠ If these two ever disagree the store has an item nobody can buy and the app asks for one
        // that does not exist — and the symptom is "the price never loads", days after setup.
        assertEquals("vastufirst_full_report", REPORT_PRODUCT_ID)
    }
}
