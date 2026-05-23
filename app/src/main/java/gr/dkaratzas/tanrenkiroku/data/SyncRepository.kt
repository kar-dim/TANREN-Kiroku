package gr.dkaratzas.tanrenkiroku.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// Sync class responsible for communication with desktop app
class SyncRepository(private val workoutsDir: File, payload: QrPayload) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val client = buildPinnedClient(payload.cert)
    private inline fun <reified T> encodeBody(value: T) = json.encodeToString(value).toRequestBody(mediaType)
    private val baseUrl = "https://${payload.ip}:${payload.port}"
    private val authHeader = "Bearer ${payload.token}"

    suspend fun ping() = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/ping")
            .header("Authorization", authHeader)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw IOException("Server rejected connection: ${resp.code}")
        }
    }

    suspend fun sendManifest(): ManifestResponse = withContext(Dispatchers.IO) {
        val files = workoutsDir.listFiles { f -> isSyncableFile(f.name) } ?: emptyArray()

        val entries = files.map { file ->
            val normalized = json.encodeToString(json.parseToJsonElement(file.readText())).toByteArray()
            FileManifestEntry(
                filename = file.name,
                modified = file.lastModified() / 1000,
                hash = sha256Hex(normalized)
            )
        }

        val body = encodeBody(entries)
        val req = Request.Builder()
            .url("$baseUrl/sync/manifest")
            .header("Authorization", authHeader)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw IOException("Manifest rejected: ${resp.code}")
            json.decodeFromString<ManifestResponse>(resp.body.string())
        }
    }

    suspend fun uploadFile(filename: String) = withContext(Dispatchers.IO) {
        val file = File(workoutsDir, filename)
        val body = encodeBody(UploadRequest(filename = filename, content = json.parseToJsonElement(file.readText())))
        val req = Request.Builder()
            .url("$baseUrl/sync/upload")
            .header("Authorization", authHeader)
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw IOException("Upload of $filename failed: ${resp.code}")
        }
    }

}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun buildPinnedClient(expectedFingerprint: String): OkHttpClient {
    val trustManager = PinnedCertTrustManager(expectedFingerprint)
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true } // connecting by IP, hostname check is irrelevant, cert pin is the trust anchor
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
}

private class PinnedCertTrustManager(private val expectedFingerprint: String) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (sha256Hex(chain[0].encoded) != expectedFingerprint)
            throw CertificateException("Certificate fingerprint mismatch")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
