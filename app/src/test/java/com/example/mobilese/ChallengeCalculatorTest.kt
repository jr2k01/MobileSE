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
    fun `the reward pool is split evenly and rounded`() {
        assertEquals(10, ChallengeCalculator.calculatePointsPerParticipant(20, 2))
        // 20 / 3 = 6.67 -> 7
        assertEquals(7, ChallengeCalculator.calculatePointsPerParticipant(20, 3))
    }

    @Test
    fun `an empty participant list does not cause a division by zero`() {
        assertEquals(0, ChallengeCalculator.calculatePointsPerParticipant(20, 0))
        assertEquals(0, ChallengeCalculator.calculatePointsPerParticipant(20, -1))
    }

    @Test
    fun `a goal of zero yields an empty reward pool`() {
        assertEquals(0, ChallengeCalculator.calculateTotalChallengePoints(ChallengeType.DISTANCE, 0.0))
        assertEquals(0, ChallengeCalculator.calculatePointsPerParticipant(0, 5))
    }
}
