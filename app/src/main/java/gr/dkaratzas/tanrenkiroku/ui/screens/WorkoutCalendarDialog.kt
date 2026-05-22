package gr.dkaratzas.tanrenkiroku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import gr.dkaratzas.tanrenkiroku.data.model.WorkoutDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Composables to show the "Workout calendar" dialog that shows a list fo workouts of specific dates
@Composable
fun WorkoutCalendarDialog(
    selectedDate: LocalDate,
    workoutDates: Set<LocalDate>,
    allWorkouts: List<WorkoutDay>,
    formatWeight: (Double) -> String,
    displayName: (String) -> String,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showHistory by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MonthHeader(
                    month = displayMonth,
                    onPrev = { displayMonth = displayMonth.minusMonths(1) },
                    onNext = { displayMonth = displayMonth.plusMonths(1) }
                )
                Spacer(Modifier.height(8.dp))
                DayOfWeekRow()
                Spacer(Modifier.height(4.dp))
                CalendarGrid(
                    month = displayMonth,
                    selectedDate = selectedDate,
                    workoutDates = workoutDates,
                    onDayClick = { date ->
                        onDateSelected(date)
                        onDismiss()
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showHistory = true }) {
                        Text("${allWorkouts.size} Workouts")
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }

    if (showHistory) {
        WorkoutHistoryDialog(
            workouts = allWorkouts,
            formatWeight = formatWeight,
            displayName = displayName,
            onSelect = { date ->
                onDateSelected(date)
                onDismiss()
            },
            onDismiss = { showHistory = false }
        )
    }
}

@Composable
fun WorkoutHistoryDialog(
    workouts: List<WorkoutDay>,
    formatWeight: (Double) -> String,
    displayName: (String) -> String,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Workout History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workouts, key = { it.date }) { workout ->
                        WorkoutHistoryItem(
                            workout = workout,
                            formatWeight = formatWeight,
                            displayName = displayName,
                            onClick = { onSelect(LocalDate.parse(workout.date)) }
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryItem(
    workout: WorkoutDay,
    formatWeight: (Double) -> String,
    displayName: (String) -> String,
    onClick: () -> Unit
) {
    val date = remember(workout.date) { LocalDate.parse(workout.date) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formatHistoryDate(date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            workout.entries.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = displayName(entry.exerciseId),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    entry.sets.forEach { set ->
                        Text(
                            text = "${formatWeight(set.kg).padStart(9)} × %3d reps".format(set.reps),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistoryDate(date: LocalDate): String {
    val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val day = date.dayOfMonth
    val ordinal = when {
        day % 100 in 11..13 -> "${day}th"
        day % 10 == 1 -> "${day}st"
        day % 10 == 2 -> "${day}nd"
        day % 10 == 3 -> "${day}rd"
        else -> "${day}th"
    }
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "$dow, $ordinal $month ${date.year}"
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun DayOfWeekRow() {
    val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    workoutDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstDay = month.atDay(1)
    val startOffset = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = startOffset + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - startOffset + 1
                    if (dayNumber !in 1..daysInMonth) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            hasWorkout = date in workoutDates,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasWorkout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (isToday && !isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )
            }
            Box(modifier = Modifier.height(6.dp), contentAlignment = Alignment.Center) {
                if (hasWorkout) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
