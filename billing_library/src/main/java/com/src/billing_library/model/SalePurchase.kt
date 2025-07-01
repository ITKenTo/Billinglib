package com.src.billing_library.model

import com.android.billingclient.api.Purchase

data class SalePurchase(val productId: String, val purchaseToken: String) {

    companion object {
        fun Purchase.toSalePurchase(): SalePurchase {
            return SalePurchase(products.firstOrNull() ?: "", purchaseToken)
        }
    }

}