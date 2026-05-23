package gr.dkaratzas.tanrenkiroku.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.dkaratzas.tanrenkiroku.data.isSyncableFile
import gr.dkaratzas.tanrenkiroku.data.EXERCISE_CATALOG
import gr.dkaratzas.tanrenkiroku.data.MuscleGroup
import gr.dkaratzas.tanrenkiroku.data.Exercise
import gr.dkaratzas.tanrenkiroku.data.PreferencesManager
import gr.dkaratzas.tanrenkiroku.data.ThemeMode
import gr.dkaratzas.tanrenkiroku.data.UnitSystem
import gr.dkaratzas.tanrenkiroku.data.WorkoutRepository
import gr.dkaratzas.tanrenkiroku.data.exerciseDisplayName
import gr.dkaratzas.tanrenkiroku.data.model.CustomExercise
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutEntry
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutSet
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import java.time.LocalDate
import kotlin.math.roundToLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ViewModel for workout operations
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WorkoutRepository(application)
    private val prefs = PreferencesManager(application)

    val workoutsDir: String get() = repo.workoutsDir.absolutePath

    var selectedDate: LocalDate by mutableStateOf(LocalDate.now())
        private set

    var workout: WorkoutDay? by mutableStateOf(null)
        private set

    private var _themeMode by mutableStateOf(prefs.themeMode)
    val themeMode: ThemeMode get() = _themeMode

    private var _unitSystem by mutableStateOf(prefs.unitSystem)
    val unitSystem: UnitSystem get() = _unitSystem

    private var _skipEmptyWorkouts by mutableStateOf(prefs.skipEmptyWorkouts)
    val skipEmptyWorkouts: Boolean get() = _skipEmptyWorkouts

    var workoutDates by mutableStateOf<Set<LocalDate>>(emptySet())
        private set

    var allWorkouts by mutableStateOf<List<WorkoutDay>>(emptyList())
        private set

    var customExercises by mutableStateOf<List<CustomExercise>>(emptyList())
        private set

    val effectiveCatalog: List<MuscleGroup> by derivedStateOf {
        if (customExercises.isEmpty()) EXERCISE_CATALOG
        else {
            val byGroup = customExercises.groupBy { it.pickerGroup }
            val withCustom = EXERCISE_CATALOG.map { group ->
                val extras = byGroup[group.name]?.map { Exercise(it.id, it.name) } ?: emptyList()
                if (extras.isEmpty()) group else group.copy(exercises = group.exercises + extras)
            }
            withCustom
        }
    }

    fun displayName(id: String): String = customExercises.find { it.id == id }?.name ?: exerciseDisplayName(id)

    val weightUnit: String get() = if (_unitSystem == UnitSystem.LB) "lb" else "kg"

    fun toDisplayWeight(kg: Double, withUnit: Boolean = true): String {
        val value = round2(if (_unitSystem == UnitSystem.LB) kg * LB_PER_KG else kg)
        val num = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        return if (withUnit) "$num $weightUnit" else num
    }

    fun toStorageKg(displayValue: Double): Double =
        if (_unitSystem == UnitSystem.LB) round2(displayValue * KG_PER_LB) else displayValue

    fun setUnitSystem(system: UnitSystem) {
        prefs.unitSystem = system
        _unitSystem = system
    }

    fun setSkipEmptyWorkouts(value: Boolean) {
        prefs.skipEmptyWorkouts = value
        _skipEmptyWorkouts = value
    }

    init {
        viewModelScope.launch { reloadAll() }
        loadWorkout()
    }

    private suspend fun reloadAll() {
        val (dates, workouts, custom) = withContext(Dispatchers.IO) {
            Triple(repo.loadWorkoutDates(), repo.loadAllWorkouts(), repo.loadCustomExercises())
        }
        workoutDates = dates
        allWorkouts = workouts
        customExercises = custom
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode = mode
    }

    fun navigateDate(delta: Int) {
        if (_skipEmptyWorkouts) {
            val today = LocalDate.now()
            val sorted = workoutDates.sorted()
            val target = if (delta > 0) {
                val nextWorkout = sorted.firstOrNull { it > selectedDate }
                if (today > selectedDate && (nextWorkout == null || today < nextWorkout)) today else nextWorkout
            } else {
                val prevWorkout = sorted.lastOrNull { it < selectedDate }
                if (today < selectedDate && (prevWorkout == null || today > prevWorkout)) today else prevWorkout
            }
            if (target != null) { selectedDate = target; loadWorkout() }
        } else {
            selectedDate = selectedDate.plusDays(delta.toLong())
            loadWorkout()
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        loadWorkout()
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            workout = withContext(Dispatchers.IO) { repo.loadWorkout(selectedDate) }
        }
    }

    var pendingNewExerciseId by mutableStateOf<String?>(null)
        private set

    fun clearPendingNewExercise() { pendingNewExerciseId = null }

    fun addExercise(exerciseId: String) {
        val current = workout ?: WorkoutDay(selectedDate.toString())
        if (current.entries.any { it.exerciseId == exerciseId })
            return
        persist(current.copy(entries = current.entries + WorkoutEntry(exerciseId)))
        pendingNewExerciseId = exerciseId
    }

    fun addSet(exerciseId: String, reps: Int, kg: Double) {
        val current = workout ?: return
        persist(current.mapEntry(exerciseId) { it.copy(sets = it.sets + WorkoutSet(reps, kg)) })
    }

    fun updateSet(exerciseId: String, setIndex: Int, reps: Int, kg: Double) {
        val current = workout ?: return
        persist(current.mapEntry(exerciseId) { entry ->
            val sets = entry.sets.toMutableList()
            sets[setIndex] = WorkoutSet(reps, kg)
            entry.copy(sets = sets)
        })
    }

    fun deleteSet(exerciseId: String, setIndex: Int) {
        val current = workout ?: return
        val updated = current.copy(
            entries = current.entries.mapNotNull { entry ->
                if (entry.exerciseId == exerciseId) {
                    val sets = entry.sets.toMutableList().also { it.removeAt(setIndex) }
                    if (sets.isEmpty()) null else entry.copy(sets = sets)
                } else entry
            }
        )
        if (updated.entries.isEmpty()) deleteCurrentWorkout() else persist(updated)
    }

    fun deleteExercise(exerciseId: String) {
        val current = workout ?: return
        val updated = current.copy(entries = current.entries.filter { it.exerciseId != exerciseId })
        if (updated.entries.isEmpty()) deleteCurrentWorkout() else persist(updated)
    }

    fun copyWorkoutFrom(sourceDate: LocalDate) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { repo.loadWorkout(sourceDate) } ?: return@launch
            persist(source.copy(date = selectedDate.toString()))
        }
    }

    suspend fun lastSetForExercise(exerciseId: String): WorkoutSet? =
        withContext(Dispatchers.IO) { repo.lastSetForExercise(exerciseId, selectedDate) }

    fun deleteAllWorkouts() {
        workoutDates = emptySet()
        allWorkouts = emptyList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteAllWorkoutFiles() }
            workout = null
        }
    }

    fun addCustomExercise(
        name: String,
        group: String,
        primaryMuscles: List<String>,
        secondaryMuscles: List<String>
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank())
            return false
        val id = "custom_" + trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val nameTaken =
            customExercises.any { it.name.equals(trimmed, ignoreCase = true) || it.id == id } ||
            EXERCISE_CATALOG.any { g -> g.exercises.any { it.name.equals(trimmed, ignoreCase = true) } }
        if (nameTaken)
            return false
        val updated = customExercises + CustomExercise(
            id = id,
            name = trimmed,
            pickerGroup = group,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles
        )
        customExercises = updated
        viewModelScope.launch(Dispatchers.IO) { repo.saveCustomExercises(updated) }
        return true
    }

    fun workoutCountForExercise(exerciseId: String): Int = allWorkouts.count { day -> day.entries.any { it.exerciseId == exerciseId } }

    fun deleteCustomExercise(exerciseId: String) {
        customExercises = customExercises.filter { it.id != exerciseId }
        allWorkouts = allWorkouts.map { day ->
            day.copy(entries = day.entries.filter { it.exerciseId != exerciseId })
        }.filter { it.entries.isNotEmpty() }
        workoutDates = allWorkouts.map { LocalDate.parse(it.date) }.toSet()
        if (workout?.entries?.any { it.exerciseId == exerciseId } == true) {
            val updated = workout!!.copy(entries = workout!!.entries.filter { it.exerciseId != exerciseId })
            workout = if (updated.entries.isEmpty()) null else updated
        }
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeExerciseFromAllWorkouts(exerciseId)
            repo.saveCustomExercises(customExercises)
        }
    }

    suspend fun importBackup(uri: Uri) {
        withContext(Dispatchers.IO) {
            repo.deleteAllWorkoutFiles()
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && isSyncableFile(entry.name)) {
                            repo.writeWorkoutFile(entry.name, zis.readBytes())
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        reloadAll()
        workout = withContext(Dispatchers.IO) { repo.loadWorkout(selectedDate) }
    }

    suspend fun exportBackupToUri(uri: Uri) = withContext(Dispatchers.IO) {
        val files = repo.syncableFiles().sortedBy { it.name }
        getApplication<Application>().contentResolver.openOutputStream(uri)?.buffered()?.use { out ->
            ZipOutputStream(out).use { zos ->
                files.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun deleteCurrentWorkout() {
        workoutDates = workoutDates - selectedDate
        allWorkouts = allWorkouts.filter { it.date != selectedDate.toString() }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteWorkout(selectedDate) }
            workout = null
        }
    }

    private fun persist(w: WorkoutDay) {
        workout = w
        val clean = w.entries.filter { it.sets.isNotEmpty() }
        workoutDates = if (clean.isNotEmpty()) workoutDates + selectedDate else workoutDates - selectedDate
        allWorkouts = if (clean.isEmpty()) {
            allWorkouts.filter { it.date != w.date }
        } else {
            val updated = w.copy(entries = clean)
            val list = allWorkouts.toMutableList()
            val idx = list.indexOfFirst { it.date == w.date }
            if (idx >= 0) list[idx] = updated else list.add(updated)
            list.sortedByDescending { it.date }
        }
        viewModelScope.launch(Dispatchers.IO) { repo.saveWorkout(w) }
    }
}

private const val KG_PER_LB = 0.453592
private const val LB_PER_KG = 2.20462

private fun round2(v: Double) = (v * 100.0).roundToLong() / 100.0

private fun WorkoutDay.mapEntry(exerciseId: String, transform: (WorkoutEntry) -> WorkoutEntry): WorkoutDay =
    copy(entries = entries.map { if (it.exerciseId == exerciseId) transform(it) else it })
