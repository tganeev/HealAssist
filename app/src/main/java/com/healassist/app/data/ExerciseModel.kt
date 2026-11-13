package com.healassist.app.data

enum class ExerciseType {
    FINGER, SQUAT
}

data class ExerciseModel(
    val id: String,
    val name: String,
    val instruction: String,
    val targetSets: Int,
    val repsPerSet: Int,
    val restBetweenSetsSec: Int,
    val type: ExerciseType
)