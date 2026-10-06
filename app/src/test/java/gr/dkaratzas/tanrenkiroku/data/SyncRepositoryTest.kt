package gr.dkaratzas.tanrenkiroku.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.X509TrustManager

class SyncRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val payload = QrPayload("127.0.0.1", 443, "1".repeat(32), "a".repeat(64), 2)
    private val workout = """{"date":"2026-10-06","entries":[{"exerciseId":"push_up","sets":[{"reps":12,"kg":0}]}]}"""

    private fun repository(directory: File, dispatcher: Dispatcher = Dispatcher(), handler: (Request) -> Pair<Int, String>): SyncRepository {
        val client = OkHttpClient.Builder().dispatcher(dispatcher).addInterceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer ${payload.token}", request.header("Authorization"))
            val (code, text) = handler(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message("test").body(text.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return SyncRepository(directory, payload, client)
    }

    private fun Request.bodyText(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()

    // Model Android's message-less runtime exception when socket cleanup runs on the UI
    // thread. Interceptor-only tests normally never exercise a real TLS pool's cleanup.
    private class MainThreadNetworkException : RuntimeException()
    private class CleanupExecutor(private val uiThread: AtomicReference<Thread>) : ThreadPoolExecutor(
        0, Int.MAX_VALUE, 60, TimeUnit.SECONDS, SynchronousQueue<Runnable>()
    ) {
        var cleanupThread: Thread? = null
        override fun shutdown() {
            if (Thread.currentThread() == uiThread.get()) throw MainThreadNetworkException()
            cleanupThread = Thread.currentThread()
            super.shutdown()
        }
    }

    @Test fun acknowledgedSyncAndClientCleanupRunOffUiThread() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher().use { ui ->
            val uiThread = AtomicReference<Thread>()
            withContext(ui) { uiThread.set(Thread.currentThread()) }
            val directory = temporary.newFolder()
            var acknowledged = false
            fun response(request: Request): Pair<Int, String> = when (request.url.encodedPath) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to """{"sessionId":"id","needed":[]}"""
                "/sync/complete" -> { acknowledged = true; 200 to """{"ok":true}""" }
                else -> error("Unexpected path")
            }
            // Reproduce the former caller: desktop acknowledges, but use closes on UI
            // and turns that success into precisely the message-less failure reported.
            val oldExecutor = CleanupExecutor(uiThread)
            try {
                val failure = withContext(ui) {
                    runCatching { repository(directory, Dispatcher(oldExecutor), ::response).use { it.sync() } }.exceptionOrNull()
                }
                assertTrue(acknowledged)
                assertTrue(failure is MainThreadNetworkException)
                assertNull(failure!!.message)
            } finally { withContext(Dispatchers.IO) { oldExecutor.shutdown() } }

            acknowledged = false
            val executor = CleanupExecutor(uiThread)
            val progress = mutableListOf<String>()
            val result = withContext(ui) {
                val synced = syncToDesktop({
                    assertNotSame(uiThread.get(), Thread.currentThread())
                    repository(directory, Dispatcher(executor), ::response)
                }) { step ->
                    withContext(ui) { progress.add(step) }
                }
                assertSame(uiThread.get(), Thread.currentThread())
                synced
            }
            assertTrue(acknowledged)
            assertEquals(SyncResult(0, 0), result)
            assertTrue(executor.isShutdown)
            assertNotSame(uiThread.get(), executor.cleanupThread)
            assertEquals("Completing sync...", progress.last())
        }
    }

    @Test fun failedCompletionStillFailsAndClosesClientOffUiThread() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher().use { ui ->
            val uiThread = AtomicReference<Thread>()
            withContext(ui) { uiThread.set(Thread.currentThread()) }
            val executor = CleanupExecutor(uiThread)
            val directory = temporary.newFolder()
            val failure = withContext(ui) {
                runCatching {
                    syncToDesktop({ repository(directory, Dispatcher(executor)) { request ->
                        when (request.url.encodedPath) {
                            "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                            "/sync/manifest" -> 200 to """{"sessionId":"id","needed":[]}"""
                            else -> 500 to """{"error":"Completion failed"}"""
                        }
                    } })
                }.exceptionOrNull()
            }
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Completion failed"))
            assertTrue(executor.isShutdown)
            assertNotSame(uiThread.get(), executor.cleanupThread)
        }
    }

    @Test fun uploadsCapturedContentWithExactHashAndCommitsLast() = runBlocking {
        val directory = temporary.newFolder()
        val file = File(directory, "2026-10-06.json").apply { writeText(workout) }
        val custom = File(directory, CUSTOM_EXERCISES_FILENAME).apply {
            writeText("""[{"id":"custom_δοκιμή","name":"Πιέσεις 💪","pickerGroup":"Chest","primaryMuscles":["Chest"],"secondaryMuscles":[]}]""")
        }
        val originalNames = listOf(file.name, custom.name)
        val hashes = mutableMapOf<String, String>()
        val requests = mutableListOf<String>()
        repository(directory) { request ->
            val path = request.url.encodedPath
            requests.add(path)
            when (path) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> {
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    assertEquals("2", body.getValue("protocolVersion").toString())
                    assertEquals("true", body.getValue("complete").toString())
                    body.getValue("files").jsonArray.forEach {
                        val entry = it.jsonObject
                        hashes[entry.getValue("filename").jsonPrimitive.content] = entry.getValue("hash").jsonPrimitive.content
                    }
                    // Both edits and deletions after capture must leave the upload unchanged.
                    file.writeText("broken after capture")
                    custom.delete()
                    200 to """{"sessionId":"batch-1","needed":["2026-10-06.json","custom_exercises.json"],"deleted":3}"""
                }
                "/sync/upload" -> {
                    val body = Json.parseToJsonElement(request.bodyText()).jsonObject
                    assertEquals("batch-1", body.getValue("sessionId").jsonPrimitive.content)
                    val filename = body.getValue("filename").jsonPrimitive.content
                    val content = body.getValue("content")
                    assertEquals(hashes[filename], sha256Hex(content.toString().toByteArray(Charsets.UTF_8)))
                    validateSyncContent(filename, content)
                    200 to """{"ok":true}"""
                }
                "/sync/complete" -> {
                    assertEquals("""{"sessionId":"batch-1"}""", request.bodyText())
                    200 to """{"ok":true}"""
                }
                else -> error("Unexpected request: $path")
            }
        }.use { repo -> assertEquals(SyncResult(2, 3), repo.sync()) }
        assertEquals(originalNames.toSet(), hashes.keys)
        assertEquals(listOf("/ping", "/sync/manifest", "/sync/upload", "/sync/upload", "/sync/complete"), requests)
    }

    @Test fun alwaysCompletesNoUploadAndEmptyBatches() = runBlocking {
        for (deleted in listOf(0, 2)) {
            val directory = temporary.newFolder()
            val paths = mutableListOf<String>()
            repository(directory) { request ->
                val path = request.url.encodedPath
                paths.add(path)
                when (path) {
                    "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                    "/sync/manifest" -> {
                        val manifest = syncJson.decodeFromString<ManifestRequest>(request.bodyText())
                        assertTrue(manifest.complete)
                        assertTrue(manifest.files.isEmpty())
                        200 to """{"sessionId":"empty","needed":[],"deleted":$deleted}"""
                    }
                    "/sync/complete" -> 200 to """{"ok":true}"""
                    else -> error("Unexpected upload")
                }
            }.use { assertEquals(SyncResult(0, deleted), it.sync()) }
            assertEquals(listOf("/ping", "/sync/manifest", "/sync/complete"), paths)
        }
    }

    @Test fun rejectsOldOrUnknownDesktopProtocolsBeforeManifest() = runBlocking {
        for (response in listOf("""{"ok":true}""", """{"ok":true,"protocolVersion":1}""", """{"ok":true,"protocolVersion":3}""", """{"ok":false,"protocolVersion":2}""")) {
            repository(temporary.newFolder()) { request ->
                assertEquals("/ping", request.url.encodedPath)
                200 to response
            }.use { repo -> assertTrue(runCatching { repo.sync() }.exceptionOrNull() is IOException) }
        }
    }

    @Test fun stopsBeforeManifestWhenAnyLocalFileIsInvalid() = runBlocking {
        val directory = temporary.newFolder()
        File(directory, "2026-10-06.json").writeText(workout)
        File(directory, "2026-10-05.json").writeText("broken")
        repository(directory) { request ->
            assertEquals("/ping", request.url.encodedPath)
            200 to """{"ok":true,"protocolVersion":2}"""
        }.use { repo ->
            val failure = runCatching { repo.sync() }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("2026-10-05.json"))
        }
    }

    @Test fun rejectsInvalidSessionAndUnrequestedPathsBeforeUploadOrCompletion() = runBlocking {
        val directory = temporary.newFolder()
        File(directory, "2026-10-06.json").writeText(workout)
        val responses = listOf(
            """{"needed":[]}""",
            """{"sessionId":"","needed":[]}""",
            """{"sessionId":"id","needed":["../secret.json"]}""",
            """{"sessionId":"id","needed":["2026-10-05.json"]}""",
            """{"sessionId":"id","needed":["2026-10-06.json","2026-10-06.json"]}""",
            """{"sessionId":"id","needed":[],"deleted":-1}"""
        )
        for (response in responses) repository(directory) { request ->
            when (request.url.encodedPath) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to response
                else -> error("Invalid response must stop the batch")
            }
        }.use { repo -> assertTrue(runCatching { repo.sync() }.exceptionOrNull() is IOException) }
    }

    @Test fun failedUploadNeverCommitsAndRetainsDesktopError() = runBlocking {
        val directory = temporary.newFolder()
        File(directory, "2026-10-06.json").writeText(workout)
        repository(directory) { request ->
            when (request.url.encodedPath) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to """{"sessionId":"id","needed":["2026-10-06.json"]}"""
                "/sync/upload" -> 409 to """{"error":"Unknown or expired sync session. Send a fresh manifest."}"""
                else -> error("Failed upload must not commit")
            }
        }.use { repo ->
            val failure = runCatching { repo.sync() }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Unknown or expired sync session"))
        }
    }

    @Test fun failedCompletionCannotReturnSuccess() = runBlocking {
        for ((code, body) in listOf(500 to """{"error":"Could not save the transfer"}""", 200 to """{"ok":false}""", 200 to "{}")) {
            repository(temporary.newFolder()) { request ->
                when (request.url.encodedPath) {
                    "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                    "/sync/manifest" -> 200 to """{"sessionId":"id","needed":[]}"""
                    "/sync/complete" -> code to body
                    else -> error("Unexpected path")
                }
            }.use { repo -> assertTrue(runCatching { repo.sync() }.exceptionOrNull() is IOException) }
        }
    }

    @Test fun cancellationBeforeCompletionDoesNotCommit() = runBlocking {
        repository(temporary.newFolder()) { request ->
            when (request.url.encodedPath) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to """{"sessionId":"id","needed":[]}"""
                else -> error("Cancelled batch must not commit")
            }
        }.use { repo ->
            val failure = runCatching { repo.sync { if (it == "Completing sync...") throw CancellationException("Leave screen") } }.exceptionOrNull()
            assertTrue(failure is CancellationException)
        }
    }

    @Test fun certificatePinRejectsEmptyChain() {
        val trust: X509TrustManager = PinnedCertTrustManager(payload.cert)
        assertTrue(runCatching { trust.checkServerTrusted(emptyArray(), "RSA") }.exceptionOrNull() is java.security.cert.CertificateException)
    }

    @Test fun transportRetryReusesUploadAndCompletionRequestBytes() = runBlocking {
        val directory = temporary.newFolder()
        File(directory, "2026-10-06.json").writeText(workout)
        val attempts = mutableMapOf<String, MutableList<String>>()
        repository(directory) { request ->
            val path = request.url.encodedPath
            val bodies = attempts.getOrPut(path) { mutableListOf() }
            bodies.add(request.body?.let { request.bodyText() } ?: "")
            if ((path == "/sync/upload" || path == "/sync/complete") && bodies.size == 1) throw IOException("Lost response")
            when (path) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to """{"sessionId":"stable-id","needed":["2026-10-06.json"]}"""
                else -> 200 to """{"ok":true}"""
            }
        }.use { assertEquals(SyncResult(1, 0), it.sync()) }
        for (path in listOf("/sync/upload", "/sync/complete")) {
            assertEquals(2, attempts.getValue(path).size)
            assertEquals(1, attempts.getValue(path).distinct().size)
        }
    }

    @Test fun cancellationCancelsAwaitingHttpCallBeforeItCanCompleteBatch() = runBlocking {
        val directory = temporary.newFolder()
        File(directory, "2026-10-06.json").writeText(workout)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        repository(directory) { request ->
            when (request.url.encodedPath) {
                "/ping" -> 200 to """{"ok":true,"protocolVersion":2}"""
                "/sync/manifest" -> 200 to """{"sessionId":"id","needed":["2026-10-06.json"]}"""
                "/sync/upload" -> {
                    started.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    200 to """{"ok":true}"""
                }
                else -> error("Cancelled HTTP call must not reach completion")
            }
        }.use { repo ->
            val job = async(Dispatchers.IO) { repo.sync() }
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue(job.isCancelled)
            } finally { release.countDown() }
        }
    }
}
