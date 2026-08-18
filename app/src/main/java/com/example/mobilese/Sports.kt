package com.example.mobilese

import java.util.Locale

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

    /** Grenzen fuer eine selbst eingetragene Sportart. */
    const val CUSTOM_MIN_LENGTH = 2
    const val CUSTOM_MAX_LENGTH = 20

    /**
     * Nimmt eine selbst eingetippte Sportart an, oder null wenn der Name nicht
     * taugt.
     *
     * Ein Name, den es schon gibt, wird auf die vorgegebene Schreibweise
     * gebracht: "padel" und "Padel" sollen dieselbe Sportart sein und nicht als
     * zwei Stuecke im Ring der Auswertung auftauchen. Aus demselben Grund wird
     * mehrfacher Leerraum in der Mitte zusammengezogen.
     */
    fun customOrNull(input: String): String? {
        val name = input.trim().replace(WHITESPACE, " ")
        if (name.length < CUSTOM_MIN_LENGTH || name.length > CUSTOM_MAX_LENGTH) return null
        // Ein Name ganz ohne Buchstaben ist keiner.
        if (name.none { it.isLetter() }) return null
        ALL.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }

        // Erster Buchstabe gross, der Rest bleibt: so wird aus "padel" dasselbe
        // wie aus "Padel". Durchgaengig zu vereinheitlichen waere schlechter -
        // aus "HIIT" wuerde "Hiit".
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    /** Ob die Sportart eine der vorgegebenen ist. */
    fun isKnown(sport: String): Boolean = ALL.any { it == sport }

    private val WHITESPACE = Regex("\\s+")

    /**
     * Das Symbol, mit dem eine Sportart gezeigt wird.
     *
     * Eine selbst eingetragene Sportart bekommt ein neutrales Zeichen. Vorher
     * fiel alles Unbekannte auf die Hantel zurueck - fuer "Padel" waere das
     * schlicht falsch.
     */
    fun iconFor(sport: String): Int = when (sport) {
        RUNNING -> R.drawable.ic_sport_running
        CYCLING -> R.drawable.ic_sport_cycling
        SWIMMING -> R.drawable.ic_sport_swimming
        YOGA -> R.drawable.ic_sport_yoga
        FOOTBALL -> R.drawable.ic_sport_football
        GYM -> R.drawable.ic_sport_gym
        else -> R.drawable.ic_sport_other
    }

    /** Sportarten, bei denen nach einer Distanz gefragt wird. */
    fun tracksDistance(sport: String): Boolean = sport == RUNNING

    /**
     * Die Intensitaet, mit der eine Sportart in die Punkte eingeht.
     *
     * Eine selbst eingetragene Sportart bekommt MEDIUM - den mittleren der drei
     * Faktoren. Ueber sie ist nichts bekannt, und der Mittelwert ist die
     * einzige Annahme, die niemanden bevorzugt oder benachteiligt: sie zaehlt
     * damit als ganz gewoehnliches Workout. Die Punkte selbst haengen ohnehin
     * nur an Dauer und Intensitaet, nicht an der Sportart.
     */
    fun intensityFor(sport: String): WorkoutIntensity = when (sport) {
        YOGA -> WorkoutIntensity.LOW
        FOOTBALL -> WorkoutIntensity.MEDIUM
        RUNNING, SWIMMING, CYCLING, GYM -> WorkoutIntensity.HIGH
        else -> WorkoutIntensity.MEDIUM
    }
}
