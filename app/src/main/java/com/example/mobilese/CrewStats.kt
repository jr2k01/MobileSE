package com.example.mobilese

import java.time.LocalDate

/**
 * Auswertungen ueber die Crew fuer den Ranglisten-Bildschirm.
 *
 * Wie [Scoreboard] und [Medals] reine Logik ueber einem bereits geladenen
 * [CrewSnapshot]: keine Abfragen, kein Android, ohne Emulator testbar. Der
 * Bildschirm bekommt fertige Zahlen und muss selbst nichts mehr rechnen.
 */
object CrewStats {

    /** Wie viele Tage die Wochenuebersicht zeigt. */
    const val WEEK_DAYS = 7

    /** Die Summen der ganzen Crew. */
    data class Totals(
        val workouts: Int,
        val minutes: Int,
        val kilometres: Double,
        /** Tage, an denen irgendwer sein Schrittziel erreicht hat. */
        val goalDays: Int
    )

    /** Ein Balken der Wochenuebersicht. */
    data class DayBar(val day: LocalDate, val minutes: Int)

    /** Ein Stueck der Sportarten-Verteilung. */
    data class SportShare(val sport: String, val minutes: Int)

    /**
     * Woraus sich die Punkte eines Mitglieds zusammensetzen.
     *
     * Die Rangliste zeigt bisher nur die Summe. Die Aufteilung sagt, wie jemand
     * dorthin gekommen ist - viel trainiert, Challenges geholt oder taeglich
     * gelaufen.
     */
    data class PointsSplit(val workouts: Int, val challenges: Int, val steps: Int) {
        val total: Int get() = workouts + challenges + steps
    }

    fun totals(snapshot: CrewSnapshot): Totals = Totals(
        workouts = snapshot.activities.size,
        minutes = snapshot.activities.sumOf { it.duration },
        kilometres = snapshot.activities.sumOf { it.distance },
        goalDays = snapshot.stepDays
            .filter { StepGoal.isReached(it.steps.toLong()) }
            .map { it.day }
            .distinct()
            .size
    )

    /**
     * Die Minuten der Crew an den letzten sieben Tagen, aeltester zuerst.
     *
     * Tage ohne Training kommen mit null Minuten vor und fallen nicht weg -
     * sonst haette die Woche eine unterschiedliche Zahl Balken und man saehe
     * die Luecken nicht, um die es gerade geht.
     */
    fun lastWeek(snapshot: CrewSnapshot, today: LocalDate = LocalDate.now()): List<DayBar> {
        val minutesByDay = snapshot.activities
            .groupBy { ActivityTime.dayOf(it.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.duration } }

        return (WEEK_DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            DayBar(day, minutesByDay[day.toString()] ?: 0)
        }
    }

    /**
     * Die Verteilung der Sportarten nach Minuten, groesste zuerst.
     *
     * Nach Minuten und nicht nach Anzahl: zehn Minuten Dehnen und zwei Stunden
     * Laufen sind zwei Eintraege, aber nicht dieselbe Leistung.
     */
    fun sportShares(snapshot: CrewSnapshot): List<SportShare> =
        snapshot.activities
            .groupBy { it.sport }
            .map { (sport, list) -> SportShare(sport, list.sumOf { it.duration }) }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }

    /**
     * Die Anteile in ganzen Prozent, die zusammen wieder 100 ergeben.
     *
     * Einfaches Abschneiden je Wert reicht nicht: aus 85,4 und 14,6 wuerden 85
     * und 14, und in der Legende staende sichtbar 99 Prozent. Der Rest zur
     * vollen Hundert geht deshalb an den groessten Anteil - den, bei dem ein
     * Prozentpunkt am wenigsten ins Gewicht faellt.
     */
    fun sharePercentages(shares: List<SportShare>): List<Int> {
        val total = shares.sumOf { it.minutes }
        if (total <= 0) return shares.map { 0 }

        val percentages = shares.map { it.minutes * 100 / total }.toMutableList()
        val missing = 100 - percentages.sum()
        if (missing != 0 && percentages.isNotEmpty()) {
            val biggest = percentages.indexOf(percentages.max())
            percentages[biggest] = percentages[biggest] + missing
        }
        return percentages
    }

    /** Die Punkte eines Mitglieds nach Herkunft. */
    fun pointsSplit(userId: String, snapshot: CrewSnapshot): PointsSplit {
        val workoutPoints = snapshot.activities
            .filter { it.userId == userId }
            .sumOf {
                PointsCalculator.calculateWorkoutPoints(
                    it.duration,
                    WorkoutIntensity.fromName(it.intensity)
                )
            }

        val challengePoints = snapshot.rewards
            .filter { it.userId == userId }
            .sumOf { it.points }

        val stepPoints = StepGoal.bonusPoints(
            snapshot.stepDays.filter { it.userId == userId }.map { it.steps }
        )

        return PointsSplit(workoutPoints, challengePoints, stepPoints)
    }
}
