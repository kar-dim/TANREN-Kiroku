package gr.dkaratzas.tanrenkiroku.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.dkaratzas.tanrenkiroku.data.EXERCISE_CATALOG
import gr.dkaratzas.tanrenkiroku.data.Exercise
import gr.dkaratzas.tanrenkiroku.data.MuscleGroup
import gr.dkaratzas.tanrenkiroku.data.PreferencesManager
import gr.dkaratzas.tanrenkiroku.data.ThemeMode
import gr.dkaratzas.tanrenkiroku.data.UnitSystem
import gr.dkaratzas.tanrenkiroku.data.WorkoutRepository
import gr.dkaratzas.tanrenkiroku.data.exerciseDisplayName
import gr.dkaratzas.tanrenkiroku.data.WorkoutDateGuard
import gr.dkaratzas.tanrenkiroku.data.extractWorkoutBackup
import gr.dkaratzas.tanrenkiroku.data.exportWorkoutBackup
import gr.dkaratzas.tanrenkiroku.data.model.CustomExercise
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutEntry
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.math.roundToLong

// ViewModel for workout operations
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = WorkoutRepository(application)
    private val prefs = PreferencesManager(application)

    val workoutsDir: String get() = repo.workoutsDir.absolutePath

    var selectedDate: LocalDate by mutableStateOf(LocalDate.now())
        private set

    var workout: WorkoutDay? by mutableStateOf(null)
        private set

    var isWorkoutLoading by mutableStateOf(true)
        private set
    private var workoutLoadSucceeded by mutableStateOf(false)
    val canEditWorkout: Boolean get() = !isWorkoutLoading && workoutLoadSucceeded
    var storageError by mutableStateOf<String?>(null)
        private set
    private val dateGuard = WorkoutDateGuard(selectedDate)
    private var loadJob: Job? = null
    private var dataRevision = 0L
    var pendingNewExerciseId by mutableStateOf<String?>(null)
        private set

    fun dismissStorageError() { storageError = null }

    fun retryStorage() {
        viewModelScope.launch {
            try {
                repo.retryFailedWrites()
                storageError = null
                reloadAll()
                loadWorkout()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { storageError = "Could not save or load workout data: ${e.message}" }
        }
    }

    private fun watchMutation(task: Deferred<Unit>) {
        dataRevision++
        viewModelScope.launch {
            try { task.await(); reloadAll() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { storageError = "Could not save workout data: ${e.message}. Retry saving before syncing or backing up." }
        }
    }

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
        val num = if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.001) {
            kotlin.math.round(value).toLong().toString()
        } else {
            "%.2f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
        }
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
        viewModelScope.launch {
            try { reloadAll() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { storageError = "Could not load workout data: ${e.message}" }
        }
        loadWorkout()
    }

    private suspend fun reloadAll() {
        val revision = dataRevision
        val (dates, workouts, custom) = repo.loadAllData()
        if (revision != dataRevision) return
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
        loadJob?.cancel()
        val ticket = dateGuard.begin(selectedDate)
        workout = null
        pendingNewExerciseId = null
        workoutLoadSucceeded = false
        isWorkoutLoading = true
        loadJob = viewModelScope.launch {
            try {
                val loaded = repo.loadWorkout(ticket.date)
                if (dateGuard.accepts(ticket)) {
                    workout = loaded
                    workoutLoadSucceeded = true
                    isWorkoutLoading = false
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (dateGuard.accepts(ticket)) {
                    isWorkoutLoading = false
                    storageError = "Could not load ${ticket.date}: ${e.message}"
                }
            }
        }
    }

    fun clearPendingNewExercise() { pendingNewExerciseId = null }

    fun addExercise(exerciseId: String) {
        if (!canEditWorkout) return
        val current = workout ?: WorkoutDay(selectedDate.toString())
        if (current.entries.any { it.exerciseId == exerciseId })
            return
        persist(current.copy(entries = current.entries + WorkoutEntry(exerciseId)))
        pendingNewExerciseId = exerciseId
    }

    fun addSet(exerciseId: String, reps: Int, kg: Double) {
        if (!canEditWorkout || !validSet(reps, kg)) return
        val current = workout ?: return
        persist(current.mapEntry(exerciseId) { it.copy(sets = it.sets + WorkoutSet(reps, kg)) })
    }

    fun updateSet(exerciseId: String, setIndex: Int, reps: Int, kg: Double) {
        if (!canEditWorkout || !validSet(reps, kg)) return
        val current = workout ?: return
        persist(current.mapEntry(exerciseId) { entry ->
            if (setIndex !in entry.sets.indices) return@mapEntry entry
            val sets = entry.sets.toMutableList()
            sets[setIndex] = WorkoutSet(reps, kg)
            entry.copy(sets = sets)
        })
    }

    fun deleteSet(exerciseId: String, setIndex: Int) {
        if (!canEditWorkout) return
        val current = workout ?: return
        val updated = current.copy(
            entries = current.entries.mapNotNull { entry ->
                if (entry.exerciseId == exerciseId) {
                    if (setIndex !in entry.sets.indices) return@mapNotNull entry
                    val sets = entry.sets.toMutableList().also { it.removeAt(setIndex) }
                    if (sets.isEmpty()) null else entry.copy(sets = sets)
                } else entry
            }
        )
        if (updated.entries.isEmpty()) deleteCurrentWorkout() else persist(updated)
    }

    fun deleteExercise(exerciseId: String) {
        if (!canEditWorkout) return
        val current = workout ?: return
        val updated = current.copy(entries = current.entries.filter { it.exerciseId != exerciseId })
        if (updated.entries.isEmpty()) deleteCurrentWorkout() else persist(updated)
    }

    fun copyWorkoutFrom(sourceDate: LocalDate) {
        if (!canEditWorkout || workout?.entries?.any { it.sets.isNotEmpty() } == true) return
        loadJob?.cancel()
        val ticket = dateGuard.begin(selectedDate)
        isWorkoutLoading = true
        val copy = repo.copyWorkout(sourceDate, ticket.date)
        watchMutation(copy)
        loadJob = viewModelScope.launch {
            try {
                copy.await()
                val copied = repo.loadWorkout(ticket.date)
                if (dateGuard.accepts(ticket)) {
                    workout = copied
                    workoutLoadSucceeded = true
                    isWorkoutLoading = false
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (dateGuard.accepts(ticket)) {
                    isWorkoutLoading = false
                    storageError = "Could not copy workout: ${e.message}"
                }
            }
        }
    }

    fun lastSetForExercise(exerciseId: String): WorkoutSet? =
        allWorkouts
            .filter { runCatching { LocalDate.parse(it.date) < selectedDate }.getOrDefault(false) }
            .firstNotNullOfOrNull { day ->
                day.entries.find { it.exerciseId == exerciseId }?.sets?.lastOrNull()
            }

    fun cleanEmptyExercises() {
        if (!canEditWorkout) return
        val current = workout ?: return
        val cleaned = current.entries.filter { it.sets.isNotEmpty() }
        if (cleaned.size != current.entries.size) {
            if (cleaned.isEmpty()) {
                deleteCurrentWorkout()
            } else {
                persist(current.copy(entries = cleaned))
            }
        }
    }

    fun deleteAllWorkouts() {
        dateGuard.invalidate()
        loadJob?.cancel()
        workoutDates = emptySet()
        allWorkouts = emptyList()
        workout = null
        isWorkoutLoading = false
        workoutLoadSucceeded = true
        watchMutation(repo.deleteAllWorkoutFiles())
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
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val baseId = if (slug.isNotBlank()) "custom_$slug" else "custom_${System.currentTimeMillis()}"
        var id = baseId
        var counter = 1
        while (customExercises.any { it.id == id } || EXERCISE_CATALOG.any { g -> g.exercises.any { it.id == id } }) {
            id = "${baseId}_$counter"
            counter++
        }
        val nameTaken =
            customExercises.any { it.name.equals(trimmed, ignoreCase = true) } ||
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
        watchMutation(repo.saveCustomExercises(updated))
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
        watchMutation(repo.deleteCustomExercise(exerciseId))
        if (isWorkoutLoading) loadWorkout()
    }

    suspend fun importBackup(uri: Uri) {
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val tempDir = File(context.cacheDir, "backup_restore_${System.currentTimeMillis()}")
            if (!tempDir.mkdirs()) {
                throw IOException("Failed to create temporary restore directory")
            }
            try {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open backup file")
                val coroutine = currentCoroutineContext()
                input.use { extractWorkoutBackup(it, tempDir) { coroutine.ensureActive() } }
                coroutine.ensureActive()
                // Keep the staging files alive until the queued replacement/rollback has finished.
                withContext(NonCancellable) { repo.restoreBackup(tempDir).await() }
            } finally {
                tempDir.deleteRecursively()
            }
        }
        dataRevision++
        reloadAll()
        loadWorkout()
    }

    suspend fun exportBackupToUri(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = repo.captureSnapshot()
        exportWorkoutBackup(snapshot, getApplication<Application>().contentResolver.openOutputStream(uri))
    }

    private fun deleteCurrentWorkout() {
        val date = workout?.date?.let(LocalDate::parse) ?: selectedDate
        dateGuard.invalidate()
        workoutDates = workoutDates - date
        allWorkouts = allWorkouts.filter { it.date != date.toString() }
        workout = null
        watchMutation(repo.deleteWorkout(date))
    }

    private fun persist(w: WorkoutDay) {
        val date = LocalDate.parse(w.date)
        if (!canEditWorkout || date != selectedDate) return
        dateGuard.invalidate()
        workout = w
        val clean = w.entries.filter { it.sets.isNotEmpty() }
        workoutDates = if (clean.isNotEmpty()) workoutDates + date else workoutDates - date
        allWorkouts = if (clean.isEmpty()) {
            allWorkouts.filter { it.date != w.date }
        } else {
            val updated = w.copy(entries = clean)
            val list = allWorkouts.toMutableList()
            val idx = list.indexOfFirst { it.date == w.date }
            if (idx >= 0) list[idx] = updated else list.add(updated)
            list.sortedByDescending { it.date }
        }
        watchMutation(repo.saveWorkout(w))
    }

    private fun validSet(reps: Int, kg: Double): Boolean {
        if (reps > 0 && kg.isFinite() && kg >= 0 && (reps * kg).isFinite()) return true
        storageError = "Use positive reps and a finite, nonnegative weight. Zero weight is valid."
        return false
    }
}

private const val KG_PER_LB = 0.453592
private const val LB_PER_KG = 2.20462

private fun round2(v: Double) = (v * 100.0).roundToLong() / 100.0

private fun WorkoutDay.mapEntry(exerciseId: String, transform: (WorkoutEntry) -> WorkoutEntry): WorkoutDay =
    copy(entries = entries.map { if (it.exerciseId == exerciseId) transform(it) else it })
