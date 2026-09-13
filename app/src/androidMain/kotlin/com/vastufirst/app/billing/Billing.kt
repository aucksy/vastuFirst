package com.vastufirst.app.billing

/**
 * ⭐ TAKING MONEY FOR THE ₹699 REPORT — built in full, shipped switched OFF.
 *
 * ⚠ THE PRODUCT DECISION THAT CHANGED THE PLAN, recorded here because it is not obvious and it is
 * expensive to get wrong: the plan said Razorpay. **Google Play does not allow that for this app.**
 * Anything sold inside a Play Store app that the customer then consumes inside the app must be sold
 * through Google Play's own billing; a third-party gateway for digital content gets the app removed.
 * So the checkout is built on Play Billing. The upside for the owner is that there is nothing secret
 * to paste into the app at all — no key, no token — which is also why nothing here reads a secret.
 * Razorpay stays the right answer only if the report is ever sold on a web page instead.
 *
 * ⚠⚠ AND THE HARD RULE ON TOP OF IT: **the app must never show a screen that LOOKS like it takes
 * payment when it does not.** With the flag off, the unlock screen says in plain words that no
 * payment is taken and the report simply unlocks on the device. That is the whole reason this is an
 * interface with two implementations rather than a boolean sprinkled through a screen — the "off"
 * path is a real, honest thing, not a disabled button.
 *
 * Everything decision-shaped lives here as pure Kotlin so it can be tested without a store, a device
 * or an account. The Play SDK is touched only by PlayBilling.
 */

/** What the unlock screen is currently able to do. */
enum class BillingMode {
    /** Payments are switched off. The report unlocks locally and the screen SAYS so. */
    DISABLED,

    /** Payments are on and the store is reachable. */
    READY,

    /** Payments are on but the store could not be reached — never silently fall back to free. */
    UNAVAILABLE,
}

/** The result of asking the store to buy the report. */
sealed interface PurchaseResult {
    /** Paid and verified by Google. The report unlocks. */
    data object Purchased : PurchaseResult

    /** The user backed out. Not an error, and must not be reported as one. */
    data object Cancelled : PurchaseResult

    /** Already bought — restoring rather than charging again. */
    data object AlreadyOwned : PurchaseResult

    /** Anything else. [message] is already in plain words fit to show a person. */
    data class Failed(val message: String) : PurchaseResult
}

/** What the unlock screen needs to know, in one value. */
data class BillingState(
    val mode: BillingMode = BillingMode.DISABLED,
    /** The store's own localised price string ("₹699.00"). Null until the store answers. */
    val price: String? = null,
    /**
     * ⭐ The price published from the Control Room, or null.
     *
     * THREE PRICES, AND THE ORDER BETWEEN THEM IS THE WHOLE POINT:
     *
     *   1. [price] — Google's own, and the only one that is ever CHARGED. It wins whenever there
     *      is one, because it is the number the customer's card will actually be debited.
     *   2. this one — what we published from the Control Room. What the app SAYS while payments
     *      are switched off, and what it shows in the moment before Google answers.
     *   3. [FALLBACK_PRICE] — built into this build, never deleted, and what a phone with no signal
     *      has always shown.
     *
     * Getting that order the other way round would put OUR number on a screen where Google was
     * about to charge a different one, which is the one mistake this whole feature must never make.
     */
    val publishedPrice: String? = null,
    val owned: Boolean = false,
    val busy: Boolean = false,
) {
    /** The price to put on the screen: the store's, then ours, then the one built in. */
    val shownPrice: String get() = price ?: publishedPrice ?: FALLBACK_PRICE
}

/**
 * The one product. A single non-consumable unlock, not a subscription — the owner's decision is a
 * one-time ₹699 for the full report, and a subscription would be a different promise entirely.
 */
const val REPORT_PRODUCT_ID = "vastufirst_full_report"

/**
 * The price built into this build, shown when nothing else can be. Never invented — it is the
 * owner's decided price.
 *
 * ⚠ THIS CONSTANT IS NEVER DELETED AND NEVER STOPS BEING THE LAST RESORT. A published price can
 * replace what is SHOWN, but a phone that has never reached the Control Room, or that refused what
 * it was sent, still has to name a price rather than a blank — and this is it. A test pins it.
 */
const val FALLBACK_PRICE = "₹699"

/**
 * The plain sentence under the unlock button. It is a function, not four strings scattered through
 * the screen, because getting it wrong is the one thing in this whole feature that could mislead
 * somebody about money — so it is pinned by tests.
 */
fun billingNotice(state: BillingState): String = when {
    state.owned -> "You already have this report. Nothing further will be charged."
    state.mode == BillingMode.DISABLED ->
        "No payment is taken in this version — the report unlocks on this device, free. " +
            "Paid checkout arrives in a later update."
    state.mode == BillingMode.UNAVAILABLE ->
        "We can't reach Google Play right now, so we can't take a payment. " +
            "Check your connection and try again — nothing has been charged."
    else -> "One payment through Google Play. No subscription, and nothing renews."
}

/** The unlock button's own words, which must never promise a charge that will not happen. */
fun billingActionLabel(state: BillingState): String = when {
    state.owned -> "See the full report"
    state.mode == BillingMode.DISABLED -> "Unlock on this device — free"
    state.mode == BillingMode.UNAVAILABLE -> "Try again"
    else -> "Pay ${state.shownPrice} and unlock"
}

/**
 * The store, behind an interface so the screen can be rendered and tested without one. The
 * flag-off implementation is [NoBilling]; the real one is PlayBilling.
 */
interface Billing {
    val state: BillingState

    /** Start the purchase. Returns what happened, in terms the screen can show a person. */
    suspend fun purchase(): PurchaseResult

    /** Re-check what this account already owns — the "restore purchases" path a store requires. */
    suspend fun restore(): PurchaseResult
}

/**
 * Payments switched off. It unlocks the report locally and reports exactly that — it does NOT
 * pretend to charge, and the screen's own words come from [billingNotice], which reads this mode.
 */
class NoBilling(publishedPrice: String? = null) : Billing {
    override val state = BillingState(
        mode = BillingMode.DISABLED,
        // Never a price from a store, because there is no store in this mode.
        price = null,
        publishedPrice = publishedPrice,
    )
    override suspend fun purchase(): PurchaseResult = PurchaseResult.Purchased
    override suspend fun restore(): PurchaseResult = PurchaseResult.AlreadyOwned
}
