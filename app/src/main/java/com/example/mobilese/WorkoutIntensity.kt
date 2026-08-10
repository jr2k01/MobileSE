package com.example.mobilese

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutIntensity(val multiplier: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3)
}
