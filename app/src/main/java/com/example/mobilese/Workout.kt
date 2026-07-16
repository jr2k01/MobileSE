package com.example.mobilese

data class Workout(
    val id: String,
    val title: String,
    val durationInMinutes: Int,
    val intensity: WorkoutIntensity,
    val timestamp: Long
)
