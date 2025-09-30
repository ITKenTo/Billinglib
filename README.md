# BillingLib

A lightweight and modular Android library for integrating Google Play Billing into your apps with minimal effort.

[![JitPack](https://jitpack.io/v/ITKenTo/BillingLib.svg)](https://jitpack.io/#ITKenTo/BillingLib)

---

## 🚀 Features

- ✅ Simple setup with BillingClient
- 🛒 One-time purchases and subscriptions
- 🔄 Automatic connection management
- 🎯 Kotlin-first, AndroidX compatible
- 🧩 Easily extendable for custom flows

---

## 📦 Installation

### 1. Add JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}


dependencies {
    implementation("com.github.ITKenTo:BillingLib:lastVersion")
}
```
🛠 Usage
Initialize Billing
```kotlin
val billingLibrary = GooglePlayBillingLibrary(context)
billingLibrary.startConnection(object : ConnectionListener {
    override fun onConnectionSuccess() {
        // Ready to query products or purchases
    }
    override fun onConnectionFailure() { }
    override fun onDisconnected() { }
})
```
Query Products
```kotlin
billingLibrary.queryProductDetails(
    subs = listOf("premium_subscription"),
    inApp = listOf("lifetime_access")
) { productDetails ->
    // Handle product details
}
```
Launch Purchase
```kotlin
billingLibrary.purchase(activity, saleProductDetail)
```
Handle Purchases
```kotlin
billingLibrary.addListener(object : OnPurchasesUpdatedListener {
    override fun onSucceeded(salePurchase: SalePurchase) {
        // Handle purchase
    }
    override fun onFailed(responseCode: PurchaseResponseCode) { }
})
```
👨‍💻 Author

Created by KentoDev – 2025

MIT License

Copyright (c) 2025 ITKenTo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights  
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell  
copies of the Software, and to permit persons to whom the Software is  
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in  
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR  
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,  
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE  
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER  
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,  
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN  
THE SOFTWARE.

