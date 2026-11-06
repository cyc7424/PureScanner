package com.purescanner.app.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Sends scanner traffic over the Wi-Fi (or Ethernet) network even if Android's default network is
 * mobile data or a VPN, which would otherwise make 192.168.x.x addresses unreachable.
 * The network is looked up on every socket, so Wi-Fi changes are picked up. Falls back to the default route.
 */
class LocalNetworkSocketFactory(context: Context) : SocketFactory() {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION") // allNetworks is deprecated in API 31 but still the simplest correct lookup
    private fun localNetwork(): Network? = connectivity.allNetworks.firstOrNull { network ->
        val caps = connectivity.getNetworkCapabilities(network) ?: return@firstOrNull false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun delegate(): SocketFactory = localNetwork()?.socketFactory ?: getDefault()

    override fun createSocket(): Socket = delegate().createSocket()

    override fun createSocket(host: String, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(host: String, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegate().createSocket(host, port, localAddress, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegate().createSocket(address, port, localAddress, localPort)
}
