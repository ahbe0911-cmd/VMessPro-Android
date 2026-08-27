package com.vmesspro.android.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface

internal object PhysicalNetworkMonitor {
    @Volatile
    var currentNetwork: Network? = null
        private set

    private var connectivityManager: ConnectivityManager? = null
    private var callbackRegistered = false
    private var listener: InterfaceUpdateListener? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isVpn(network)) return
            currentNetwork = network
            notifyListener(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (currentNetwork == network && !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                notifyListener(network)
            }
        }

        override fun onLost(network: Network) {
            if (currentNetwork == network) {
                currentNetwork = null
                listener?.updateDefaultInterface("", -1, false, false)
            }
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (callbackRegistered) return
        val cm = context.getSystemService(ConnectivityManager::class.java)
        connectivityManager = cm
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        if (Build.VERSION.SDK_INT >= 31) {
            cm.registerBestMatchingNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        } else {
            cm.requestNetwork(request, callback)
        }
        callbackRegistered = true

        cm.allNetworks.firstOrNull { !isVpn(it) && hasInternet(it) }?.let {
            currentNetwork = it
            notifyListener(it)
        }
    }

    @Synchronized
    fun stop() {
        val cm = connectivityManager
        if (callbackRegistered && cm != null) {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
        callbackRegistered = false
        currentNetwork = null
        listener = null
        connectivityManager = null
    }

    fun setListener(value: InterfaceUpdateListener?) {
        listener = value
        val network = currentNetwork
        if (value != null && network != null) notifyListener(network)
    }

    private fun notifyListener(network: Network) {
        val cm = connectivityManager ?: return
        val lp = cm.getLinkProperties(network) ?: return
        val interfaceName = lp.interfaceName ?: return
        val networkInterface = runCatching { NetworkInterface.getByName(interfaceName) }.getOrNull() ?: return
        val caps = cm.getNetworkCapabilities(network)
        val expensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        listener?.updateDefaultInterface(interfaceName, networkInterface.index, expensive, false)
    }

    private fun isVpn(network: Network): Boolean = connectivityManager
        ?.getNetworkCapabilities(network)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    private fun hasInternet(network: Network): Boolean = connectivityManager
        ?.getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
