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

    /** Sportarten, bei denen nach einer Distanz gefragt wird. */
    fun tracksDistance(sport: String): Boolean = sport == RUNNING

    fun intensityFor(sport: String): WorkoutIntensity = when (sport) {
        YOGA -> WorkoutIntensity.LOW
        FOOTBALL -> WorkoutIntensity.MEDIUM
        RUNNING, SWIMMING, CYCLING, GYM -> WorkoutIntensity.HIGH
        else -> WorkoutIntensity.MEDIUM
    }
}
