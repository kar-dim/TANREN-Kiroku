package gr.dkaratzas.tanrenkiroku.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutEntry
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutSet
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.WorkoutViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

private data class SetDialogState(
    val exerciseId: String,
    val setIndex: Int?,
    val reps: String,
    val kg: String
)

private fun newSetDialog(
    exerciseId: String,
    prev: WorkoutSet?,
    toDisplayKg: (Double) -> String
): SetDialogState = SetDialogState(
    exerciseId = exerciseId,
    setIndex = null,
    reps = prev?.reps?.toString() ?: "8",
    kg = prev?.kg?.let(toDisplayKg) ?: "1"
)

// Composables for the Main (Home) screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WorkoutViewModel,
    onAddExercise: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onInfo: () -> Unit
) {
    var setDialog by remember { mutableStateOf<SetDialogState?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.pendingNewExerciseId) {
        val exerciseId = viewModel.pendingNewExerciseId ?: return@LaunchedEffect
        val prev = viewModel.lastSetForExercise(exerciseId)
        setDialog = newSetDialog(exerciseId, prev) { viewModel.toDisplayWeight(it, withUnit = false) }
        viewModel.clearPendingNewExercise()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TANREN Kiroku") },
                actions = {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExercise) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val skip = viewModel.skipEmptyWorkouts
            val today = LocalDate.now()
            DateNavigator(
                date = viewModel.selectedDate,
                canGoPrev = !skip || viewModel.workoutDates.any { it < viewModel.selectedDate } || today < viewModel.selectedDate,
                canGoNext = !skip || viewModel.workoutDates.any { it > viewModel.selectedDate } || today > viewModel.selectedDate,
                onPrevious = { viewModel.navigateDate(-1) },
                onNext = { viewModel.navigateDate(1) },
                onPickDate = { showDatePicker = true }
            )
            HorizontalDivider()

            val workout = viewModel.workout
            if (workout == null || workout.entries.isEmpty()) {
                EmptyWorkoutPlaceholder(
                    onCopyWorkout = if (viewModel.allWorkouts.isNotEmpty()) {
                        { showCopyPicker = true }
                    } else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = workout.entries,
                        key = { _, entry -> entry.exerciseId }
                    ) { _, entry ->
                        ExerciseCard(
                            entry = entry,
                            displayName = { id -> viewModel.displayName(id) },
                            formatWeight = { kg -> viewModel.toDisplayWeight(kg) },
                            onAddSet = {
                                val todayLast = entry.sets.lastOrNull()
                                if (todayLast != null) {
                                    setDialog = newSetDialog(entry.exerciseId, todayLast) { viewModel.toDisplayWeight(it, withUnit = false) }
                                } else {
                                    scope.launch {
                                        val prev = viewModel.lastSetForExercise(entry.exerciseId)
                                        setDialog = newSetDialog(entry.exerciseId, prev) { viewModel.toDisplayWeight(it, withUnit = false) }
                                    }
                                }
                            },
                            onEditSet = { idx, set ->
                                setDialog = SetDialogState(
                                    exerciseId = entry.exerciseId,
                                    setIndex = idx,
                                    reps = set.reps.toString(),
                                    kg = viewModel.toDisplayWeight(set.kg, withUnit = false)
                                )
                            },
                            onDeleteExercise = { viewModel.deleteExercise(entry.exerciseId) }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        WorkoutCalendarDialog(
            selectedDate = viewModel.selectedDate,
            workoutDates = viewModel.workoutDates,
            allWorkouts = viewModel.allWorkouts,
            formatWeight = { viewModel.toDisplayWeight(it) },
            displayName = { viewModel.displayName(it) },
            onDateSelected = { viewModel.selectDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showCopyPicker) {
        WorkoutHistoryDialog(
            workouts = viewModel.allWorkouts,
            formatWeight = { viewModel.toDisplayWeight(it) },
            displayName = { viewModel.displayName(it) },
            onSelect = { viewModel.copyWorkoutFrom(it); showCopyPicker = false },
            onDismiss = { showCopyPicker = false }
        )
    }

    // Set add/edit dialog
    setDialog?.let { state ->
        SetDialog(
            title = if (state.setIndex == null)
                "Add Set - ${viewModel.displayName(state.exerciseId)}"
            else
                "Edit Set ${state.setIndex + 1} - ${viewModel.displayName(state.exerciseId)}",
            initialReps = state.reps,
            initialKg = state.kg,
            weightUnit = viewModel.weightUnit,
            isEdit = state.setIndex != null,
            onConfirm = { reps, displayValue ->
                val kg = viewModel.toStorageKg(displayValue)
                if (state.setIndex == null) {
                    viewModel.addSet(state.exerciseId, reps, kg)
                } else {
                    viewModel.updateSet(state.exerciseId, state.setIndex, reps, kg)
                }
                setDialog = null
            },
            onDelete = if (state.setIndex != null) {
                { viewModel.deleteSet(state.exerciseId, state.setIndex); setDialog = null }
            } else null,
            onDismiss = { setDialog = null }
        )
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious, enabled = canGoPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onPickDate)
        ) {
            Text(
                text = if (date == LocalDate.now()) "Today" else date.format(DATE_FORMATTER),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Pick date",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun ExerciseCard(
    entry: WorkoutEntry,
    displayName: (String) -> String,
    formatWeight: (Double) -> String,
    onAddSet: () -> Unit,
    onEditSet: (Int, WorkoutSet) -> Unit,
    onDeleteExercise: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Remove Exercise?",
            text = "Remove ${displayName(entry.exerciseId)} and all its sets from this workout?",
            confirmLabel = "Remove",
            onConfirm = { onDeleteExercise(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName(entry.exerciseId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onAddSet,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("Set", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove exercise",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (entry.sets.isEmpty()) {
                Text(
                    "No sets yet - tap + Set to add one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
                entry.sets.forEachIndexed { idx, set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditSet(idx, set) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = "${set.reps} reps",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = formatWeight(set.kg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetDialog(
    title: String,
    initialReps: String,
    initialKg: String,
    weightUnit: String,
    isEdit: Boolean,
    onConfirm: (reps: Int, kg: Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var repsText by remember(initialReps) { mutableStateOf(initialReps) }
    var kgText by remember(initialKg) { mutableStateOf(initialKg) }
    var repsError by remember { mutableStateOf(false) }
    var kgError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it; repsError = false },
                    label = { Text("Reps") },
                    isError = repsError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kgText,
                    onValueChange = { kgText = it; kgError = false },
                    label = { Text(weightUnit) },
                    isError = kgError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    val reps = repsText.trim().toIntOrNull()?.takeIf { it > 0 }
                    val kg = kgText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
                    repsError = reps == null
                    kgError = kg == null
                    if (reps != null && kg != null) onConfirm(reps, kg)
                }) {
                    Text(if (isEdit) "Save" else "Add")
                }
            }
        }
    )
}

@Composable
private fun EmptyWorkoutPlaceholder(onCopyWorkout: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "No workout logged for this day.\nTap + to add exercises.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onCopyWorkout != null) {
                TextButton(onClick = onCopyWorkout) {
                    Text("Copy a previous workout")
                }
            }
        }
    }
}
