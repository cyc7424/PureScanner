package com.purescanner.app.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class ScannerDeviceTest {

    private fun ip(s: String): InetAddress = InetAddress.getByName(s)

    @Test
    fun `builds device from TXT records`() {
        val device = ScannerDevice.fromService(
            serviceName = "EPSON XP-1 (2)",
            addresses = listOf(ip("192.168.1.102")),
            port = 80,
            attributes = mapOf("rs" to "/eSCL/", "ty" to "Epson Laserjet", "UUID" to "u-1"),
            https = false,
        )!!

        assertEquals("u-1", device.id)
        assertEquals("Epson Laserjet", device.name)
        assertEquals("192.168.1.102", device.host)
        assertEquals("eSCL", device.rootPath)
        assertEquals("http://192.168.1.102/eSCL", device.endpoint.baseUrl.toString())
    }

    @Test
    fun `falls back when TXT records are missing`() {
        val device = ScannerDevice.fromService("Printer", listOf(ip("10.0.0.5")), 8080, emptyMap(), https = true)!!

        assertEquals("Printer", device.name)
        assertEquals("eSCL", device.rootPath)
        assertEquals("10.0.0.5:8080", device.id)
        assertEquals("https://10.0.0.5:8080/eSCL", device.endpoint.baseUrl.toString())
    }

    @Test
    fun `custom root path is respected`() {
        val device = ScannerDevice.fromService("P", listOf(ip("10.0.0.5")), 80, mapOf("rs" to "scanner/escl"), false)!!
        assertEquals("http://10.0.0.5/scanner/escl", device.endpoint.baseUrl.toString())
    }

    @Test
    fun `prefers IPv4 and skips link-local IPv6`() {
        val both = ScannerDevice.fromService("P", listOf(ip("fe80::1"), ip("192.168.1.9")), 80, emptyMap(), false)!!
        assertEquals("192.168.1.9", both.host)

        val onlyLinkLocal = ScannerDevice.fromService("P", listOf(ip("fe80::1")), 80, emptyMap(), false)
        assertNull(onlyLinkLocal)

        val globalV6 = ScannerDevice.fromService("P", listOf(ip("fd00::1")), 80, emptyMap(), false)
        assertNotNull(globalV6)
    }

    @Test
    fun `no addresses gives null`() {
        assertNull(ScannerDevice.fromService("P", emptyList(), 80, emptyMap(), false))
    }

    @Test
    fun `merge keeps one entry per host preferring manual then http`() {
        val http = ScannerDevice("a", "Epson", "192.168.1.2", 80, https = false)
        val https = ScannerDevice("a", "Epson", "192.168.1.2", 443, https = true)
        val other = ScannerDevice("b", "Brother", "192.168.1.3", 80)

        val merged = mergeDevices(listOf(https, http, other))
        assertEquals(listOf("Brother", "Epson"), merged.map { it.name })
        assertEquals(80, merged.first { it.host == "192.168.1.2" }.port)

        val manual = ScannerDevice("m", "My scanner", "192.168.1.2", 443, https = true, manual = true)
        val withManual = mergeDevices(listOf(http, manual))
        assertEquals(1, withManual.size)
        assertEquals("My scanner", withManual[0].name)
    }

    @Test
    fun `parses host input`() {
        assertEquals(HostInput("192.168.1.5", null), parseHostInput("192.168.1.5"))
        assertEquals(HostInput("192.168.1.5", null), parseHostInput("  192.168.1.5 "))
        assertEquals(HostInput("10.0.2.2", 8080), parseHostInput("10.0.2.2:8080"))
        assertEquals(HostInput("printer.local", null), parseHostInput("http://printer.local/"))
        assertEquals(HostInput("fd00::1", 80), parseHostInput("[fd00::1]:80"))
        assertEquals(HostInput("fd00::1", null), parseHostInput("fd00::1"))
    }

    @Test
    fun `rejects bad host input`() {
        assertNull(parseHostInput(""))
        assertNull(parseHostInput("   "))
        assertNull(parseHostInput("192.168.1.5:abc"))
        assertNull(parseHostInput("192.168.1.5:99999"))
        assertNull(parseHostInput("[fd00::1"))
        assertNull(parseHostInput("bad host name"))
    }
}
