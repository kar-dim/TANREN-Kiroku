package gr.dkaratzas.tanrenkiroku.data

import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun parseSyncQr(raw: String): QrPayload {
    require(raw.length <= 4096 && raw.toByteArray(Charsets.UTF_8).size <= 4096) { "QR code is too large" }
    val payload = try { syncJson.decodeFromString<QrPayload>(raw) }
    catch (e: kotlinx.serialization.SerializationException) { throw IllegalArgumentException("Invalid QR code. Scan the QR code shown by TANREN Metsuke.", e) }
    return validateSyncQr(payload)
}

internal fun validateSyncQr(payload: QrPayload): QrPayload {
    require(payload.protocolVersion == SYNC_PROTOCOL_VERSION) { "This QR code requires a different sync version. Update Metsuke and scan its new QR code." }
    require(payload.ip.length in 1..45 && payload.ip.none { it.isWhitespace() }) { "Invalid desktop IP address" }
    val ipv4 = payload.ip.split('.').let { parts -> parts.size == 4 && parts.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() in 0..255 } }
    val ipv6 = ':' in payload.ip && payload.ip.matches(Regex("[0-9a-fA-F:.]+"))
    require(ipv4 || ipv6) { "QR code must contain a numeric desktop IP address" }
    "https://localhost".toHttpUrl().newBuilder().host(payload.ip).build()
    require(payload.port in 1..65535) { "Invalid desktop port" }
    require(payload.token.matches(Regex("[0-9a-fA-F]{32}"))) { "Invalid desktop connection token" }
    require(payload.cert.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid desktop certificate fingerprint" }
    return payload
}
