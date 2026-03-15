package com.claudescreensaver.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BridgeServer(
    val name: String,
    val host: String,
    val port: Int,
) {
    val sseUrl: String get() = "http://$host:$port/events"
}

class BridgeDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _servers = MutableStateFlow<List<BridgeServer>>(emptyList())
    val servers: StateFlow<List<BridgeServer>> = _servers.asStateFlow()

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "Discovery started for $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
            nsdManager.resolveService(serviceInfo, resolveListener)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            _servers.value = _servers.value.filter { it.name != serviceInfo.serviceName }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery stop failed: $errorCode")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "Resolve failed: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val hostAddr = serviceInfo.host?.hostAddress ?: return
            // Filter out loopback from NSD discovery
            if (hostAddr == "127.0.0.1") return

            val server = BridgeServer(
                name = serviceInfo.serviceName,
                host = hostAddr,
                port = serviceInfo.port,
            )
            Log.d(TAG, "Resolved: $server")
            _servers.value = (_servers.value + server).distinctBy { it.host to it.port }
        }
    }

    fun startDiscovery() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Stop discovery failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BridgeDiscovery"
        private const val SERVICE_TYPE = "_ccrestatus._tcp."
    }
}
