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
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.src.billing_library.callback.ConnectionListener
import com.src.billing_library.callback.OnPurchasesUpdatedListener
import com.src.billing_library.model.PurchaseResponseCode
import com.src.billing_library.model.SaleProductDetail
import com.src.billing_library.model.SaleProductDetail.Companion.toSaleProductDetail
import com.src.billing_library.model.SalePurchase
import com.src.billing_library.model.SalePurchase.Companion.toSalePurchase
import com.src.billing_library.model.SubscriptionOfferDetail
import java.util.concurrent.CountDownLatch

class GooglePlayBillingLibrary(context: Context) : BillingLibrary {

    /**
     * K is purchaseToken
     * V is Purchase
     * **/
    private val purchases = HashMap<String, Purchase>()

    /**
     * K is productId
     * V is ProductDetails
     * **/
    private val detailProducts = HashMap<String, ProductDetails>()

    private val listeners = ArrayList<OnPurchasesUpdatedListener>()

    private val purchasesListener = PurchasesUpdatedListener { billingResult, purchases ->
        when {
            billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                purchases.forEach { purchase ->
                    this.purchases[purchase.purchaseToken] = purchase
                    listeners.forEach { ls ->
                        ls.onSucceeded(purchase.toSalePurchase())
                    }
                }
            }

            billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                listeners.forEach { ls ->
                    ls.onFailed(PurchaseResponseCode.ITEM_ALREADY_OWNED)
                }
            }

            billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                listeners.forEach { ls ->
                    ls.onFailed(PurchaseResponseCode.USER_CANCELED)
                }
            }

            else -> {
                listeners.forEach { ls ->
                    ls.onFailed(PurchaseResponseCode.ERROR)
                }
            }
        }
    }

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context).setListener(purchasesListener).enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        ).build()

    override fun startConnection(listener: ConnectionListener) {
        Thread {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    Log.d("log_debugs", "GooglePlayBillingLibrary_onBillingServiceDisconnected: ")
                    listener.onDisconnected()
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    Log.d(
                        "log_debugs",
                        "GooglePlayBillingLibrary_onBillingSetupFinished: ${billingResult.responseCode}"
                    )
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        listener.onConnectionSuccess()
                    } else {
                        listener.onConnectionFailure()
                    }
                }
            })
        }.start()
    }

    override fun queryProductDetails(
        subs: List<String>, inApp: List<String>, onCompleted: (List<SaleProductDetail>) -> Unit
    ) {
        var subsProductDetails: List<ProductDetails>? = null
        var inAppProductDetails: List<ProductDetails>? = null
        val latch = CountDownLatch(2) // Wait for 2 async tasks

        // Query subscription products

        if (subs.isEmpty()) {
            latch.countDown() // Decrement the latch count
        } else {
            Thread {
                val params = subs.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS).build()
                }
                val builder = QueryProductDetailsParams.newBuilder().setProductList(params).build()
                billingClient.queryProductDetailsAsync(builder) { _, productDetails ->
                    subsProductDetails = ArrayList(productDetails.productDetailsList)

                    latch.countDown() // Decrement the latch count
                }
            }.start()
        }

        if (inApp.isEmpty()) {
            latch.countDown() // Decrement the latch count
        } else {
            // Query in-app products
            Thread {
                val params = inApp.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP).build()
                }
                val builder = QueryProductDetailsParams.newBuilder().setProductList(params).build()
                billingClient.queryProductDetailsAsync(builder) { _, productDetails ->
                    inAppProductDetails = ArrayList(productDetails.productDetailsList)
                    latch.countDown() // Decrement the latch count
                }
            }.start()
        }


        // Wait for both queries to complete
        latch.await()

        // Combine the results
        val allProducts = ArrayList<ProductDetails>()
        subsProductDetails?.let { list ->
            allProducts.addAll(list)
        }
        inAppProductDetails?.let { list ->
            allProducts.addAll(list)
        }

        // Convert to SaleProductDetail
        val productDetails = ArrayList<SaleProductDetail>()
        allProducts.forEach {
            detailProducts[it.productId] = it
            productDetails.add(it.toSaleProductDetail())
        }

        productDetails.forEach {
            Log.d("log_debugs", "GooglePlayBillingLibrary_queryProductDetails: ${it}")
        }
        // Response productDetails
        onCompleted(productDetails)
    }

    override fun queryPurchases(
        onCompleted: (List<SalePurchase>) -> Unit
    ) {
        val latch = CountDownLatch(2) // Wait for 2 async tasks
        var subs: List<Purchase>? = null
        var inApp: List<Purchase>? = null

        // Query subscription purchases
        Thread {
            val params =
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
            billingClient.queryPurchasesAsync(params.build()) { _, purchases ->
                subs = ArrayList(purchases)
                latch.countDown()
            }
        }.start()

        // Query in-app purchases
        Thread {
            val params =
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP)
            billingClient.queryPurchasesAsync(params.build()) { _, purchases ->
                inApp = ArrayList(purchases)
                latch.countDown()
            }
        }.start()

        // Wait for both queries to complete
        latch.await()

        // Combine the results
        val allPurchases = ArrayList<Purchase>()
        subs?.let { list ->
            allPurchases.addAll(list)
        }
        inApp?.let { list ->
            allPurchases.addAll(list)
        }

        // Convert to SalePurchase
        val salePurchases = ArrayList<SalePurchase>()
        allPurchases.forEach {
            purchases[it.purchaseToken] = it
            salePurchases.add(it.toSalePurchase())
        }

        Log.d("log_debugs", "GooglePlayBillingLibrary_queryPurchases: ${salePurchases.size}")
        // Response salePurchases
        onCompleted(salePurchases)
    }

    override fun addListener(listener: OnPurchasesUpdatedListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: OnPurchasesUpdatedListener) {
        listeners.remove(listener)
    }

    override fun onTransactionCompleted(salePurchase: SalePurchase, isConsume: Boolean) {
        val purchase = purchases[salePurchase.purchaseToken] ?: return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                billingClient.acknowledgePurchase(acknowledgePurchaseParams.build()) { billingResult ->
                    if (isConsume && billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        consumePurchase(purchase.purchaseToken)
                    }
                }
            }
        }
    }

    private fun consumePurchase(purchaseToken: String) {
        val consumeParams =
            ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        billingClient.consumeAsync(consumeParams) { _, _ -> }
    }

    override fun purchase(activity: Activity, saleProductDetail: SaleProductDetail) {
        val productDetail: ProductDetails = detailProducts[saleProductDetail.productId] ?: return

        val productDetailsParamsList = when {
            // Nếu là gói Subscription
            saleProductDetail.subscriptionOfferDetails?.isNotEmpty() == true -> {
                saleProductDetail.subscriptionOfferDetails.first().let {
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetail)
                            .setOfferToken(it.offerToken)
                            .build()
                    )
                }
            }
            // Nếu là gói Lifetime (One-time Purchase)
            saleProductDetail.oneTimePurchaseOfferDetails != null -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetail)
                        .build()
                )
            }
            else -> null
        }

        if (productDetailsParamsList == null) {
            Log.e("Billing", "Không tìm thấy thông tin sản phẩm")
            return
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
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
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun endConnection() {
        billingClient.endConnection()
    }

    private fun BillingResult.isResponseOk(): Boolean =
        responseCode == BillingClient.BillingResponseCode.OK
}