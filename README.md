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

