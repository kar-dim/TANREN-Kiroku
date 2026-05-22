package gr.dkaratzas.tanrenkiroku.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomExercise(
    val id: String,
    val name: String,
    val pickerGroup: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList()
)
