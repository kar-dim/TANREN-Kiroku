package gr.dkaratzas.tanrenkiroku.data

import android.content.Context
import gr.dkaratzas.tanrenkiroku.data.model.CustomExercise
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutSet
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

internal val WORKOUT_FILE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}\\.json")
internal const val CUSTOM_EXERCISES_FILENAME = "custom_exercises.json"
private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

// Class responsible for loading and saving workout from/into disk
class WorkoutRepository(private val context: Context) {

    val workoutsDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    val customExercisesFile: File get() = File(workoutsDir, CUSTOM_EXERCISES_FILENAME)

    fun workoutFiles(): Array<File> = workoutsDir.listFiles { f -> f.name.matches(WORKOUT_FILE_REGEX) } ?: emptyArray()

    fun deleteAllWorkoutFiles() = workoutFiles().forEach { it.delete() }

    fun writeWorkoutFile(name: String, data: ByteArray) = File(workoutsDir, name).writeBytes(data)

    private fun File.decodeWorkout(): WorkoutDay? = runCatching { json.decodeFromString<WorkoutDay>(readText()) }.getOrNull()

    fun loadWorkout(date: LocalDate): WorkoutDay? {
        val file = File(workoutsDir, "$date.json")
        return if (file.exists()) file.decodeWorkout() else null
    }

    fun saveWorkout(workout: WorkoutDay) {
        val clean = workout.copy(entries = workout.entries.filter { it.sets.isNotEmpty() })
        if (clean.entries.isEmpty()) {
            deleteWorkout(LocalDate.parse(workout.date))
            return
        }
        workoutsDir.mkdirs()
        File(workoutsDir, "${workout.date}.json").writeText(json.encodeToString(clean))
    }

    fun deleteWorkout(date: LocalDate) {
        File(workoutsDir, "$date.json").delete()
    }

    fun loadAllWorkouts(): List<WorkoutDay> =
        workoutFiles()
            .sortedByDescending { it.name }
            .mapNotNull { it.decodeWorkout() }

    fun loadWorkoutDates(): Set<LocalDate> =
        workoutFiles().mapNotNull { f ->
            runCatching { LocalDate.parse(f.nameWithoutExtension) }.getOrNull()
        }.toSet()

    fun loadCustomExercises(): List<CustomExercise> {
        val file = File(workoutsDir, CUSTOM_EXERCISES_FILENAME)
        if (!file.exists())
            return emptyList()
        return runCatching { json.decodeFromString<List<CustomExercise>>(file.readText()) }.getOrElse { emptyList() }
    }

    fun saveCustomExercises(exercises: List<CustomExercise>) {
        val file = File(workoutsDir, CUSTOM_EXERCISES_FILENAME)
        if (exercises.isEmpty()) {
            file.delete()
            return
        }
        workoutsDir.mkdirs()
        file.writeText(json.encodeToString(exercises))
    }

    fun removeExerciseFromAllWorkouts(exerciseId: String) {
        workoutFiles().forEach { f ->
            val day = f.decodeWorkout() ?: return@forEach
            val updated = day.copy(entries = day.entries.filter { it.exerciseId != exerciseId })
            if (updated.entries.isEmpty())
                f.delete()
            else
                f.writeText(json.encodeToString(updated))
        }
    }

    fun lastSetForExercise(exerciseId: String, before: LocalDate): WorkoutSet? =
        workoutFiles()
            .mapNotNull { f -> runCatching { LocalDate.parse(f.nameWithoutExtension) to f }.getOrNull() }
            .filter { (date, _) -> date < before }
            .sortedByDescending { (date, _) -> date }
            .firstNotNullOfOrNull { (_, f) -> f.decodeWorkout()?.entries?.find { it.exerciseId == exerciseId }?.sets?.lastOrNull() }
}
