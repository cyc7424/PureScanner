package com.purescanner.app.escl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTlsTest {

    @Test
    fun `private literals are accepted`() {
        assertTrue(LanTls.isPrivateAddress("192.168.1.102"))
        assertTrue(LanTls.isPrivateAddress("10.0.2.2"))
        assertTrue(LanTls.isPrivateAddress("172.16.5.5"))
        assertTrue(LanTls.isPrivateAddress("169.254.1.1"))
        assertTrue(LanTls.isPrivateAddress("127.0.0.1"))
        assertTrue(LanTls.isPrivateAddress("fe80::1"))
        assertTrue(LanTls.isPrivateAddress("[fd00::1]"))
    }

    @Test
    fun `public addresses and hostnames are rejected`() {
        assertFalse(LanTls.isPrivateAddress("8.8.8.8"))
        assertFalse(LanTls.isPrivateAddress("2001:4860:4860::8888"))
        assertFalse(LanTls.isPrivateAddress("example.com"))
        assertFalse(LanTls.isPrivateAddress("printer.local"))
        assertFalse(LanTls.isPrivateAddress("1234"))
        assertFalse(LanTls.isPrivateAddress(""))
    }
}
