package gr.dkaratzas.tanrenkiroku.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate

// Snapshot capture and local writes share this gate, so sync never reads a partial write.
internal val workoutStorageGate = Any()
internal val syncJson = Json { ignoreUnknownKeys = true }
internal const val SYNC_PROTOCOL_VERSION = 2

internal data class SnapshotFile(val metadata: FileManifestEntry, val content: JsonElement)

internal class SyncSnapshot private constructor(val files: Map<String, SnapshotFile>) {
    val manifest: ManifestRequest
        get() = ManifestRequest(SYNC_PROTOCOL_VERSION, true, files.values.map { it.metadata })

    companion object {
        fun capture(directory: File): SyncSnapshot = synchronized(workoutStorageGate) {
            recoverWorkoutRestores(directory)
            // A failed listing must never be interpreted as an authoritative empty dataset.
            val candidates = directory.listFiles { file -> isSyncableFile(file.name) }
                ?: throw IOException("Cannot read the workout folder. Sync stopped before sending a manifest.")
            val files = candidates.sortedBy { it.name }.associate { file ->
                try {
                    if (!file.isFile) throw IOException("Expected a regular file")
                    val content = syncJson.parseToJsonElement(readBoundedFile(file).toString(Charsets.UTF_8))
                    validateSyncContent(file.name, content)
                    // Hash the exact compact UTF-8 representation also serialized inside the upload.
                    val bytes = syncJson.encodeToString(content).toByteArray(Charsets.UTF_8)
                    file.name to SnapshotFile(
                        FileManifestEntry(file.name, (file.lastModified() / 1000).coerceAtLeast(0), sha256Hex(bytes)),
                        content
                    )
                } catch (e: Exception) {
                    throw IOException("Cannot sync ${file.name}: ${e.message}. Repair or restore this file and retry.", e)
                }
            }
            SyncSnapshot(files)
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

// Match Metsuke's workout/custom exercise validation before declaring the manifest complete.
internal fun validateSyncContent(filename: String, content: JsonElement) {
    if (filename == CUSTOM_EXERCISES_FILENAME) {
        val ids = mutableSetOf<String>()
        for (element in content.array("Custom exercises")) {
            val exercise = element.obj("Custom exercise")
            val id = exercise["id"].string("Exercise ID")
            require(id.isNotBlank() && ids.add(id)) { "Custom exercise IDs must be nonblank and unique" }
            require(exercise["name"].string("Exercise name").isNotBlank()) { "Exercise name is required" }
            val primary = (exercise["primaryMuscles"] ?: JsonArray(emptyList())).muscles()
            val secondary = (exercise["secondaryMuscles"] ?: JsonArray(emptyList())).muscles()
            require(primary.isNotEmpty()) { "Custom exercises need primary muscles" }
            require(primary.intersect(secondary).isEmpty()) { "A muscle cannot be both primary and secondary" }
        }
        return
    }
    require(filename.matches(WORKOUT_FILE_REGEX)) { "Invalid workout filename" }
    val date = LocalDate.parse(filename.removeSuffix(".json"))
    require(date.year in 1..9999 && date != LocalDate.of(1, 1, 1)) { "Invalid workout date" }
    val workout = content.obj("Workout")
    require(workout["date"].string("Workout date") == date.toString()) { "Workout date must match its filename" }
    var volume = 0.0
    for (element in workout["entries"].array("Workout entries")) {
        val entry = element.obj("Workout entry")
        require(entry["exerciseId"].string("Exercise ID").isNotBlank()) { "Exercise ID is required" }
        for (setElement in entry["sets"].array("Sets")) {
            val set = setElement.obj("Set")
            val reps = (set["reps"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            val kg = (set["kg"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
            require(reps != null && reps > 0) { "Sets require positive integer reps" }
            require(kg != null && kg.isFinite() && kg >= 0) { "Sets require a finite, nonnegative weight" }
            volume += reps * kg
            require(volume.isFinite()) { "Workout volume is too large" }
        }
    }
}

private fun JsonElement?.obj(label: String) = this as? JsonObject ?: error("$label must be an object")
private fun JsonElement?.array(label: String) = this as? JsonArray ?: error("$label must be an array")
private fun JsonElement?.string(label: String) =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("$label must be a string")

private val syncMuscles = setOf(
    "shoulders", "biceps", "chest", "forearms", "core", "quads", "shins", "traps", "back",
    "triceps", "glutes", "hamstrings", "calves"
)

private fun JsonElement.muscles(): Set<String> = array("Muscles").map {
    it.string("Muscle").lowercase(java.util.Locale.ROOT).also { name ->
        require(name in syncMuscles) { "Unknown muscle: $name" }
    }
}.toSet()
