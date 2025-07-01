package com.src.billing_library.callback

import com.src.billing_library.model.SalePurchase

interface OnPurchasesUpdatedListener {

    fun onSucceeded(purchase: SalePurchase)

    fun onFailed(responseCode: Int)

}