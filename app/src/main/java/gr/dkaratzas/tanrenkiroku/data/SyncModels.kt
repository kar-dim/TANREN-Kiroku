package gr.dkaratzas.tanrenkiroku.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class QrPayload(val ip: String, val port: Int, val token: String, val cert: String, val protocolVersion: Int? = null)

@Serializable
data class FileManifestEntry(val filename: String, val modified: Long, val hash: String)

@Serializable
data class ManifestRequest(val protocolVersion: Int, val complete: Boolean, val files: List<FileManifestEntry>)

@Serializable
data class ManifestResponse(val sessionId: String, val needed: List<String>, val deleted: Int = 0)

@Serializable
data class PingResponse(val ok: Boolean, val protocolVersion: Int)

@Serializable
data class SyncAcknowledgement(val ok: Boolean)

@Serializable
data class UploadRequest(val sessionId: String, val filename: String, val content: JsonElement)

@Serializable
data class CompletionRequest(val sessionId: String)

data class SyncResult(val uploaded: Int, val deleted: Int)
