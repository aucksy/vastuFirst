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
    val owned: Boolean = false,
    val busy: Boolean = false,
)

/**
 * The one product. A single non-consumable unlock, not a subscription — the owner's decision is a
 * one-time ₹699 for the full report, and a subscription would be a different promise entirely.
 */
const val REPORT_PRODUCT_ID = "vastufirst_full_report"

/** The price shown when the store has not answered (or is switched off). Never invented — it is the
 *  owner's decided price, and the store's own string replaces it the moment one arrives. */
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
    else -> "Pay ${state.price ?: FALLBACK_PRICE} and unlock"
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
class NoBilling : Billing {
    override val state = BillingState(mode = BillingMode.DISABLED, price = null)
    override suspend fun purchase(): PurchaseResult = PurchaseResult.Purchased
    override suspend fun restore(): PurchaseResult = PurchaseResult.AlreadyOwned
}
