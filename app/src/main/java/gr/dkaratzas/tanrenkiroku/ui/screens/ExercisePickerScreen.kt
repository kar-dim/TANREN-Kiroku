package gr.dkaratzas.tanrenkiroku.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gr.dkaratzas.tanrenkiroku.data.EXERCISE_CATALOG
import gr.dkaratzas.tanrenkiroku.data.MuscleGroup
import gr.dkaratzas.tanrenkiroku.ui.viewmodel.WorkoutViewModel

// Composables fore the Exercise Picker screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {
    val addedIds = viewModel.workout?.entries?.map { it.exerciseId }?.toSet() ?: emptySet()
    var selectedGroup by remember { mutableStateOf<MuscleGroup?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedGroup?.name ?: "Add Exercise") },
                navigationIcon = {
                    BackButton(onClick = { if (selectedGroup != null) selectedGroup = null else onBack() })
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create custom exercise")
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedGroup,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "picker"
        ) { group ->
            if (group == null) {
                MuscleGroupGrid(
                    catalog = viewModel.effectiveCatalog,
                    addedIds = addedIds,
                    modifier = Modifier.padding(padding),
                    onGroupSelected = { selectedGroup = it }
                )
            } else {
                ExerciseList(
                    group = group,
                    addedIds = addedIds,
                    modifier = Modifier.padding(padding),
                    onExercisePicked = { id ->
                        viewModel.addExercise(id)
                        onBack()
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateCustomExerciseDialog(
            onConfirm = { name, group, primary, secondary ->
                viewModel.addCustomExercise(name, group, primary, secondary)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun MuscleGroupGrid(
    catalog: List<MuscleGroup>,
    addedIds: Set<String>,
    modifier: Modifier = Modifier,
    onGroupSelected: (MuscleGroup) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(catalog, key = { it.name }) { group ->
            val totalCount = group.exercises.size
            val addedCount = group.exercises.count { it.id in addedIds }
            Card(
                onClick = { onGroupSelected(group) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (addedCount > 0) "$addedCount / $totalCount added"
                               else "$totalCount exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (addedCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseList(
    group: MuscleGroup,
    addedIds: Set<String>,
    modifier: Modifier = Modifier,
    onExercisePicked: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(group.exercises, key = { it.id }) { exercise ->
            val alreadyAdded = exercise.id in addedIds
            ListItem(
                headlineContent = {
                    Text(
                        exercise.name,
                        color = if (alreadyAdded) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingContent = if (alreadyAdded) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Already added",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                } else null,
                modifier = Modifier.clickable(enabled = !alreadyAdded) {
                    onExercisePicked(exercise.id)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCustomExerciseDialog(
    onConfirm: (name: String, group: String, primary: List<String>, secondary: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = EXERCISE_CATALOG.map { it.name }
    var name by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(groups.first()) }
    var primaryMuscles by remember { mutableStateOf(emptySet<String>()) }
    var secondaryMuscles by remember { mutableStateOf(emptySet<String>()) }
    var nameError by remember { mutableStateOf(false) }
    // we use several checks here for not allowing a secondary exercise to be primary (and vice versa), plus the "category"
    //muscle IS automatically primary (can't be secondary)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Exercise") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Exercise name") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            enabled = group !in primaryMuscles && group !in secondaryMuscles, //disable it if it exists in other categories!
                            onClick = { selectedGroup = group },
                            label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Primary muscles (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = group in primaryMuscles,
                            enabled = group != selectedGroup && group !in secondaryMuscles, //can't select it again as primary if it is in "category" or if it is already secondary
                            onClick = {
                                primaryMuscles = if (group in primaryMuscles) primaryMuscles - group else primaryMuscles + group
                            },
                            label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Secondary muscles (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = group in secondaryMuscles,
                            enabled = group != selectedGroup && group !in primaryMuscles, // same logic as primary
                            onClick = {
                                secondaryMuscles = if (group in secondaryMuscles) secondaryMuscles - group else secondaryMuscles + group
                            },
                            label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { nameError = true; return@Button }
                val effectivePrimary = primaryMuscles.ifEmpty { setOf(selectedGroup) }
                onConfirm(name.trim(), selectedGroup, effectivePrimary.toList(), secondaryMuscles.toList())
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
