package gr.dkaratzas.tanrenkiroku.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Keep construction, transfer, and TLS socket cleanup off the UI thread. Closing an idle
// HTTPS connection can send close_notify and trigger Android's network thread checks.
internal suspend fun syncToDesktop(
    createRepository: () -> SyncRepository,
    onProgress: suspend (String) -> Unit = {}
): SyncResult = withContext(Dispatchers.IO) {
    createRepository().use { it.sync(onProgress) }
}

class SyncRepository internal constructor(
    private val workoutsDir: File,
    payload: QrPayload,
    private val client: OkHttpClient
) : Closeable {
    constructor(workoutsDir: File, payload: QrPayload) : this(workoutsDir, payload, buildPinnedClient(validateSyncQr(payload).cert))

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://localhost".toHttpUrl().newBuilder()
        .host(payload.ip).port(payload.port).build()
    private val authHeader = "Bearer ${payload.token}"

    init {
        validateSyncQr(payload)
    }

    // Success means the desktop acknowledged its committed snapshot, including removal-only batches.
    suspend fun sync(onProgress: suspend (String) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
        onProgress("Connecting...")
        val ping: PingResponse = request("/ping", "Connection")
        if (!ping.ok || ping.protocolVersion != SYNC_PROTOCOL_VERSION)
            throw IOException("This version of Kiroku requires Metsuke sync protocol v2. Update the desktop app and scan its QR code again.")

        onProgress("Checking workout files...")
        val snapshot = captureWorkoutSnapshot(workoutsDir)
        ensureActive()
        onProgress("Sending manifest...")
        val manifest: ManifestResponse = request("/sync/manifest", "Manifest", encodeBody(snapshot.manifest))
        if (manifest.sessionId.isBlank() || manifest.deleted < 0 ||
            manifest.needed.distinct().size != manifest.needed.size ||
            manifest.needed.any { it !in snapshot.files })
            throw IOException("Desktop returned an invalid sync session or requested files outside the manifest. Refresh the desktop connection and retry.")

        manifest.needed.forEachIndexed { index, filename ->
            ensureActive()
            onProgress("Uploading file ${index + 1} of ${manifest.needed.size}...")
            val upload = UploadRequest(manifest.sessionId, filename, snapshot.files.getValue(filename).content)
            val acknowledgement: SyncAcknowledgement = request("/sync/upload", "Upload of $filename", encodeBody(upload))
            if (!acknowledgement.ok) throw IOException("Desktop did not accept $filename")
        }
        ensureActive()
        onProgress("Completing sync...")
        val acknowledgement: SyncAcknowledgement = request("/sync/complete", "Sync completion", encodeBody(CompletionRequest(manifest.sessionId)))
        if (!acknowledgement.ok) throw IOException("Desktop did not confirm sync completion. Retry sync.")
        SyncResult(manifest.needed.size, manifest.deleted)
    }

    private inline fun <reified T> encodeBody(value: T): RequestBody {
        val bytes = syncJson.encodeToString(value).toByteArray(Charsets.UTF_8)
        if (bytes.size > 8 * 1024 * 1024) throw IOException("Sync request exceeds the desktop's 8 MiB limit")
        return bytes.toRequestBody(mediaType)
    }

    private suspend inline fun <reified T> request(path: String, operation: String, body: RequestBody? = null): T {
        val request = Request.Builder()
            .url(baseUrl.resolve(path)!!)
            .header("Authorization", authHeader)
            .apply { if (body != null) post(body) }
            .build()
        val responseText = executeWithRetry(request, operation)
        return try {
            syncJson.decodeFromString<T>(responseText)
        } catch (e: Exception) {
            throw IOException("$operation returned an invalid response. Update Metsuke and retry sync.", e)
        }
    }

    private suspend fun executeWithRetry(request: Request, operation: String): String {
        repeat(3) { attempt ->
            try { return execute(request, operation) }
            catch (e: IOException) {
                if (e is SyncHttpException || e is SSLException || attempt == 2) throw e
                // Reuse the same request bytes and session when a response is lost.
                delay(250L * (attempt + 1))
            }
        }
        error("Unreachable")
    }

    private suspend fun execute(request: Request, operation: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        val text = it.body.string()
                        if (!it.isSuccessful) {
                            val detail = runCatching {
                                Json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                            }.getOrNull()
                            val advice = when (it.code) {
                                401 -> ". Scan the current desktop QR code again."
                                409 -> ". Start sync again with a fresh manifest."
                                413 -> ". The desktop request limit is 8 MiB."
                                else -> ""
                            }
                            throw SyncHttpException("$operation failed (HTTP ${it.code})${detail?.let { message -> ": $message" } ?: ""}$advice")
                        }
                        text
                    }
                    continuation.resume(text)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    override fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

private class SyncHttpException(message: String) : IOException(message)

private fun buildPinnedClient(expectedFingerprint: String): OkHttpClient {
    val trustManager = PinnedCertTrustManager(expectedFingerprint.lowercase(java.util.Locale.ROOT))
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true } // The scanned certificate fingerprint is the trust anchor for an IP connection.
        .protocols(listOf(Protocol.HTTP_1_1))
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

internal class PinnedCertTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty() || sha256Hex(chain[0].encoded) != expectedFingerprint)
            throw CertificateException("Certificate fingerprint mismatch. Refresh the desktop connection and scan its QR code again.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
