package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer die selbst eingetragene Sportart.
 *
 * Zwei Dinge sollen sie leisten: offensichtlichen Unsinn abweisen, und einen
 * Namen, den es schon gibt, nicht ein zweites Mal entstehen lassen - sonst
 * stuenden "padel" und "Padel" als zwei Stuecke im Ring der Auswertung.
 */
class SportsTest {

    @Test
    fun `a new name is taken as it is typed`() {
        assertEquals("Padel", Sports.customOrNull("Padel"))
        assertEquals("Bouldern", Sports.customOrNull("  Bouldern  "))
    }

    /** Sonst waeren "padel" und "Padel" zwei Stuecke im Ring der Sportarten. */
    @Test
    fun `the first letter is capitalised`() {
        assertEquals("Padel", Sports.customOrNull("padel"))
    }

    /** Der Rest bleibt, wie er getippt wurde - sonst wuerde aus HIIT ein Hiit. */
    @Test
    fun `an acronym keeps its capitals`() {
        assertEquals("HIIT", Sports.customOrNull("HIIT"))
        assertEquals("CrossFit", Sports.customOrNull("CrossFit"))
    }

    @Test
    fun `a known sport keeps the spelling of the list`() {
        assertEquals(Sports.RUNNING, Sports.customOrNull("running"))
        assertEquals(Sports.GYM, Sports.customOrNull("GYM"))
        assertEquals(Sports.YOGA, Sports.customOrNull(" yoga "))
    }

    /** Sonst waeren "Table  Tennis" und "Table Tennis" zwei Sportarten. */
    @Test
    fun `repeated spaces inside the name are collapsed`() {
        assertEquals("Table Tennis", Sports.customOrNull("Table   Tennis"))
    }

    @Test
    fun `a name that is too short or too long is refused`() {
        assertNull(Sports.customOrNull(""))
        assertNull(Sports.customOrNull("   "))
        assertNull(Sports.customOrNull("a"))
        assertNull(Sports.customOrNull("x".repeat(Sports.CUSTOM_MAX_LENGTH + 1)))
    }

    @Test
    fun `a name at the limits is still accepted`() {
        // Schon gross geschrieben, damit der Test die Laenge prueft und nicht
        // die Grossschreibung.
        val shortest = "X".repeat(Sports.CUSTOM_MIN_LENGTH)
        val longest = "Y".repeat(Sports.CUSTOM_MAX_LENGTH)
        assertEquals(shortest, Sports.customOrNull(shortest))
        assertEquals(longest, Sports.customOrNull(longest))
    }

    @Test
    fun `a name without any letter is refused`() {
        assertNull(Sports.customOrNull("123"))
        assertNull(Sports.customOrNull("!!!"))
    }

    @Test
    fun `only the sports of the list count as known`() {
        assertTrue(Sports.isKnown(Sports.RUNNING))
        assertFalse(Sports.isKnown("Padel"))
        assertFalse(Sports.isKnown("running"))
    }

    // === Punkte ===

    /**
     * Der eigentliche Punkt der Sache: eine eigene Sportart zaehlt als ganz
     * gewoehnliches Workout, mit dem mittleren der drei Faktoren.
     */
    @Test
    fun `a custom sport gets the middle intensity`() {
        assertEquals(WorkoutIntensity.MEDIUM, Sports.intensityFor("Padel"))
        assertEquals(WorkoutIntensity.MEDIUM.multiplier, WorkoutIntensity.LOW.multiplier + 1)
        assertEquals(WorkoutIntensity.HIGH.multiplier, WorkoutIntensity.MEDIUM.multiplier + 1)
    }

    @Test
    fun `a custom sport scores like any other workout of that intensity`() {
        val custom = PointsCalculator.calculateWorkoutPoints(60, Sports.intensityFor("Padel"))
        val football = PointsCalculator.calculateWorkoutPoints(60, Sports.intensityFor(Sports.FOOTBALL))
        assertEquals(football, custom)
    }

    /** Eine unbekannte Sportart bekommt kein Geraetesymbol angedichtet. */
    @Test
    fun `a custom sport gets the neutral icon`() {
        assertEquals(R.drawable.ic_sport_other, Sports.iconFor("Padel"))
        assertEquals(R.drawable.ic_sport_gym, Sports.iconFor(Sports.GYM))
    }

    @Test
    fun `distance is only asked for running`() {
        assertTrue(Sports.tracksDistance(Sports.RUNNING))
        assertFalse(Sports.tracksDistance("Padel"))
    }
}
