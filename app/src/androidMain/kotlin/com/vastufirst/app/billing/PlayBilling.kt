package com.vastufirst.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The real ₹699 checkout, through Google Play. Reached only when payments are switched on.
 *
 * ⚠ NOTHING SECRET LIVES HERE, and that is a property of Play Billing rather than an oversight: the
 * store is identified by the app's own signature and package name, so there is no key to paste and
 * nothing that could leak out of the APK. Switching payments on is a Play Console setting plus a
 * build flag — see docs/PLAY-STORE-SETUP.md.
 *
 * ⚠ A purchase is ACKNOWLEDGED as soon as it is verified. Google automatically REFUNDS and revokes
 * any purchase left unacknowledged for three days — so forgetting this line does not fail loudly, it
 * quietly hands the money back a few days later while the customer keeps the report.
 */
class PlayBilling(
    context: Context,
    private val activityProvider: () -> Activity?,
) : Billing {

    private var current = BillingState(mode = BillingMode.UNAVAILABLE, price = null)
    override val state: BillingState get() = current

    private var details: ProductDetails? = null
    private var pending: CompletableDeferred<PurchaseResult>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val waiting = pending
        pending = null
        when {
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                waiting?.complete(PurchaseResult.Cancelled)
            result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                current = current.copy(owned = true)
                waiting?.complete(PurchaseResult.AlreadyOwned)
            }
            result.responseCode != BillingClient.BillingResponseCode.OK ->
                waiting?.complete(PurchaseResult.Failed(plainMessage(result)))
            else -> {
                val bought = purchases.orEmpty().any { record(it) }
                waiting?.complete(if (bought) PurchaseResult.Purchased else PurchaseResult.Cancelled)
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        // Required from Billing 7 onward; a purchase can be left pending (a customer paying at a
        // shop counter), and the app has to be able to receive it later rather than losing it.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connect, ask the store for the price, and see whether this account already owns the report. */
    suspend fun start() {
        if (!connect()) {
            current = current.copy(mode = BillingMode.UNAVAILABLE)
            return
        }
        current = current.copy(mode = BillingMode.READY)
        loadPrice()
        refreshOwned()
    }

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (client.isReady) {
            if (cont.isActive) cont.resume(true)
            return@suspendCancellableCoroutine
        }
        client.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                // Not resumed here: a disconnect after a successful setup is normal and the client
                // reconnects itself. Resuming twice would crash.
            }
        })
    }

    private suspend fun loadPrice() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REPORT_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val found = suspendCancellableCoroutine<ProductDetails?> { cont ->
            client.queryProductDetailsAsync(params) { _, list ->
                if (cont.isActive) cont.resume(list.firstOrNull())
            }
        }
        details = found
        // ⭐ The STORE'S OWN price string, never one we typed. It carries the customer's currency and
        // any local tax, and it is the number Google will actually charge — a hard-coded "₹699" would
        // be a promise the app is not the one keeping.
        current = current.copy(price = found?.oneTimePurchaseOfferDetails?.formattedPrice)
    }

    private suspend fun refreshOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val owned = suspendCancellableCoroutine<Boolean> { cont ->
            client.queryPurchasesAsync(params) { _, purchases ->
                if (cont.isActive) cont.resume(purchases.any { record(it) })
            }
        }
        current = current.copy(owned = owned)
    }

    override suspend fun purchase(): PurchaseResult {
        if (current.owned) return PurchaseResult.AlreadyOwned
        val product = details ?: run {
            start()
            details
        } ?: return PurchaseResult.Failed(
            "We couldn't reach Google Play to take a payment. Nothing has been charged — please try again.",
        )
        val activity = activityProvider()
            ?: return PurchaseResult.Failed("We couldn't open the payment screen. Nothing has been charged.")

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()

        val waiting = CompletableDeferred<PurchaseResult>()
        pending = waiting
        current = current.copy(busy = true)
        val launch = client.launchBillingFlow(activity, flow)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pending = null
            current = current.copy(busy = false)
            return PurchaseResult.Failed(plainMessage(launch))
        }
        val result = waiting.await()
        current = current.copy(busy = false, owned = current.owned || result is PurchaseResult.Purchased)
        return result
    }

    override suspend fun restore(): PurchaseResult {
        if (!connect()) {
            return PurchaseResult.Failed("We can't reach Google Play right now, so we can't check your purchases.")
        }
        refreshOwned()
        return if (current.owned) PurchaseResult.AlreadyOwned else PurchaseResult.Cancelled
    }

    /** True when this purchase is a real, paid, acknowledged unlock. */
    private fun record(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        if (REPORT_PRODUCT_ID !in purchase.products) return false
        if (!purchase.isAcknowledged) {
            // ⚠ Fire-and-forget is correct HERE and only here: an acknowledgement that fails is retried
            // on the next launch by refreshOwned(), and Google's three-day window is far longer than
            // any plausible gap. Blocking the customer's report on it would be worse.
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { _ -> }
        }
        return true
    }

    /** Google's response codes, in words a person can act on rather than a number. */
    private fun plainMessage(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play couldn't take a payment on this device. Nothing has been charged."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This report isn't on sale yet. Nothing has been charged."
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        ->
            "We couldn't reach Google Play. Check your connection and try again — nothing has been charged."
        else ->
            "The payment didn't go through, and nothing has been charged. Please try again."
    }
}
