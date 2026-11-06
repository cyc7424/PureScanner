package com.purescanner.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Finds eSCL scanners on the local network with NsdManager (mDNS / DNS-SD).
 * eSCL scanners advertise `_uscan._tcp` (HTTP) and/or `_uscans._tcp` (HTTPS).
 */
class NsdScannerDiscovery(context: Context) {

    private val nsd: NsdManager = context.applicationContext.getSystemService(NsdManager::class.java)

    /** Before API 34 NsdManager can resolve only one service at a time, so resolves are serialised. */
    private val legacyResolveLock = Mutex()

    /**
     * Emits each scanner as it is resolved (possibly more than once; use [mergeDevices] on the collected list).
     * Completes by itself after [timeoutMs]; cancelling the collector stops discovery immediately.
     */
    fun discover(timeoutMs: Long = 8_000): Flow<ScannerDevice> = callbackFlow {
        val scope = this
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        for ((type, https) in SERVICE_TYPES) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Discovery of $serviceType failed to start: $errorCode")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    scope.launch {
                        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(serviceInfo) } ?: return@launch
                        ScannerDevice.fromService(
                            serviceName = resolved.name,
                            addresses = resolved.addresses,
                            port = resolved.port,
                            attributes = resolved.attributes,
                            https = https,
                        )?.let { trySend(it) }
                    }
                }
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners += listener
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Could not start discovery of $type", e)
            }
        }

        scope.launch {
            delay(timeoutMs)
            close()
        }

        awaitClose {
            listeners.forEach {
                try {
                    nsd.stopServiceDiscovery(it)
                } catch (_: IllegalArgumentException) {
                    // already stopped
                }
            }
        }
    }

    // ---------- Resolving ----------

    private class Resolved(
        val name: String,
        val addresses: List<InetAddress>,
        val port: Int,
        val attributes: Map<String, String>,
    )

    private fun NsdServiceInfo.attributesAsText(): Map<String, String> =
        attributes.mapValues { (_, value) -> value?.toString(Charsets.UTF_8).orEmpty() }

    private suspend fun resolve(info: NsdServiceInfo): Resolved? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) resolveModern(info) else resolveLegacy(info)

    /** API 34+: listen for the service's details, take the first update that has an address, then unregister. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveModern(info: NsdServiceInfo): Resolved? = suspendCancellableCoroutine { cont ->
        val unregistered = AtomicBoolean(false)
        lateinit var callback: NsdManager.ServiceInfoCallback

        fun finish(result: Resolved?) {
            if (cont.isActive) cont.resume(result)
            if (unregistered.compareAndSet(false, true)) {
                try {
                    nsd.unregisterServiceInfoCallback(callback)
                } catch (_: IllegalArgumentException) {
                    // never registered or already gone
                }
            }
        }

        callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                unregistered.set(true) // nothing to unregister
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val addresses = serviceInfo.hostAddresses
                if (addresses.isEmpty()) return // wait for a later update that has one
                finish(Resolved(serviceInfo.serviceName, addresses, serviceInfo.port, serviceInfo.attributesAsText()))
            }

            override fun onServiceLost() = finish(null)
            override fun onServiceInfoCallbackUnregistered() {}
        }

        try {
            nsd.registerServiceInfoCallback(info, DIRECT_EXECUTOR, callback)
        } catch (e: IllegalArgumentException) {
            unregistered.set(true)
            if (cont.isActive) cont.resume(null)
        }
        cont.invokeOnCancellation { finish(null) }
    }

    /** API 26–33: resolveService, one at a time. A concurrent call fails with FAILURE_ALREADY_ACTIVE, so retry. */
    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(info: NsdServiceInfo): Resolved? = legacyResolveLock.withLock {
        var result: Resolved? = null
        for (attempt in 1..3) {
            var retry = false
            result = suspendCancellableCoroutine { cont ->
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        retry = errorCode == NsdManager.FAILURE_ALREADY_ACTIVE
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) {
                            cont.resume(
                                Resolved(
                                    serviceInfo.serviceName,
                                    listOfNotNull(serviceInfo.host),
                                    serviceInfo.port,
                                    serviceInfo.attributesAsText(),
                                ),
                            )
                        }
                    }
                })
            }
            if (result != null || !retry) break
            delay(500)
        }
        result
    }

    private companion object {
        const val TAG = "NsdScannerDiscovery"
        const val RESOLVE_TIMEOUT_MS = 5_000L

        /** service type → is HTTPS */
        val SERVICE_TYPES = listOf("_uscan._tcp" to false, "_uscans._tcp" to true)

        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
