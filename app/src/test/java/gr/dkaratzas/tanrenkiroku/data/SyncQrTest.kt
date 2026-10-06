package gr.dkaratzas.tanrenkiroku.data

import org.junit.Assert.*
import org.junit.Test

class SyncQrTest {
    private val payload = QrPayload("192.168.1.20", 12345, "1".repeat(32), "a".repeat(64), 2)

    @Test fun acceptsDesktopV2QrAndRejectsOlderVersionsWithUpdateMessage() {
        assertEquals(payload, parseSyncQr(syncJson.encodeToString(payload)))
        for (version in listOf(null, 1, 3)) {
            val failure = runCatching { parseSyncQr(syncJson.encodeToString(payload.copy(protocolVersion = version))) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("Update Metsuke"))
        }
    }

    @Test fun rejectsInvalidOrUnboundedQrFields() {
        for (invalid in listOf(
            payload.copy(ip = "example.com"), payload.copy(ip = "999.1.1.1"), payload.copy(ip = "127.0.0.1\n"),
            payload.copy(ip = "1".repeat(500)), payload.copy(port = 0), payload.copy(port = 65536),
            payload.copy(token = "1".repeat(31)), payload.copy(token = "\n".repeat(32)),
            payload.copy(cert = "not-a-fingerprint")
        )) assertTrue(runCatching { validateSyncQr(invalid) }.isFailure)
        assertTrue(runCatching { parseSyncQr(" ".repeat(4097)) }.isFailure)
    }

    @Test fun malformedQrErrorDoesNotExposeToken() {
        val token = "secret-qr-token"
        val failure = runCatching { parseSyncQr("""{"token":"$token","port":"bad"}""") }.exceptionOrNull()
        assertFalse(failure!!.message!!.contains(token))
    }
}
