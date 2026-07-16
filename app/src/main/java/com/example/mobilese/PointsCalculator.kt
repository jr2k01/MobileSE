package com.example.mobilese

import kotlin.math.roundToInt

object PointsCalculator {
    /**
     * Calculates points for a workout based on duration and intensity.
     * Logic:
     * - Under 10 minutes: 0 points.
     * - 10 minutes or more: (duration / 10.0) * intensity multiplier + 5 bonus.
     * - Result rounded to nearest integer.
     */
    fun calculateWorkoutPoints(durationInMinutes: Int, intensity: WorkoutIntensity): Int {
        if (durationInMinutes < 10) return 0
        
        val basePoints = (durationInMinutes / 10.0) * intensity.multiplier
        val bonus = 5
        
        return (basePoints + bonus).roundToInt()
    }
}
