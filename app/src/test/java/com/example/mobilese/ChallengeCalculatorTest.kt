package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-Tests fuer den Belohnungstopf einer Team-Challenge und dessen
 * Aufteilung auf die Teilnehmenden.
 */
class ChallengeCalculatorTest {

    @Test
    fun `distance challenges award two points per unit of the goal`() {
        assertEquals(20, ChallengeCalculator.calculateTotalChallengePoints(ChallengeType.DISTANCE, 10.0))
        assertEquals(25, ChallengeCalculator.calculateTotalChallengePoints(ChallengeType.DISTANCE, 12.5))
    }

    @Test
    fun `workout count challenges award five points per unit of the goal`() {
        assertEquals(50, ChallengeCalculator.calculateTotalChallengePoints(ChallengeType.WORKOUT_COUNT, 10.0))
    }

    @Test
    fun `the pot follows the share of the work`() {
        // Neunzig Prozent der Arbeit, neunzig Prozent der Punkte.
        assertEquals(listOf(90, 10), ChallengeCalculator.shareOut(100, listOf(90, 10)))
    }

    @Test
    fun `equal work is split equally`() {
        assertEquals(listOf(10, 10), ChallengeCalculator.shareOut(20, listOf(5, 5)))
    }

    /** Wer zugesehen hat, bekommt nichts - frueher war es die Haelfte. */
    @Test
    fun `who contributed nothing gets nothing`() {
        assertEquals(listOf(20, 0), ChallengeCalculator.shareOut(20, listOf(7, 0)))
    }

    /**
     * Einzeln gerundet ergaebe 3 x 7 = 21 von 20 Punkten. Der Topf muss auf den
     * Punkt genau aufgehen, sonst entstehen Punkte aus dem Nichts.
     */
    @Test
    fun `the pot is spent exactly`() {
        val shares = ChallengeCalculator.shareOut(20, listOf(1, 1, 1))

        assertEquals(20, shares.sum())
        assertEquals(listOf(7, 7, 6), shares)
    }

    @Test
    fun `a big pot on many members still adds up`() {
        val shares = ChallengeCalculator.shareOut(100, listOf(33, 33, 33, 1))

        assertEquals(100, shares.sum())
    }

    /** Mehr Mitglieder als Punkte: es darf nichts verschwinden und nichts hangen. */
    @Test
    fun `more members than points`() {
        val shares = ChallengeCalculator.shareOut(2, listOf(5, 4, 3, 2, 1))

        assertEquals(2, shares.sum())
    }

    @Test
    fun `nothing to share out`() {
        assertEquals(listOf(0, 0), ChallengeCalculator.shareOut(0, listOf(5, 5)))
        assertEquals(listOf(0, 0), ChallengeCalculator.shareOut(20, listOf(0, 0)))
        assertEquals(emptyList<Int>(), ChallengeCalculator.shareOut(20, emptyList()))
    }

    /** Negative Beitraege kann es nicht geben, koennen aber aus Daten kommen. */
    @Test
    fun `negative contributions count as nothing`() {
        assertEquals(listOf(20, 0), ChallengeCalculator.shareOut(20, listOf(4, -3)))
    }

    @Test
    fun `a goal of zero yields an empty reward pool`() {
        assertEquals(0, ChallengeCalculator.calculateTotalChallengePoints(ChallengeType.DISTANCE, 0.0))
    }
}
