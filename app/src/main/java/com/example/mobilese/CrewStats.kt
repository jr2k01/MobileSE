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

    /**
     * Ein Balken einer Tagesreihe.
     *
     * Der Wert heisst nicht "minutes", weil dieselbe Reihe zweimal gebraucht
     * wird: einmal fuer Trainingsminuten, einmal fuer Schritte.
     */
    /**
     * Ein Tag der Wochenuebersicht.
     *
     * [isToday] steht hier und wird nicht in der Ansicht ausgerechnet: die
     * Reihe weiss, auf welchen Tag sie sich bezieht, die Ansicht nicht - sie
     * bekaeme sonst ein zweites "heute", das an einem Datumswechsel um
     * Mitternacht vom ersten abweichen kann.
     */
    data class DayBar(val day: LocalDate, val amount: Int, val isToday: Boolean = false)

    /** Ein Stueck der Sportarten-Verteilung. */
    data class SportShare(val sport: String, val minutes: Int)

    /**
     * Was ein einzelnes Mitglied beigetragen hat.
     *
     * Die Rangliste zeigt Punkte, und die verrechnen Training, Challenges und
     * Schritte miteinander. Hier steht nur das Training - daran laesst sich
     * ablesen, wer die Stunden gemacht hat.
     */
    data class MemberShare(
        val userId: String,
        val minutes: Int,
        val workouts: Int,
        val kilometres: Double
    )

    /** Einzelwerte, die als Zahl mehr sagen als in einem Diagramm. */
    data class Highlights(
        /** Das laengste einzelne Training, oder null. */
        val longest: Activity?,
        /** Tage in Folge mit mindestens einem Training. */
        val streakDays: Int,
        /** Tage, an denen irgendwer das Schrittziel erreicht hat. */
        val goalDays: Int,
        /** Durchschnittliche Dauer eines Trainings in Minuten. */
        val averageMinutes: Int
    )

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
     * Die Minuten der Crew in der laufenden Woche, Montag bis Sonntag.
     *
     * Nicht die letzten sieben Tage: die begannen an einem beliebigen
     * Wochentag, und eine Reihe, die mit Freitag anfaengt, liest sich wie ein
     * Zufall. Eine Woche beginnt am Montag - dann steht jeder Balken immer an
     * derselben Stelle, und man vergleicht Montag mit Montag.
     *
     * Tage ohne Training kommen mit null Minuten vor und fallen nicht weg -
     * sonst haette die Woche eine unterschiedliche Zahl Balken und man saehe
     * die Luecken nicht, um die es gerade geht. Die Tage nach heute stehen
     * ebenfalls da, leer: die Woche ist noch nicht vorbei.
     */
    fun lastWeek(snapshot: CrewSnapshot, today: LocalDate = LocalDate.now()): List<DayBar> {
        val minutesByDay = snapshot.activities
            .groupBy { ActivityTime.dayOf(it.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.duration } }

        val monday = mondayOf(today)
        return (0 until WEEK_DAYS).map { offset ->
            val day = monday.plusDays(offset.toLong())
            DayBar(day, minutesByDay[day.toString()] ?: 0, isToday = day == today)
        }
    }

    /**
     * Der Montag der Woche, in der [day] liegt - er selbst, wenn er ein
     * Montag ist.
     */
    fun mondayOf(day: LocalDate): LocalDate =
        day.minusDays(((day.dayOfWeek.value + 6) % 7).toLong())

    /**
     * Die Schritte der ganzen Crew in der laufenden Woche, Montag bis Sonntag.
     *
     * Bisher waren Schritte nur als heutiger Stand zu sehen - ein Wert, der
     * morgen wieder bei null anfaengt. Als Reihe zeigen sie, ob die Crew
     * ueberhaupt in Bewegung ist, auch an Tagen ohne Training.
     *
     * Summiert ueber die Mitglieder, wie die Minuten: ein Crew-Bildschirm zeigt
     * die Crew.
     */
    fun lastWeekSteps(snapshot: CrewSnapshot, today: LocalDate = LocalDate.now()): List<DayBar> {
        val stepsByDay = snapshot.stepDays
            .groupBy { it.day }
            .mapValues { (_, list) -> list.sumOf { it.steps } }

        val monday = mondayOf(today)
        return (0 until WEEK_DAYS).map { offset ->
            val day = monday.plusDays(offset.toLong())
            DayBar(day, stepsByDay[day.toString()] ?: 0, isToday = day == today)
        }
    }

    /**
     * Was jedes Mitglied trainiert hat, das meiste zuerst.
     *
     * Mitglieder ohne Training bleiben mit null stehen: dass jemand nichts
     * beigetragen hat, ist eine Aussage - faellt die Zeile weg, sieht es aus,
     * als gaebe es die Person nicht.
     */
    fun memberShares(snapshot: CrewSnapshot): List<MemberShare> {
        val byUser = snapshot.activities.groupBy { it.userId }

        return snapshot.members
            .map { member ->
                val own = byUser[member.id].orEmpty()
                MemberShare(
                    userId = member.id,
                    minutes = own.sumOf { it.duration },
                    workouts = own.size,
                    kilometres = own.sumOf { it.distance }
                )
            }
            // Bei Gleichstand die Kennung, damit die Reihenfolge zwischen zwei
            // Aufrufen dieselbe bleibt.
            .sortedWith(compareByDescending<MemberShare> { it.minutes }.thenBy { it.userId })
    }

    fun highlights(snapshot: CrewSnapshot, today: LocalDate = LocalDate.now()): Highlights {
        val activities = snapshot.activities
        return Highlights(
            longest = activities.maxByOrNull { it.duration },
            streakDays = streakDays(snapshot, today),
            goalDays = totals(snapshot).goalDays,
            averageMinutes =
                if (activities.isEmpty()) 0 else activities.sumOf { it.duration } / activities.size
        )
    }

    /**
     * Tage in Folge, an denen irgendwer in der Crew trainiert hat.
     *
     * Gezaehlt wird ab heute rueckwaerts - hat heute noch niemand trainiert, ab
     * gestern. Sonst stuende die Serie den ganzen Vormittag ueber auf null und
     * spraenge erst mit dem ersten Training des Tages wieder hoch, obwohl sie
     * nie unterbrochen war.
     */
    private fun streakDays(snapshot: CrewSnapshot, today: LocalDate): Int {
        val trainedOn = snapshot.activities
            .map { ActivityTime.dayOf(it.timestamp) }
            .toSet()

        var start = today
        if (today.toString() !in trainedOn) {
            start = today.minusDays(1)
            if (start.toString() !in trainedOn) return 0
        }

        var days = 0
        var day = start
        while (day.toString() in trainedOn) {
            days++
            day = day.minusDays(1)
        }
        return days
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
        val own = snapshot.activities.filter { it.userId == userId }
        val ownSteps = snapshot.stepDays.filter { it.userId == userId }

        // Muss so rechnen wie Scoreboard.build, samt Aufschlag der Serie -
        // sonst summierte sich der Balken nicht mehr zu dem Punktestand, der
        // in derselben Zeile steht.
        val activeDays = Streak.activeDays(userId, own, ownSteps)
        val workoutPoints = own.sumOf { activity ->
            val base = PointsCalculator.calculateWorkoutPoints(
                activity.duration,
                WorkoutIntensity.fromName(activity.intensity)
            )
            val day = ActivityTime.dayOf(activity.timestamp)
            if (day.isEmpty()) base
            else Streak.applyMultiplier(
                base,
                Streak.multiplierFor(Streak.endingOn(activeDays, LocalDate.parse(day)))
            )
        }

        val challengePoints = snapshot.rewards
            .filter { it.userId == userId }
            .sumOf { it.points }

        val stepPoints = StepGoal.bonusPoints(ownSteps.map { it.steps })

        return PointsSplit(workoutPoints, challengePoints, stepPoints)
    }
}
