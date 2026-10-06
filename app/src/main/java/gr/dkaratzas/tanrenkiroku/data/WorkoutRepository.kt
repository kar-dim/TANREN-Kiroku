package gr.dkaratzas.tanrenkiroku.data

import android.content.Context
import gr.dkaratzas.tanrenkiroku.data.model.CustomExercise
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate
import java.util.WeakHashMap

internal val WORKOUT_FILE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}\\.json")
internal const val CUSTOM_EXERCISES_FILENAME = "custom_exercises.json"
internal fun isSyncableFile(name: String): Boolean = name.matches(WORKOUT_FILE_REGEX) || name == CUSTOM_EXERCISES_FILENAME
private val storageJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
private val workoutDirectories = WeakHashMap<Context, File>()

// Keep one location for the application lifetime; sync must not switch to an empty fallback
// directory if external storage becomes unavailable after the logger has selected its folder.
private fun workoutDirectory(context: Context): File = synchronized(workoutDirectories) {
    val application = context.applicationContext ?: context
    workoutDirectories.getOrPut(application) { application.getExternalFilesDir(null) ?: application.filesDir }
}

internal fun validateWorkoutForStorage(workout: WorkoutDay) {
    validateSyncContent("${workout.date}.json", storageJson.parseToJsonElement(storageJson.encodeToString(workout)))
}

class WorkoutRepository internal constructor(val workoutsDir: File) {
    constructor(context: Context) : this(workoutDirectory(context))
    private val namespace = workoutsDir.storageNamespace()

    private fun mutate(key: String, supersedes: List<String> = emptyList(), action: () -> Unit): Deferred<Unit> =
        workoutStorageQueue.mutate(namespace, key, supersedes) { recoverWorkoutRestores(workoutsDir); action() }

    private fun workoutFiles(): Array<File> {
        recoverWorkoutRestores(workoutsDir)
        return workoutsDir.listFiles { f -> f.name.matches(WORKOUT_FILE_REGEX) } ?: throw IOException("Cannot read the workout folder")
    }

    private fun File.decodeWorkout(): WorkoutDay = try {
        val text = readBoundedFile(this).toString(Charsets.UTF_8)
        validateSyncContent(name, syncJson.parseToJsonElement(text))
        storageJson.decodeFromString<WorkoutDay>(text)
    } catch (e: Exception) { throw IOException("Cannot read $name: ${e.message}", e) }

    private fun readCustomExercises(): List<CustomExercise> {
        val file = File(workoutsDir, CUSTOM_EXERCISES_FILENAME)
        if (!file.exists()) return emptyList()
        val text = readBoundedFile(file).toString(Charsets.UTF_8)
        validateSyncContent(file.name, syncJson.parseToJsonElement(text))
        return storageJson.decodeFromString(text)
    }

    private fun writeCustomExercises(exercises: List<CustomExercise>) {
        val text = storageJson.encodeToString(exercises)
        validateSyncContent(CUSTOM_EXERCISES_FILENAME, syncJson.parseToJsonElement(text))
        workoutsDir.mkdirs()
        val file = File(workoutsDir, CUSTOM_EXERCISES_FILENAME)
        if (exercises.isEmpty()) Files.deleteIfExists(file.toPath())
        else atomicWrite(file, text.toByteArray(Charsets.UTF_8))
    }

    suspend fun loadWorkout(date: LocalDate): WorkoutDay? = workoutStorageQueue.read(namespace) {
        recoverWorkoutRestores(workoutsDir)
        val file = File(workoutsDir, "$date.json")
        if (file.exists()) file.decodeWorkout() else null
    }.await()

    suspend fun loadAllData(): Triple<Set<LocalDate>, List<WorkoutDay>, List<CustomExercise>> = workoutStorageQueue.read(namespace) {
        val workouts = workoutFiles().sortedByDescending { it.name }.map { it.decodeWorkout() }
            .filter { day -> day.entries.any { it.sets.isNotEmpty() } }
        Triple(workouts.map { LocalDate.parse(it.date) }.toSet(), workouts, readCustomExercises())
    }.await()

    fun saveWorkout(workout: WorkoutDay): Deferred<Unit> {
        val captured = workout.copy(entries = workout.entries.map { it.copy(sets = it.sets.toList()) })
        return mutate("workout:${captured.date}") { writeWorkout(captured) }
    }

    private fun writeWorkout(workout: WorkoutDay) {
        validateWorkoutForStorage(workout)
        val clean = workout.copy(entries = workout.entries.filter { it.sets.isNotEmpty() })
        workoutsDir.mkdirs()
        val file = File(workoutsDir, "${workout.date}.json")
        if (clean.entries.isEmpty()) Files.deleteIfExists(file.toPath())
        else atomicWrite(file, storageJson.encodeToString(clean).toByteArray(Charsets.UTF_8))
    }

    fun copyWorkout(sourceDate: LocalDate, targetDate: LocalDate): Deferred<Unit> = mutate("workout:$targetDate") {
        val source = File(workoutsDir, "$sourceDate.json")
        if (!source.exists()) throw IOException("The source workout is no longer available")
        writeWorkout(source.decodeWorkout().copy(date = targetDate.toString()))
    }

    fun deleteWorkout(date: LocalDate): Deferred<Unit> = mutate("workout:$date") {
        Files.deleteIfExists(File(workoutsDir, "$date.json").toPath()); Unit
    }

    fun deleteAllWorkoutFiles(): Deferred<Unit> = mutate("delete-all", listOf("workout:")) {
        workoutFiles().forEach { Files.delete(it.toPath()) }
    }

    fun saveCustomExercises(exercises: List<CustomExercise>): Deferred<Unit> {
        val captured = exercises.map { it.copy(primaryMuscles = it.primaryMuscles.toList(), secondaryMuscles = it.secondaryMuscles.toList()) }
        return mutate("custom") { writeCustomExercises(captured) }
    }

    fun deleteCustomExercise(exerciseId: String): Deferred<Unit> = mutate("delete-custom:$exerciseId") {
        workoutFiles().forEach { file ->
            val day = file.decodeWorkout()
            val updated = day.copy(entries = day.entries.filter { it.exerciseId != exerciseId && it.sets.isNotEmpty() })
            if (updated.entries.isEmpty()) Files.delete(file.toPath())
            else atomicWrite(file, storageJson.encodeToString(updated).toByteArray(Charsets.UTF_8))
        }
        writeCustomExercises(readCustomExercises().filter { it.id != exerciseId })
    }

    fun restoreBackup(source: File): Deferred<Unit> {
        val snapshot = SyncSnapshot.capture(source)
        return mutate("restore", listOf("")) {
            restoreWorkoutSnapshot(snapshot, workoutsDir)
        }
    }

    internal suspend fun captureSnapshot(): SyncSnapshot = captureWorkoutSnapshot(workoutsDir)
    suspend fun retryFailedWrites() { workoutStorageQueue.retry(namespace).await() }
}
