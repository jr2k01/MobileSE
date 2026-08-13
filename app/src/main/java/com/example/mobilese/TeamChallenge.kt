package com.example.mobilese

import kotlinx.serialization.Serializable

@Serializable
data class TeamChallenge(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val rewardPool: Int,
    val participantIds: List<String>
)

object ChallengeManager {
    /**
     * Distributes reward pool points to all participants if the challenge is completed.
     * Rewards are distributed fairly based on the total pool and participant count.
     * Ensures each user only receives the reward once per challenge.
     */
    suspend fun distributeChallengePoints(challenge: TeamChallenge, backend: AppRepository) {
        if (!challenge.isCompleted) return

        val participantCount = challenge.participantIds.size
        if (participantCount <= 0) return

        val pointsPerUser = ChallengeCalculator.calculatePointsPerParticipant(challenge.rewardPool, participantCount)

        challenge.participantIds.forEach { participant ->
            if (!backend.isChallengeRewarded(participant, challenge.id)) {
                // Die Punktzahl wird im Belohnungseintrag selbst festgehalten.
                // Ein separates Hochzaehlen entfaellt damit, und die Punkte
                // bleiben auch dann korrekt, wenn sich die Crew spaeter
                // vergroessert oder verkleinert.
                backend.markChallengeRewarded(participant, challenge.id, pointsPerUser)
            }
        }
    }
}
