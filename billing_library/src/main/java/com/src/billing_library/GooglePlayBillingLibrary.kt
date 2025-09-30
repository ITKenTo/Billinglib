package com.src.billing_library

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.src.billing_library.callback.ConnectionListener
import com.src.billing_library.callback.OnPurchasesUpdatedListener
import com.src.billing_library.model.PurchaseResponseCode
import com.src.billing_library.model.SaleProductDetail
import com.src.billing_library.model.SaleProductDetail.Companion.toSaleProductDetail
import com.src.billing_library.model.SalePurchase
import com.src.billing_library.model.SalePurchase.Companion.toSalePurchase
import com.src.billing_library.model.SubscriptionOfferDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * GooglePlayBillingLibrary.kt
 *
 * Created by KentoDev in 2025
 *
 * A wrapper around Google Play BillingClient that:
 * - Manages the billing service connection lifecycle
 * - Queries product details (in-app products & subscriptions)
 * - Queries already owned purchases
 * - Handles purchase flows and purchase updates
 * - Acknowledges or consumes completed transactions
 *
 * All public APIs are coroutine-based (suspend functions) → non-blocking and safer for UI.
 *
 * Logging guideline:
 * - Log.d(TAG, "...") for state changes (connection, queries)
 * - Log.i(TAG, "...") for successful operations
 * - Log.w(TAG, "...") for recoverable issues
 * - Log.e(TAG, "...") for errors or unexpected behavior
 *
 * Note: Never log sensitive data such as purchaseToken in production builds.
 */
class GooglePlayBillingLibrary(context: Context) : BillingLibrary {
    companion object {
        private const val TAG = "GooglePlayBilling"
    }

    /**
     * K is purchaseToken
     * V is Purchase
     * **/
    private val purchases = ConcurrentHashMap<String, Purchase>()

    /**
     * K is productId
     * V is ProductDetails
     * **/
    private val detailProducts = ConcurrentHashMap<String, ProductDetails>()

    private val listeners = CopyOnWriteArrayList<OnPurchasesUpdatedListener>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isConnected = false

    private val purchasesListener = PurchasesUpdatedListener { billingResult, purchases ->
        handlePurchasesUpdate(billingResult, purchases)
    }

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

    private fun handlePurchasesUpdate(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d(
            TAG, "onPurchasesUpdated: responseCode=${billingResult.responseCode}, " +
                    "debugMessage=${billingResult.debugMessage}"
        )

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    Log.w(TAG, "Purchase success but list is empty")
                    notifyListeners { it.onFailed(PurchaseResponseCode.ERROR) }
                    return
                }

                purchases.forEach { purchase ->
                    this.purchases[purchase.purchaseToken] = purchase
                    Log.d(
                        TAG, "Purchase successful: ${purchase.products}, " +
                                "token=${purchase.purchaseToken}, " +
                                "state=${purchase.purchaseState}"
                    )
                }

                notifyListeners { listener ->
                    purchases.forEach { purchase ->
                        listener.onSucceeded(purchase.toSalePurchase())
                    }
                }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.w(TAG, "Item already owned")
                notifyListeners { it.onFailed(PurchaseResponseCode.ITEM_ALREADY_OWNED) }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                notifyListeners { it.onFailed(PurchaseResponseCode.USER_CANCELED) }
            }

            else -> {
                Log.e(
                    TAG,
                    "Purchase failed: ${billingResult.responseCode} - ${billingResult.debugMessage}"
                )
                notifyListeners { it.onFailed(PurchaseResponseCode.ERROR) }
            }
        }
    }

    private inline fun notifyListeners(action: (OnPurchasesUpdatedListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }

    /**
     * Establishes a connection to Google Play Billing.
     *
     * Should be called early (e.g., Application.onCreate or when billing is needed).
     *
     * @return Result<Unit>
     *   - Success → connection established
     *   - Failure → connection failed, check logs for BillingResponseCode
     */
    override fun startConnection(listener: ConnectionListener) {
        if (billingClient.isReady) {
            Log.d(TAG, "BillingClient already connected")
            listener.onConnectionSuccess()
            return
        }


        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                isConnected = false
                listener.onDisconnected()
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(
                    TAG,
                    "Billing setup finished: ${billingResult.responseCode} - ${billingResult.debugMessage}"
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    listener.onConnectionSuccess()
                } else {
                    isConnected = false
                    listener.onConnectionFailure()
                }
            }
        })
    }

    /**
     * Queries product details from the Play Console.
     *
     * @param products A list of products to query (in-app products or subscriptions).
     * @return List<ProductDetails> containing price, currency, sale info, subscription offers, etc.
     *
     * Notes:
     * - Results are cached internally in [productMap]
     * - Safe to call multiple times, as the latest data will overwrite cache
     * - Always call [startConnection] before using this
     */
    override fun queryProductDetails(
        subs: List<String>,
        inApp: List<String>,
        onCompleted: (List<SaleProductDetail>) -> Unit
    ) {
        if (!billingClient.isReady) {
            Log.e(TAG, "BillingClient not ready")
            onCompleted(emptyList())
            return
        }

        scope.launch {
            try {
                val subsDetails = if (subs.isNotEmpty()) {
                    queryProductDetailsAsync(subs, BillingClient.ProductType.SUBS)
                } else {
                    emptyList()
                }

                val inAppDetails = if (inApp.isNotEmpty()) {
                    queryProductDetailsAsync(inApp, BillingClient.ProductType.INAPP)
                } else {
                    emptyList()
                }

                val allProducts = subsDetails + inAppDetails

                val saleProductDetails = allProducts.map { productDetail ->
                    detailProducts[productDetail.productId] = productDetail
                    productDetail.toSaleProductDetail()
                }

                Log.d(TAG, "Query completed: ${saleProductDetails.size} products found")
                saleProductDetails.forEach {
                    Log.d(TAG, "Product: $it")
                }

                withContext(Dispatchers.Main) {
                    onCompleted(saleProductDetails)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying product details", e)
                withContext(Dispatchers.Main) {
                    onCompleted(emptyList())
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun queryProductDetailsAsync(
        productIds: List<String>,
        productType: String
    ): List<ProductDetails> = suspendCancellableCoroutine { continuation ->
        val params = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(params)
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                continuation.resume(productDetailsList.productDetailsList) {}
            } else {
                Log.e(TAG, "Query $productType failed: ${billingResult.debugMessage}")
                continuation.resume(emptyList()) {}
            }
        }
    }

    /**
     * Queries all active purchases from Google Play.
     *
     * @return List<Purchase> including both INAPP and SUBS purchases
     *
     * Notes:
     * - Purchases are cached internally in [purchaseMap]
     * - Use this to restore purchases when a user reinstalls or changes device
     */
    override fun queryPurchases(
        onCompleted: (List<SalePurchase>) -> Unit
    ) {
        if (!billingClient.isReady) {
            Log.e(TAG, "BillingClient not ready")
            onCompleted(emptyList())
            return
        }

        scope.launch {
            try {
                val subsPurchases = queryPurchasesAsync(BillingClient.ProductType.SUBS)
                val inAppPurchases = queryPurchasesAsync(BillingClient.ProductType.INAPP)

                val allPurchases = subsPurchases + inAppPurchases

                val salePurchases = allPurchases.map { purchase ->
                    purchases[purchase.purchaseToken] = purchase
                    purchase.toSalePurchase()
                }

                Log.d(TAG, "Query purchases completed: ${salePurchases.size} purchases found")

                withContext(Dispatchers.Main) {
                    onCompleted(salePurchases)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying purchases", e)
                withContext(Dispatchers.Main) {
                    onCompleted(emptyList())
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun queryPurchasesAsync(productType: String): List<Purchase> =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchasesList) {}
                } else {
                    Log.e(TAG, "Query $productType purchases failed: ${billingResult.debugMessage}")
                    continuation.resume(emptyList()) {}
                }
            }
        }

    override fun addListener(listener: OnPurchasesUpdatedListener) {
        listeners.add(listener)
        Log.d(TAG, "Listener added, total: ${listeners.size}")
    }

    override fun removeListener(listener: OnPurchasesUpdatedListener) {
        listeners.remove(listener)
        Log.d(TAG, "Listener removed, total: ${listeners.size}")
    }

    /**
     * Acknowledges or consumes a purchase after it has been successfully completed.
     *
     * @param purchase The [Purchase] to acknowledge or consume
     *
     * Notes:
     * - Consumables → consumed so they can be purchased again
     * - Non-consumables / subscriptions → acknowledged once, then permanently owned
     * - Must be called within 3 days of the purchase, otherwise Play will auto-refund
     */
    override fun onTransactionCompleted(salePurchase: SalePurchase, isConsume: Boolean) {
        val purchase = purchases[salePurchase.purchaseToken] ?: return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase, isConsume)
                } else if (isConsume) {
                    consumePurchase(purchase.purchaseToken)
                }
            }

            Purchase.PurchaseState.PENDING -> {
                Log.d(TAG, "Purchase pending: ${purchase.purchaseToken}")
            }

            Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                Log.w(TAG, "Purchase state unspecified: ${purchase.purchaseToken}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase, shouldConsume: Boolean) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged: ${purchase.purchaseToken}")
                if (shouldConsume) {
                    consumePurchase(purchase.purchaseToken)
                }
            } else {
                Log.e(TAG, "Acknowledge failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun consumePurchase(purchaseToken: String) {
        val consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase consumed: $purchaseToken")
                purchases.remove(purchaseToken)
            } else {
                Log.e(TAG, "Consume failed: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launches a billing flow for the given product.
     *
     * @param activity The Activity to use for launching the purchase UI
     * @param productId The product ID defined in Play Console
     *
     * Notes:
     * - If the product has multiple offers (e.g., subscription base plan + offer), use
     *   the overload that accepts [SubscriptionOfferDetails]
     * - Purchase results are received in [onPurchasesUpdated] and forwarded to listeners
     */
    override fun purchase(activity: Activity, saleProductDetail: SaleProductDetail) {
        val productDetail: ProductDetails = detailProducts[saleProductDetail.productId] ?: return

        val productDetailsParamsList = when {
            // Subscription product
            !saleProductDetail.subscriptionOfferDetails.isNullOrEmpty() -> {
                val offerToken = saleProductDetail.subscriptionOfferDetails.first().offerToken
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetail)
                        .setOfferToken(offerToken)
                        .build()
                )
            }
            // One-time purchase product
            saleProductDetail.oneTimePurchaseOfferDetails != null -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetail)
                        .build()
                )
            }

            else -> {
                Log.e(TAG, "No valid offer details found")
                return
            }
        }
        launchBillingFlow(activity, productDetailsParamsList)
    }

    override fun purchase(
        activity: Activity,
        saleProductDetail: SaleProductDetail,
        subscriptionOfferDetail: SubscriptionOfferDetail
    ) {
        val productDetail: ProductDetails = detailProducts[saleProductDetail.productId] ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetail)
                .setOfferToken(subscriptionOfferDetail.offerToken)
                .build()
        )
        launchBillingFlow(activity, productDetailsParamsList)
    }

    private fun launchBillingFlow(
        activity: Activity,
        productDetailsParamsList: List<BillingFlowParams.ProductDetailsParams>
    ) {
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Launch billing flow failed: ${billingResult.debugMessage}")
        }
    }

    /**
     * Ends the connection to Google Play Billing.
     *
     * Should be called when billing is no longer needed (e.g., on app shutdown).
     * Also clears all cached listeners and products to prevent memory leaks.
     */
    override fun endConnection() {
        billingClient.endConnection()
    }

}