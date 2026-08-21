package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-Tests fuer die Punkteberechnung eines einzelnen Workouts.
 *
 * PointsCalculator ist eine reine Funktion ohne Android-Abhaengigkeiten und
 * laesst sich deshalb ohne Emulator auf der JVM testen.
 */
class PointsCalculatorTest {

    @Test
    fun `workouts under ten minutes score no points`() {
        assertEquals(0, PointsCalculator.calculateWorkoutPoints(0, WorkoutIntensity.HIGH))
        assertEquals(0, PointsCalculator.calculateWorkoutPoints(9, WorkoutIntensity.HIGH))
    }

    @Test
    fun `ten minutes is the lower threshold and grants the first-step bonus`() {
        // (10 / 10.0) * 1 + 5 = 6
        assertEquals(6, PointsCalculator.calculateWorkoutPoints(10, WorkoutIntensity.LOW))
    }

    @Test
    fun `intensity multiplies the duration component only, not the bonus`() {
        // (10 / 10.0) * 3 + 5 = 8
        assertEquals(8, PointsCalculator.calculateWorkoutPoints(10, WorkoutIntensity.HIGH))
        // (30 / 10.0) * 3 + 5 = 14
        assertEquals(14, PointsCalculator.calculateWorkoutPoints(30, WorkoutIntensity.HIGH))
        // (45 / 10.0) * 2 + 5 = 14
        assertEquals(14, PointsCalculator.calculateWorkoutPoints(45, WorkoutIntensity.MEDIUM))
    }

    @Test
    fun `fractional results are rounded to the nearest integer`() {
        // (25 / 10.0) * 1 + 5 = 7.5 -> 8
        assertEquals(8, PointsCalculator.calculateWorkoutPoints(25, WorkoutIntensity.LOW))
    }

    @Test
    fun `a longer workout never scores less than a shorter one at equal intensity`() {
        var previous = -1
        for (minutes in 0..180 step 5) {
            val points = PointsCalculator.calculateWorkoutPoints(minutes, WorkoutIntensity.MEDIUM)
            assert(points >= previous) { "points dropped at $minutes minutes" }
            previous = points
        }
    }

    // --- Gemeinsames Training ---

    @Test
    fun `training together counts double`() {
        val alone = PointsCalculator.calculateWorkoutPoints(60, WorkoutIntensity.HIGH)
        val together = PointsCalculator.calculateWorkoutPoints(60, WorkoutIntensity.HIGH, together = true)

        assertEquals(alone * 2, together)
    }

    /** Ohne Angabe bleibt alles wie bisher - der Rest der App ruft so auf. */
    @Test
    fun `counts single by default`() {
        assertEquals(
            PointsCalculator.calculateWorkoutPoints(45, WorkoutIntensity.LOW),
            PointsCalculator.calculateWorkoutPoints(45, WorkoutIntensity.LOW, together = false)
        )
    }

    /** Zweimal nichts ist nichts: gemeinsam kurz stehenbleiben zaehlt nicht. */
    @Test
    fun `too short stays worthless even together`() {
        assertEquals(0, PointsCalculator.calculateWorkoutPoints(9, WorkoutIntensity.HIGH, together = true))
    }

    /**
     * Verdoppelt wird nach dem Runden. Sonst haenge das Ergebnis daran, ob
     * eine halbe Punktzahl auf- oder abgerundet wurde, und zwei gleiche
     * Trainings braechten unterschiedlich viel.
     */
    @Test
    fun `doubles the rounded value, not the raw one`() {
        for (minutes in 10..180 step 1) {
            for (intensity in WorkoutIntensity.entries) {
                val alone = PointsCalculator.calculateWorkoutPoints(minutes, intensity)
                val together = PointsCalculator.calculateWorkoutPoints(minutes, intensity, together = true)
                assertEquals("$minutes min, $intensity", alone * 2, together)
            }
        }
    }
}
