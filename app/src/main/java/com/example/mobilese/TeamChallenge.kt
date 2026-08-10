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
    suspend fun distributeChallengePoints(challenge: TeamChallenge, crewCode: String, backend: AppBackend) {
        if (challenge.isCompleted) {
            val participantCount = challenge.participantIds.size
            val pointsPerUser = ChallengeCalculator.calculatePointsPerParticipant(challenge.rewardPool, participantCount)
            
            challenge.participantIds.forEach { userId ->
                if (!backend.isChallengeRewarded(userId, challenge.id)) {
                    backend.addUserChallengePoints(userId, crewCode, pointsPerUser)
                    backend.markChallengeRewarded(userId, challenge.id)
                }
            }
        }
    }
}
