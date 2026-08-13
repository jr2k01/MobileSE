package com.example.mobilese

import kotlin.math.roundToInt

enum class ChallengeType {
    DISTANCE,
    WORKOUT_COUNT
}

object ChallengeCalculator {

    /**
     * Der gesamte Belohnungstopf einer Challenge.
     * DISTANCE: Ziel * 2.0, WORKOUT_COUNT: Ziel * 5.0
     */
    fun calculateTotalChallengePoints(type: ChallengeType, targetGoal: Double): Int {
        val multiplier = when (type) {
            ChallengeType.DISTANCE -> 2.0
            ChallengeType.WORKOUT_COUNT -> 5.0
        }
        return (targetGoal * multiplier).roundToInt()
    }

    /** Der Anteil einer teilnehmenden Person am Belohnungstopf, kaufmaennisch gerundet. */
    fun calculatePointsPerParticipant(totalPoints: Int, participantCount: Int): Int {
        if (participantCount <= 0) return 0
        return (totalPoints.toDouble() / participantCount).roundToInt()
    }

    /**
     * Erkennt Distanz-Challenges. "Running" ist der Typname aus einer frueheren
     * Version und steht noch in bestehenden Datensaetzen.
     */
    fun isDistanceChallenge(type: String): Boolean =
        type == ChallengeType.DISTANCE.name || type == Sports.RUNNING

    /**
     * Der Beitrag einer Menge von Aktivitaeten zu einer Challenge.
     *
     * Bei Distanz-Challenges werden die Kilometer als Kommazahlen summiert und
     * erst am Ende abgeschnitten. Vorher wurde jede Aktivitaet einzeln auf eine
     * ganze Zahl gekuerzt, wodurch drei Laeufe von je 900 Metern als null
     * Kilometer zaehlten statt als zwei.
     */
    fun progressOf(type: String, activities: List<Activity>): Int =
        if (isDistanceChallenge(type)) {
            activities.sumOf { it.distance }.toInt()
        } else {
            activities.count { it.sport == Sports.GYM }
        }
}
