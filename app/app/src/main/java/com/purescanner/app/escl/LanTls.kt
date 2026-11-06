package com.purescanner.app.escl

import okhttp3.OkHttpClient
import java.net.Inet6Address
import java.net.InetAddress
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Scanners that advertise `_uscans._tcp` almost always use a self-signed certificate, which normal TLS
 * validation rejects.
 *
 * Trade-off: we accept any certificate, but ONLY when the scanner's address is a literal private-network IP
 * (192.168.x.x, 10.x.x.x, fe80::, …). Traffic to anything else still fails the hostname check. This protects
 * against accidentally talking to a public host, but it does not stop someone already on the same LAN from
 * impersonating the scanner. Plain `http` eSCL has the same exposure, so this is not a regression.
 */
internal object LanTls {

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return builder
            .sslSocketFactory(context.socketFactory, trustAll)
            .hostnameVerifier { host, _ -> isPrivateAddress(host) }
    }

    private val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** True only for IP *literals* in private/link-local/loopback ranges. Never does a DNS lookup. */
    fun isPrivateAddress(host: String): Boolean {
        val literal = host.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        val isLiteral = ipv4Regex.matches(literal) || literal.contains(':')
        if (!isLiteral) return false
        val address = try {
            InetAddress.getByName(literal)
        } catch (_: Exception) {
            return false
        }
        if (address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) return true
        // IPv6 unique local addresses (fc00::/7)
        return address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC
    }
}
