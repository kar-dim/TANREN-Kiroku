package gr.dkaratzas.tanrenkiroku.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutDay(
    val date: String,
    val entries: List<WorkoutEntry> = emptyList()
)

@Serializable
data class WorkoutEntry(
    val exerciseId: String,
    val sets: List<WorkoutSet> = emptyList()
)

@Serializable
data class WorkoutSet(
    val reps: Int,
    val kg: Double
)
