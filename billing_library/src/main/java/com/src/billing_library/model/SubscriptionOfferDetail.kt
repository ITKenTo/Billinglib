package com.src.billing_library.model

import com.android.billingclient.api.ProductDetails
import com.src.billing_library.model.PricingPhase.Companion.toPricingPhases

data class SubscriptionOfferDetail(
    val offerId: String?,
    val offerToken: String,
    val offerTags: List<String>,
    val pricingPhases: List<PricingPhase>
) {
    companion object {

        private fun ProductDetails.SubscriptionOfferDetails.toSubscriptionOfferDetail(): SubscriptionOfferDetail {
            return SubscriptionOfferDetail(
                offerId = this.offerId,
                offerToken = this.offerToken,
                offerTags = this.offerTags,
                pricingPhases = this.pricingPhases.toPricingPhases()
            )
        }

        fun List<ProductDetails.SubscriptionOfferDetails>.toSubscriptionOfferDetails(): List<SubscriptionOfferDetail> {
            return this.map { it.toSubscriptionOfferDetail() }
        }

    }

}