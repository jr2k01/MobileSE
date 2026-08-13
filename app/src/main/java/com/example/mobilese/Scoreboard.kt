package com.example.mobilese

/**
 * Rechnet aus einem [CrewSnapshot] die Rangliste aus.
 *
 * Bewusst ohne Android- und Netzabhaengigkeiten: Frueher steckte diese Logik
 * in den Activities und stellte pro Mitglied mehrere Datenbankabfragen. Jetzt
 * ist es eine reine Funktion ueber bereits geladenen Daten - schnell, und ohne
 * Emulator testbar.
 */
object Scoreboard {

    data class Entry(
        val userId: String,
        val email: String,
        val name: String,
        val avatarUrl: String?,
        val points: Int
    )

    /**
     * Die Mitglieder der Crew, nach Punkten absteigend sortiert.
     *
     * Punkte = Workout-Punkte aller Aktivitaeten in dieser Crew plus die
     * gutgeschriebenen Challenge-Belohnungen. Aktivitaeten von Nutzern, die die
     * Crew verlassen haben, bleiben in der Datenbank stehen und werden hier
     * uebergangen, weil sie zu keinem aktuellen Mitglied gehoeren.
     *
     * Bei Punktgleichstand entscheidet der Name, damit die Reihenfolge zwischen
     * zwei Aufrufen stabil bleibt.
     */
    fun build(snapshot: CrewSnapshot): List<Entry> {
        val activitiesByUser = snapshot.activities.groupBy { it.userId }
        val rewardsByUser = snapshot.rewards
            .groupBy { it.userId }
            .mapValues { (_, rewards) -> rewards.sumOf { it.points } }

        return snapshot.members.map { profile ->
            val workoutPoints = activitiesByUser[profile.id].orEmpty().sumOf { activity ->
                PointsCalculator.calculateWorkoutPoints(
                    activity.duration,
                    WorkoutIntensity.fromName(activity.intensity)
                )
            }
            Entry(
                userId = profile.id,
                email = profile.email.orEmpty(),
                name = profile.name?.takeIf { it.isNotBlank() } ?: "Unknown",
                avatarUrl = profile.avatarUrl,
                points = workoutPoints + (rewardsByUser[profile.id] ?: 0)
            )
        }.sortedWith(compareByDescending<Entry> { it.points }.thenBy { it.name })
    }
}
