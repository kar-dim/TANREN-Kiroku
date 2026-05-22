package gr.dkaratzas.tanrenkiroku.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gr.dkaratzas.tanrenkiroku.data.ThemeMode
import gr.dkaratzas.tanrenkiroku.data.UnitSystem
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

// Composables for settings screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                val ok = runCatching { viewModel.exportBackupToUri(uri) }.isSuccess
                isExporting = false
                if (ok) successMessage = "Backup saved successfully."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!isImporting) pendingImportUri = null },
            title = { Text("Replace All Workouts?") },
            text = { Text("This will delete all current workout data and replace it with the contents of the selected backup. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        isImporting = true
                        scope.launch {
                            val ok = runCatching { viewModel.importBackup(uri) }.isSuccess
                            isImporting = false
                            pendingImportUri = null
                            if (ok) successMessage = "Backup restored successfully."
                        }
                    },
                    enabled = !isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isImporting) SmallSpinner() else Text("Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }, enabled = !isImporting) { Text("Cancel") }
            }
        )
    }

    successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { successMessage = null },
            title = { Text("Done") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { successMessage = null }) { Text("OK") }
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete All Workouts?",
            text = "This will permanently delete all workout history from this device. This cannot be undone.",
            confirmLabel = "Delete All",
            onConfirm = { viewModel.deleteAllWorkouts(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { BackButton(onClick = onBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection("Theme") {
                ThemeSelector(current = viewModel.themeMode, onSelect = viewModel::setThemeMode)
            }

            HorizontalDivider()

            SettingsSection("Units") {
                UnitSelector(current = viewModel.unitSystem, onSelect = viewModel::setUnitSystem)
            }

            HorizontalDivider()

            SettingsSection("Navigation") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Skip empty days", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Arrows jump to the previous / next workout day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.skipEmptyWorkouts,
                        onCheckedChange = viewModel::setSkipEmptyWorkouts
                    )
                }
            }

            HorizontalDivider()

            SettingsSection("Backup") {
                OutlinedButton(
                    onClick = { exportLauncher.launch("tanren_backup_${LocalDate.now()}.zip") },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) SmallSpinner()
                    else Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isExporting) "Saving..." else "Export Backup")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Backup")
                }
            }

            HorizontalDivider()

            SettingsSection("Custom Exercises") {
                if (viewModel.customExercises.isEmpty()) {
                    Text(
                        "No custom exercises yet. Tap + in the exercise picker to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    viewModel.customExercises.forEach { exercise ->
                        CustomExerciseDeleteRow(
                            name = exercise.name,
                            group = exercise.pickerGroup,
                            workoutCount = viewModel.workoutCountForExercise(exercise.id),
                            onDelete = { viewModel.deleteCustomExercise(exercise.id) }
                        )
                    }
                }
            }

            HorizontalDivider()

            SettingsSection("Data") {
                DestructiveOutlinedButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All Workout History")
                }
            }

            HorizontalDivider()

            SettingsSection("Workout Files Location") {
                Text(
                    text = viewModel.workoutsDir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Use the Sync button on the home screen to transfer to TANREN Metsuke.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(mode) },
                selected = current == mode,
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun UnitSelector(current: UnitSystem, onSelect: (UnitSystem) -> Unit) {
    val options = listOf(UnitSystem.KG to "kg", UnitSystem.LB to "lb")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (unit, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(unit) },
                selected = current == unit,
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun CustomExerciseDeleteRow(
    name: String,
    group: String,
    workoutCount: Int,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        ConfirmDialog(
            title = "Remove \"$name\"?",
            text = if (workoutCount == 0) "This exercise has no recorded sets."
                   else "This will remove \"$name\" and delete its sets from $workoutCount workout${if (workoutCount == 1) "" else "s"}. This cannot be undone.",
            confirmLabel = "Remove",
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false }
        )
    }

    DestructiveOutlinedButton(onClick = { showConfirm = true }) {
        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f))
        Text(group, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DestructiveOutlinedButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        content = content
    )
}
