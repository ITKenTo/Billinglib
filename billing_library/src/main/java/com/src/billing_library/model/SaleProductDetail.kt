package com.src.billing_library.model

import com.android.billingclient.api.ProductDetails
import com.src.billing_library.model.OneTimePurchaseOfferDetails.Companion.toOneTimePurchaseOfferDetails
import com.src.billing_library.model.SubscriptionOfferDetail.Companion.toSubscriptionOfferDetails

data class SaleProductDetail(
    val productId: String,
    val name: String,
    val description: String,
    val oneTimePurchaseOfferDetails: OneTimePurchaseOfferDetails?,
    val subscriptionOfferDetails: List<SubscriptionOfferDetail>?
) {

    companion object {
        fun ProductDetails.toSaleProductDetail(): SaleProductDetail {
            return SaleProductDetail(
                productId = this.productId,
                name = this.name,
                description = this.description,
                oneTimePurchaseOfferDetails = this.oneTimePurchaseOfferDetails?.toOneTimePurchaseOfferDetails(),
                subscriptionOfferDetails = this.subscriptionOfferDetails?.toSubscriptionOfferDetails()
            )
        }
    }

    fun getSubscriptionOfferDetail(offerId: String?): SubscriptionOfferDetail? {
        return subscriptionOfferDetails?.find { it.offerId == offerId }
            ?: subscriptionOfferDetails?.find { it.offerId == null }
    }

}