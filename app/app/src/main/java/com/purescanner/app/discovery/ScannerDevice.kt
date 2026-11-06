package com.purescanner.app.discovery

import com.purescanner.app.escl.EsclEndpoint
import okhttp3.HttpUrl
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** A scanner the user can pick: either found via mDNS or added by hand. */
data class ScannerDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val rootPath: String = "eSCL",
    val https: Boolean = false,
    val manual: Boolean = false,
) {
    val endpoint: EsclEndpoint get() = EsclEndpoint.fromHost(host, port, rootPath, https)

    companion object {
        /**
         * Build a device from a resolved `_uscan._tcp` / `_uscans._tcp` service.
         * TXT keys used: `rs` (eSCL root path), `ty` (model name), `UUID`.
         * @return null if the service has no usable IP address.
         */
        fun fromService(
            serviceName: String,
            addresses: List<InetAddress>,
            port: Int,
            attributes: Map<String, String>,
            https: Boolean,
        ): ScannerDevice? {
            val host = pickAddress(addresses) ?: return null
            val rootPath = attributes["rs"]?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: "eSCL"
            val name = attributes["ty"]?.trim()?.takeIf { it.isNotEmpty() } ?: serviceName
            val id = attributes["UUID"]?.trim()?.takeIf { it.isNotEmpty() } ?: "$host:$port"
            return ScannerDevice(id, name, host, port, rootPath, https, manual = false)
        }

        /** Prefer IPv4; fall back to a non-link-local IPv6 (link-local needs a scope id we can't use). */
        internal fun pickAddress(addresses: List<InetAddress>): String? {
            addresses.firstOrNull { it is Inet4Address }?.hostAddress?.let { return it }
            return addresses.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
                ?.hostAddress?.substringBefore('%')
        }
    }
}

/**
 * One entry per physical scanner. Printers usually advertise both `_uscan` (http) and `_uscans` (https),
 * so group by host and prefer: manual entry (the user named it), then plain http.
 */
fun mergeDevices(devices: List<ScannerDevice>): List<ScannerDevice> =
    devices.groupBy { it.host }.values
        .map { group -> group.minWith(compareBy({ !it.manual }, { it.https })) }
        .sortedBy { it.name.lowercase() }

data class HostInput(val host: String, val port: Int?)

/**
 * Parse what a user typed in "add device manually": `192.168.1.5`, `192.168.1.5:8080`, `http://printer.local/`,
 * `[fd00::1]:80`, a bare IPv6 address. Returns null if it can't be a valid host.
 */
fun parseHostInput(raw: String): HostInput? {
    val s = raw.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
    if (s.isEmpty()) return null

    val host: String
    var port: Int? = null
    when {
        s.startsWith("[") -> {
            val end = s.indexOf(']')
            if (end < 0) return null
            host = s.substring(1, end)
            val rest = s.substring(end + 1)
            if (rest.startsWith(":")) port = rest.drop(1).toIntOrNull() ?: return null
            else if (rest.isNotEmpty()) return null
        }
        s.count { it == ':' } == 1 -> {
            host = s.substringBefore(':')
            port = s.substringAfter(':').toIntOrNull() ?: return null
        }
        else -> host = s // hostname, IPv4, or bare IPv6
    }
    if (port != null && port !in 1..65535) return null
    val valid = try {
        HttpUrl.Builder().scheme("http").host(host).build()
        true
    } catch (_: IllegalArgumentException) {
        false
    }
    return if (valid) HostInput(host, port) else null
}
