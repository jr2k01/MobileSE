package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer das Tagesziel der Schritte.
 *
 * Die Schrittzahl kommt von aussen - aus Health Connect, gespeist von Geraeten,
 * ueber die die App nichts weiss. Deshalb wird auch geprueft, was bei Werten
 * passiert, die es eigentlich nicht geben duerfte.
 */
class StepGoalTest {

    @Test
    fun `the ring fills in proportion to the goal`() {
        assertEquals(0, StepGoal.progressPercent(0))
        assertEquals(10, StepGoal.progressPercent(1_000))
        assertEquals(50, StepGoal.progressPercent(5_000))
        assertEquals(99, StepGoal.progressPercent(9_999))
        assertEquals(100, StepGoal.progressPercent(10_000))
    }

    /** Ein voller Ring bleibt voll, er laeuft nicht wieder von vorne an. */
    @Test
    fun `beyond the goal the ring stays full`() {
        assertEquals(100, StepGoal.progressPercent(10_001))
        assertEquals(100, StepGoal.progressPercent(250_000))
        assertEquals(100, StepGoal.progressPercent(Long.MAX_VALUE))
    }

    @Test
    fun `a nonsensical negative count does not produce a negative ring`() {
        assertEquals(0, StepGoal.progressPercent(-1))
        assertEquals(0, StepGoal.progressPercent(Long.MIN_VALUE))
    }

    /** Grundlage der Bonuspunkte: erreicht ab genau dem Ziel. */
    @Test
    fun `the goal counts as reached from exactly the goal on`() {
        assertFalse(StepGoal.isReached(9_999))
        assertTrue(StepGoal.isReached(StepGoal.DAILY_STEPS.toLong()))
        assertTrue(StepGoal.isReached(20_000))
    }

    @Test
    fun `every reached day counts once, the others not at all`() {
        assertEquals(0, StepGoal.bonusPoints(emptyList()))
        assertEquals(0, StepGoal.bonusPoints(listOf(9_999, 300, 0)))
        assertEquals(StepGoal.BONUS_POINTS, StepGoal.bonusPoints(listOf(10_000)))
        assertEquals(
            3 * StepGoal.BONUS_POINTS,
            StepGoal.bonusPoints(listOf(10_000, 12_500, 400, 40_000, 9_999))
        )
    }

    /**
     * Ein besonders langer Tag bringt nicht mehr als einen normalen. Die Punkte
     * haengen an der Anzahl erreichter Tage, nicht an der Schrittzahl - sonst
     * koennte ein einzelner Ausreisser die Rangliste kippen.
     */
    @Test
    fun `a huge day counts the same as a just-reached one`() {
        assertEquals(
            StepGoal.bonusPoints(listOf(10_000)),
            StepGoal.bonusPoints(listOf(1_000_000))
        )
    }
}
