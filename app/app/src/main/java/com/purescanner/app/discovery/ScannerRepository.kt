package com.purescanner.app.discovery

import android.content.Context
import com.purescanner.app.escl.EsclClient
import com.purescanner.app.escl.EsclEndpoint
import com.purescanner.app.escl.EsclException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

sealed class ManualAddResult {
    data class Added(val device: ScannerDevice) : ManualAddResult()

    /** The text is not a usable IP address / host name. */
    data object InvalidInput : ManualAddResult()

    /** Nothing answered like an eSCL scanner at that address. */
    data object NotFound : ManualAddResult()
}

/** Single entry point for "which scanners exist": discovered ones, manually added ones, and the HTTP client to use. */
class ScannerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val socketFactory = LocalNetworkSocketFactory(appContext)
    private val discovery = NsdScannerDiscovery(appContext)
    private val prefs = appContext.getSharedPreferences("manual_devices", Context.MODE_PRIVATE)

    /** Client for real scanning (long read timeout). */
    val client = EsclClient(EsclClient.newHttpClient(socketFactory))

    /** Client used only to check "is there a scanner here?" so a wrong address fails fast. */
    private val probeClient = EsclClient(
        http = EsclClient.newHttpClient(socketFactory, connectTimeoutSec = 3, readTimeoutSec = 8),
        busyTimeoutMs = 0,
    )

    fun discover(timeoutMs: Long = 8_000): Flow<ScannerDevice> = discovery.discover(timeoutMs)

    // ---------- Manual devices ----------

    fun manualDevices(): List<ScannerDevice> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ScannerDevice(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    host = o.getString("host"),
                    port = o.getInt("port"),
                    rootPath = o.getString("rootPath"),
                    https = o.getBoolean("https"),
                    manual = true,
                )
            }
        } catch (_: Exception) {
            emptyList() // corrupt data: start over rather than crash
        }
    }

    fun removeManual(id: String) = saveManual(manualDevices().filterNot { it.id == id })

    /**
     * Verify that an eSCL scanner answers at [input] and remember it.
     * With no port typed we try http:80 then https:443; with a port we try http then https on it.
     */
    suspend fun addManual(input: String, name: String?): ManualAddResult {
        val parsed = parseHostInput(input) ?: return ManualAddResult.InvalidInput

        val candidates = if (parsed.port != null) {
            listOf(parsed.port to false, parsed.port to true)
        } else {
            listOf(80 to false, 443 to true)
        }

        for ((port, https) in candidates) {
            val endpoint = EsclEndpoint.fromHost(parsed.host, port, https = https)
            val caps = try {
                probeClient.getCapabilities(endpoint)
            } catch (_: EsclException) {
                continue // Network, Http or Parse: not a scanner on this port/scheme
            }
            val device = ScannerDevice(
                id = caps.uuid ?: "${parsed.host}:$port",
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: caps.makeAndModel ?: parsed.host,
                host = parsed.host,
                port = port,
                rootPath = "eSCL",
                https = https,
                manual = true,
            )
            saveManual(manualDevices().filterNot { it.host == device.host && it.port == device.port } + device)
            return ManualAddResult.Added(device)
        }
        return ManualAddResult.NotFound
    }

    private fun saveManual(devices: List<ScannerDevice>) {
        val array = JSONArray()
        devices.forEach { d ->
            array.put(
                JSONObject()
                    .put("id", d.id).put("name", d.name).put("host", d.host).put("port", d.port)
                    .put("rootPath", d.rootPath).put("https", d.https),
            )
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
    }
}
