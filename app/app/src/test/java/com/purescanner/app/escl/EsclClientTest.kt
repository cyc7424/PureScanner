package com.purescanner.app.escl

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class EsclClientTest {

    private lateinit var server: MockWebServer
    private lateinit var endpoint: EsclEndpoint
    private val client = EsclClient(busyRetryDelayMs = 10, busyTimeoutMs = 300)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        endpoint = EsclEndpoint(server.url("/eSCL"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private fun jpegResponse() = MockResponse()
        .setHeader("Content-Type", "image/jpeg")
        .setBody(Buffer().write(jpegBytes))

    @Test
    fun `platen scan returns one page and deletes the job`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Location", "/eSCL/ScanJobs/job1"))
        server.enqueue(jpegResponse())
        server.enqueue(MockResponse().setResponseCode(200)) // DELETE

        val pages = client.scanAll(endpoint, ScanSettings())

        assertEquals(1, pages.size)
        assertEquals("image/jpeg", pages[0].mime)
        assertTrue(pages[0].bytes.contentEquals(jpegBytes))

        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/eSCL/ScanJobs", post.path)
        assertTrue(post.body.readUtf8().contains("<pwg:InputSource>Platen</pwg:InputSource>"))

        val get = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("/eSCL/ScanJobs/job1/NextDocument", get.path)

        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/eSCL/ScanJobs/job1", delete.path)
    }

    @Test
    fun `feeder scan fetches only the first page`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Location", "/eSCL/ScanJobs/job2"))
        server.enqueue(jpegResponse())
        server.enqueue(MockResponse().setResponseCode(200)) // DELETE

        val pages = client.scanAll(endpoint, ScanSettings(source = InputSource.Feeder))

        assertEquals(1, pages.size)
    }

    @Test
    fun `absolute Location with wrong host is rewritten to the scanner host`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setHeader("Location", "http://localhost:1234/eSCL/ScanJobs/job3"),
        )
        server.enqueue(jpegResponse())
        server.enqueue(MockResponse().setResponseCode(200))

        client.scanAll(endpoint, ScanSettings())

        server.takeRequest() // POST
        assertEquals("/eSCL/ScanJobs/job3/NextDocument", server.takeRequest().path)
    }

    @Test
    fun `busy scanner is retried then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Location", "/eSCL/ScanJobs/job4"))
        server.enqueue(jpegResponse())
        server.enqueue(MockResponse().setResponseCode(200))

        assertEquals(1, client.scanAll(endpoint, ScanSettings()).size)
    }

    @Test
    fun `scanner that stays busy throws Busy`() = runBlocking {
        repeat(100) { server.enqueue(MockResponse().setResponseCode(503)) }
        try {
            client.scanAll(endpoint, ScanSettings())
            fail("expected Busy")
        } catch (_: EsclException.Busy) {
        }
    }

    @Test
    fun `rejected settings throw Unsupported`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))
        try {
            client.scanAll(endpoint, ScanSettings())
            fail("expected Unsupported")
        } catch (_: EsclException.Unsupported) {
        }
    }

    @Test
    fun `job is deleted even if a page request fails`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Location", "/eSCL/ScanJobs/job5"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200)) // DELETE

        try {
            client.scanAll(endpoint, ScanSettings())
            fail("expected Http")
        } catch (e: EsclException.Http) {
            assertEquals(500, e.code)
        }

        server.takeRequest()
        server.takeRequest()
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `unreachable scanner throws Network`() = runBlocking {
        val dead = EsclEndpoint.fromHost("127.0.0.1", port = 1) // nothing listens on port 1
        try {
            client.getCapabilities(dead)
            fail("expected Network")
        } catch (_: EsclException.Network) {
        }
    }

    @Test
    fun `getCapabilities parses reply`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<scan:ScannerCapabilities xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03"
                       xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
                     <pwg:MakeAndModel>Test Scanner</pwg:MakeAndModel>
                   </scan:ScannerCapabilities>""",
            ),
        )
        assertEquals("Test Scanner", client.getCapabilities(endpoint).makeAndModel)
        assertEquals("/eSCL/ScannerCapabilities", server.takeRequest().path)
    }

    @Test
    fun `fromHost builds default endpoint`() {
        assertEquals("http://192.168.1.102/eSCL", EsclEndpoint.fromHost("192.168.1.102").baseUrl.toString())
    }
}
