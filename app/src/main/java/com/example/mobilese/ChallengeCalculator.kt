package com.example.mobilese

import kotlin.math.roundToInt

enum class ChallengeType {
    DISTANCE,
    WORKOUT_COUNT
}

object ChallengeCalculator {

    /**
     * Calculates the total reward pool for a challenge.
     * DISTANCE: targetGoal * 2.0
     * WORKOUT_COUNT: targetGoal * 5.0
     */
    fun calculateTotalChallengePoints(type: ChallengeType, targetGoal: Double): Int {
        val multiplier = when (type) {
            ChallengeType.DISTANCE -> 2.0
            ChallengeType.WORKOUT_COUNT -> 5.0
        }
        return (targetGoal * multiplier).roundToInt()
    }

    /**
     * Calculates points per participant.
     * totalPoints / participantCount, rounded to nearest integer.
     */
    fun calculatePointsPerParticipant(totalPoints: Int, participantCount: Int): Int {
        if (participantCount <= 0) return 0
        return (totalPoints.toDouble() / participantCount).roundToInt()
    }
}
