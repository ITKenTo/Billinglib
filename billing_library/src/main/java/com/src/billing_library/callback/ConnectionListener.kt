package com.src.billing_library.callback

interface ConnectionListener {

    fun onConnectionSuccess()

    fun onDisconnected()

    fun onConnectionFailure()

}