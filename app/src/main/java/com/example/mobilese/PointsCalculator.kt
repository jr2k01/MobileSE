package com.example.mobilese

import kotlin.math.roundToInt

object PointsCalculator {

    /** Womit ein gemeinsames Training vervielfacht wird. */
    const val TOGETHER_FACTOR = 2

    /**
     * Calculates points for a workout based on duration and intensity.
     * Logic:
     * - Under 10 minutes: 0 points.
     * - 10 minutes or more: (duration / 10.0) * intensity multiplier + 5 bonus.
     * - Result rounded to nearest integer.
     *
     * @param together Ob mit jemandem aus der Crew zusammen trainiert wurde -
     *        die Telefone haben sich dabei ueber Bluetooth erkannt, siehe
     *        [CoLocation]. Dann zaehlt das Workout doppelt.
     *
     *        Verdoppelt wird **nach** dem Runden, nicht davor: sonst haengt das
     *        Ergebnis daran, ob eine halbe Punktzahl auf- oder abgerundet
     *        wurde, und zwei gleiche Trainings brachten unterschiedlich viel.
     *
     *        Ein Training unter zehn Minuten bleibt bei null. Zweimal nichts
     *        ist nichts - gemeinsam kurz stehenbleiben ist kein Training.
     */
    fun calculateWorkoutPoints(
        durationInMinutes: Int,
        intensity: WorkoutIntensity,
        together: Boolean = false
    ): Int {
        if (durationInMinutes < 10) return 0

        val basePoints = (durationInMinutes / 10.0) * intensity.multiplier
        val bonus = 5
        val points = (basePoints + bonus).roundToInt()

        return if (together) points * TOGETHER_FACTOR else points
    }
}
