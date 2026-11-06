package com.purescanner.app.escl

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal eSCL (AirScan) client.
 *
 * Flow: GET ScannerCapabilities → POST ScanJobs (201 + Location) → GET {job}/NextDocument
 * (repeat for a feeder until 404) → DELETE {job}.
 */
class EsclClient(
    private val http: OkHttpClient = newHttpClient(),
    private val busyRetryDelayMs: Long = 1_000,
    private val busyTimeoutMs: Long = 60_000,
) {

    suspend fun getCapabilities(endpoint: EsclEndpoint): ScannerCapabilities {
        val res = send(Request.Builder().url(endpoint.resolve("ScannerCapabilities")).get().build())
        if (res.code != 200) throw EsclException.Http(res.code, res.text())
        return EsclXml.parseCapabilities(res.text())
    }

    suspend fun getStatus(endpoint: EsclEndpoint): ScannerStatus {
        val res = send(Request.Builder().url(endpoint.resolve("ScannerStatus")).get().build())
        if (res.code != 200) throw EsclException.Http(res.code, res.text())
        return EsclXml.parseStatus(res.text())
    }

    /**
     * Run one scan job and report each page as it arrives via [onPage].
     * Only the first page is fetched; the job is deleted afterwards (a feeder's remaining sheets are not pulled).
     * Cancelling the calling coroutine cancels the job on the scanner.
     */
    suspend fun scan(
        endpoint: EsclEndpoint,
        settings: ScanSettings,
        onPage: suspend (ScannedPage) -> Unit,
    ) {
        val jobUrl = createJob(endpoint, settings)
        try {
            nextDocument(jobUrl, settings.format)?.let { onPage(it) }
        } finally {
            withContext(NonCancellable) { deleteJob(jobUrl) }
        }
    }

    /** Convenience wrapper that collects every page of a job. */
    suspend fun scanAll(endpoint: EsclEndpoint, settings: ScanSettings): List<ScannedPage> {
        val pages = mutableListOf<ScannedPage>()
        scan(endpoint, settings) { pages += it }
        return pages
    }

    // ---------- Job steps ----------

    private suspend fun createJob(endpoint: EsclEndpoint, settings: ScanSettings): HttpUrl {
        val body = EsclXml.buildScanSettings(settings).toRequestBody("text/xml".toMediaType())
        val request = Request.Builder().url(endpoint.resolve("ScanJobs")).post(body).build()

        // 503 = busy; some scanners answer 409 while another job is active.
        val res = retryWhileBusy(retryCodes = setOf(503, 409)) { send(request) }
        if (res.code != 201) {
            if (res.code == 400) throw EsclException.Unsupported("Scanner rejected the scan settings")
            throw EsclException.Http(res.code, res.text())
        }
        val location = res.location ?: throw EsclException.Parse("ScanJobs reply had no Location header")
        // Some scanners put a wrong/internal host in Location. Keep only the path and reuse the host we trust.
        val resolved = endpoint.baseUrl.resolve(location)
            ?: throw EsclException.Parse("Bad Location header: $location")
        return resolved.newBuilder()
            .scheme(endpoint.baseUrl.scheme)
            .host(endpoint.baseUrl.host)
            .port(endpoint.baseUrl.port)
            .build()
    }

    /** @return the next page, or null if the scanner has no more pages. */
    private suspend fun nextDocument(jobUrl: HttpUrl, requested: DocumentFormat): ScannedPage? {
        val url = jobUrl.newBuilder().addPathSegment("NextDocument").build()
        val request = Request.Builder().url(url).get().build()
        val res = retryWhileBusy(retryCodes = setOf(503)) { send(request) }
        return when (res.code) {
            200 -> ScannedPage(res.body, res.contentType?.substringBefore(';')?.trim() ?: requested.mime)
            404 -> null
            else -> throw EsclException.Http(res.code, res.text())
        }
    }

    /** Best effort: the scanner may already have discarded the job. */
    private suspend fun deleteJob(jobUrl: HttpUrl) {
        try {
            send(Request.Builder().url(jobUrl).delete().build())
        } catch (_: EsclException) {
            // ignore
        }
    }

    private suspend fun retryWhileBusy(retryCodes: Set<Int>, attempt: suspend () -> RawResponse): RawResponse {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(busyTimeoutMs)
        while (true) {
            val res = attempt()
            if (res.code !in retryCodes) return res
            if (System.nanoTime() >= deadline) throw EsclException.Busy()
            delay(busyRetryDelayMs)
        }
    }

    // ---------- HTTP plumbing ----------

    /** Fully buffered response: the body is read before we resume so that cancelling aborts the download too. */
    private class RawResponse(val code: Int, val location: String?, val contentType: String?, val body: ByteArray) {
        fun text(): String = body.toString(Charsets.UTF_8)
    }

    private suspend fun send(request: Request): RawResponse = suspendCancellableCoroutine { cont ->
        val call: Call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(EsclException.Network(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = try {
                    response.use {
                        RawResponse(
                            code = it.code,
                            location = it.header("Location"),
                            contentType = it.header("Content-Type"),
                            body = it.body?.bytes() ?: ByteArray(0),
                        )
                    }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(EsclException.Network(e))
                    return
                }
                cont.resume(raw)
            }
        })
    }

    companion object {
        /**
         * @param socketFactory use this to pin traffic to the Wi-Fi network (see LocalNetworkSocketFactory).
         * @param readTimeoutSec a scan itself can take tens of seconds before the first byte of NextDocument.
         */
        fun newHttpClient(
            socketFactory: SocketFactory? = null,
            connectTimeoutSec: Long = 10,
            readTimeoutSec: Long = 120,
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply { socketFactory?.let { socketFactory(it) } }
            .let { LanTls.apply(it) }
            .build()
    }
}
