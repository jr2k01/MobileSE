package com.example.mobilese

/**
 * Eine noch nicht ausgeschuettete Belohnung: wer bekommt fuer welche Challenge
 * wie viele Punkte.
 */
data class PendingAward(
    val challengeId: String,
    val userIds: List<String>,
    val pointsPerUser: Int
)

object ChallengeManager {

    /**
     * Der Beitrag jedes Crew-Mitglieds zu einer Challenge, absteigend sortiert.
     * Grundlage sind die bereits geladenen Aktivitaeten aus dem Snapshot.
     */
    fun progressByMember(challenge: Challenge, snapshot: CrewSnapshot): List<Pair<UserProfile, Int>> {
        val activitiesByUser = snapshot.activities.groupBy { it.userId }
        return snapshot.members
            .map { member ->
                member to ChallengeCalculator.progressOf(
                    challenge.type,
                    activitiesByUser[member.id].orEmpty()
                )
            }
            .sortedByDescending { it.second }
    }

    /**
     * Ermittelt, welche Mitglieder fuer eine Challenge noch Punkte bekommen
     * muessen. Gibt null zurueck, wenn nichts zu tun ist - das Ziel ist noch
     * nicht erreicht, alle wurden bereits bedacht, oder der Topf ist leer.
     *
     * Die Trennung von der Datenbank ist Absicht: vorher fragte diese Logik
     * fuer jede teilnehmende Person einzeln nach, ob sie schon belohnt wurde.
     * Bei fuenf Mitgliedern und drei Challenges waren das fuenfzehn Abfragen
     * bei jedem Oeffnen der Rangliste. Der Stand der Belohnungen kommt jetzt
     * aus dem bereits geladenen [CrewSnapshot].
     */
    fun pendingAward(
        challenge: Challenge,
        totalProgress: Int,
        memberIds: List<String>,
        snapshot: CrewSnapshot
    ): PendingAward? {
        if (totalProgress < challenge.goal) return null
        if (memberIds.isEmpty()) return null

        val alreadyRewarded = snapshot.rewards
            .filter { it.challengeId == challenge.id }
            .map { it.userId }
            .toSet()

        val pending = memberIds.filterNot { it in alreadyRewarded }
        if (pending.isEmpty()) return null

        val pointsPerUser =
            ChallengeCalculator.calculatePointsPerParticipant(challenge.reward, memberIds.size)
        if (pointsPerUser <= 0) return null

        return PendingAward(challenge.id, pending, pointsPerUser)
    }
}
