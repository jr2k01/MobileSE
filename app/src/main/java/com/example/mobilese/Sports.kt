package com.example.mobilese

/**
 * Die auswaehlbaren Sportarten und die daraus abgeleitete Intensitaet.
 *
 * Vorher standen die Namen als Zeichenketten in der Activity, im Repository und
 * in der Challenge-Auswertung nebeneinander. Ein Tippfehler an einer der
 * Stellen waere nicht aufgefallen, haette aber die Punktevergabe verstellt.
 */
object Sports {

    const val RUNNING = "Running"
    const val CYCLING = "Cycling"
    const val SWIMMING = "Swimming"
    const val YOGA = "Yoga"
    const val FOOTBALL = "Football"
    const val GYM = "Gym"

    val ALL = arrayOf(RUNNING, CYCLING, SWIMMING, YOGA, FOOTBALL, GYM)

    /** Das Symbol, mit dem eine Sportart in der Auswahl gezeigt wird. */
    fun iconFor(sport: String): Int = when (sport) {
        RUNNING -> R.drawable.ic_sport_running
        CYCLING -> R.drawable.ic_sport_cycling
        SWIMMING -> R.drawable.ic_sport_swimming
        YOGA -> R.drawable.ic_sport_yoga
        FOOTBALL -> R.drawable.ic_sport_football
        else -> R.drawable.ic_sport_gym
    }

    /** Sportarten, bei denen nach einer Distanz gefragt wird. */
    fun tracksDistance(sport: String): Boolean = sport == RUNNING

    fun intensityFor(sport: String): WorkoutIntensity = when (sport) {
        YOGA -> WorkoutIntensity.LOW
        FOOTBALL -> WorkoutIntensity.MEDIUM
        RUNNING, SWIMMING, CYCLING, GYM -> WorkoutIntensity.HIGH
        else -> WorkoutIntensity.MEDIUM
    }
}
