package com.example.mobilese

/**
 * Level und Prestige aus der Gesamtpunktzahl.
 *
 * Gerechnet wird ueber alle Crews hinweg: Punkte sind eine Eigenschaft der
 * Person, nicht der Crew. Wer in zwei Crews trainiert, sammelt nicht zweimal
 * getrennt - sonst waere der Wechsel in eine neue Crew ein Rueckschritt.
 *
 * Hundert Level, danach Prestige 1 und wieder bei Level 1. Der Zaehler laeuft
 * also nicht ins Unendliche, sondern beginnt sichtbar von vorn - der Reiz
 * liegt in der Zahl davor.
 *
 * Reine Rechnung ohne Android- und Netzzugriff, wie [Scoreboard] und
 * [PointsCalculator], und damit ohne Emulator zu pruefen.
 */
object Levels {

    /** Hoechstes Level vor dem Prestige. */
    const val MAX_LEVEL = 100

    /**
     * Was der Aufstieg von Level 1 kostet.
     *
     * Ein Workout von einer Stunde bei hoher Intensitaet bringt 23 Punkte -
     * das erste Level ist also nach ein bis zwei Trainings geschafft. Der
     * Anfang soll sich sofort bewegen, sonst sieht niemand, dass es das
     * System ueberhaupt gibt.
     */
    private const val FIRST_LEVEL_COST = 30

    /**
     * Um wie viel jedes weitere Level teurer wird.
     *
     * Linear und nicht exponentiell: Level 100 kostet damit 426 Punkte, gut
     * das Vierzehnfache des ersten. Exponentiell waeren die letzten Level
     * unerreichbar, und ein Ziel, das niemand sieht, spornt nicht an.
     */
    private const val COST_STEP = 4

    /**
     * Was ein Level kostet - die Punkte, um es zu verlassen.
     *
     * Ausserhalb von 1 bis [MAX_LEVEL] wird auf den naechsten gueltigen Wert
     * geklemmt: die Zahl kommt am Ende aus der Datenbank, und ein krummer
     * Wert dort darf keine Division durch null ergeben.
     */
    fun costOf(level: Int): Int {
        val bounded = level.coerceIn(1, MAX_LEVEL)
        return FIRST_LEVEL_COST + COST_STEP * (bounded - 1)
    }

    /**
     * Punkte fuer einen vollen Durchlauf, also von Prestige n Level 1 bis
     * Prestige n+1 Level 1.
     */
    val prestigeCost: Int = (1..MAX_LEVEL).sumOf { costOf(it) }

    /**
     * Prestige, Level und Fortschritt darin.
     *
     * Negative Punkte kann es nicht geben; sie werden trotzdem abgefangen,
     * weil die Summe aus Daten entsteht, die auch von Hand in der Datenbank
     * stehen koennen.
     */
    fun of(totalPoints: Int): LevelProgress {
        val points = totalPoints.coerceAtLeast(0)

        val prestige = points / prestigeCost
        var rest = points % prestigeCost

        // Der Rest ist kleiner als ein voller Durchlauf, kann Level 100 also
        // nicht abschliessen - die Schleife laeuft hoechstens 99 Schritte.
        var level = 1
        while (level < MAX_LEVEL && rest >= costOf(level)) {
            rest -= costOf(level)
            level++
        }

        return LevelProgress(
            prestige = prestige,
            level = level,
            pointsIntoLevel = rest,
            pointsForNextLevel = costOf(level)
        )
    }
}

/**
 * Wo jemand steht.
 *
 * @param prestige Wie oft Level 100 vollendet wurde. 0 beim ersten Durchlauf.
 * @param pointsIntoLevel Punkte, die im aktuellen Level schon gesammelt sind.
 * @param pointsForNextLevel Punkte, die dieses Level insgesamt kostet.
 */
data class LevelProgress(
    val prestige: Int,
    val level: Int,
    val pointsIntoLevel: Int,
    val pointsForNextLevel: Int
) {
    /** Fuellstand des Fortschrittsbalkens in Prozent, 0 bis 100. */
    val percent: Int
        get() = if (pointsForNextLevel <= 0) 0
        else (pointsIntoLevel * 100 / pointsForNextLevel).coerceIn(0, 100)
}
