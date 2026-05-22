package gr.dkaratzas.tanrenkiroku.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class QrPayload(val ip: String, val port: Int, val token: String, val cert: String)

@Serializable
data class FileManifestEntry(val filename: String, val modified: Long, val hash: String)

@Serializable
data class ManifestResponse(val needed: List<String>, val deleted: Int = 0)

@Serializable
data class UploadRequest(val filename: String, val content: JsonElement)
