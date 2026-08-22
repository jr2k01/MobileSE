package com.example.mobilese

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.math.roundToInt

/**
 * Die Arten von Team-Challenges.
 *
 * Jede traegt selbst, was sie ueber sich weiss: wie sie heisst, in welcher
 * Einheit gezaehlt wird, wie gross ein sinnvolles Ziel ist und wie viele Punkte
 * es dafuer gibt. Vorher stand das verteilt in when-Bloecken im Rechner, in der
 * Activity und in den Layouts - eine neue Art hinzuzufuegen hiess, vier Stellen
 * zu finden.
 *
 * Der Name wird als Text in der Datenbank gespeichert. Er darf sich deshalb
 * nicht aendern; die Beschriftung liegt in den Sprachdateien und ist frei.
 *
 * @param rewardMultiplier Ziel mal diesem Faktor ergibt den Belohnungstopf. Die
 *        Faktoren sind so gewaehlt, dass ein uebliches Ziel bei rund hundert
 *        Punkten landet - sonst waere eine Schritt-Challenge mit ihren
 *        sechsstelligen Zielen unbezahlbar und eine ueber Trainingstage
 *        wertlos.
 * @param maxGoal Obergrenze der Eingabe. Haengt an der Einheit: zwanzig
 *        Trainingstage sind viel, zwanzigtausend Schritte sind ein Nachmittag.
 */
enum class ChallengeType(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    /** "12 / 50 km" auf der Karte. */
    @StringRes val progressRes: Int,
    /** "12 km" in der Zeile eines Mitglieds. */
    @StringRes val contributionRes: Int,
    /** Beschriftung des Zielfeldes, mit der Einheit. */
    @StringRes val goalHintRes: Int,
    val rewardMultiplier: Double,
    val maxGoal: Int
) {
    /** Kilometer, nur aus Laufeinheiten. */
    DISTANCE(
        R.string.challenge_type_running,
        R.drawable.ic_sport_running,
        R.string.progress_km,
        R.string.contribution_km,
        R.string.challenge_goal_hint_km,
        rewardMultiplier = 2.0,
        maxGoal = 10_000
    ),

    /** Einheiten im Studio. */
    WORKOUT_COUNT(
        R.string.challenge_type_gym,
        R.drawable.ic_sport_gym,
        R.string.progress_sessions,
        R.string.contribution_sessions,
        R.string.challenge_goal_hint_sessions,
        rewardMultiplier = 5.0,
        maxGoal = 1_000
    ),

    /**
     * Punkte aus Workouts, unabhaengig von der Sportart.
     *
     * Gezaehlt werden nur die Punkte aus dem Training selbst - nicht die aus
     * Challenges und nicht der Schrittbonus. Sonst zaehlte die Belohnung einer
     * Challenge auf die naechste ein, und eine Challenge koennte sich am Ende
     * selbst erfuellen.
     */
    POINTS(
        R.string.challenge_type_points,
        R.drawable.ic_leaderboard,
        R.string.progress_points,
        R.string.contribution_points,
        R.string.challenge_goal_hint_points,
        rewardMultiplier = 0.25,
        maxGoal = 20_000
    ),

    /** Trainingszeit, unabhaengig von der Sportart. */
    MINUTES(
        R.string.challenge_type_minutes,
        R.drawable.ic_calendar,
        R.string.progress_minutes,
        R.string.contribution_minutes,
        R.string.challenge_goal_hint_minutes,
        rewardMultiplier = 0.1,
        maxGoal = 100_000
    ),

    /** Schritte aus Health Connect, zusammengezaehlt ueber die Crew. */
    STEPS(
        R.string.challenge_type_steps,
        R.drawable.ic_steps,
        R.string.progress_steps,
        R.string.contribution_steps,
        R.string.challenge_goal_hint_steps,
        rewardMultiplier = 0.0005,
        maxGoal = 5_000_000
    ),

    /**
     * Tage, an denen trainiert wurde, ueber alle Mitglieder zusammengezaehlt.
     *
     * Zwei Mitglieder am selben Tag sind zwei Trainingstage, nicht einer. Nur
     * so ergibt die Summe der Beitraege wieder den Gesamtstand - und nur so
     * traegt jeder sichtbar bei. Belohnt wird damit Regelmaessigkeit statt
     * Umfang: zehnmal zwanzig Minuten schlaegt einmal drei Stunden.
     */
    ACTIVE_DAYS(
        R.string.challenge_type_days,
        R.drawable.ic_check,
        R.string.progress_days,
        R.string.contribution_days,
        R.string.challenge_goal_hint_days,
        rewardMultiplier = 5.0,
        maxGoal = 500
    );

    companion object {
        /**
         * Liest den in der Datenbank gespeicherten Typ.
         *
         * Alles Unbekannte ergibt DISTANCE statt einer Ausnahme. Darunter
         * faellt auch "Running", der Typname einer frueheren Fassung, der in
         * bestehenden Zeilen noch steht - und genau eine Distanz-Challenge
         * meint. Eine einzelne unlesbare Zeile darf den Bildschirm nicht
         * kippen.
         */
        fun fromStored(stored: String?): ChallengeType =
            entries.firstOrNull { it.name == stored } ?: DISTANCE
    }
}

object ChallengeCalculator {

    /**
     * Der gesamte Belohnungstopf einer Challenge.
     *
     * Mindestens ein Punkt, solange ueberhaupt ein Ziel gesetzt ist: bei den
     * kleinen Faktoren - Schritte rechnen mit 0,0005 - ergaebe ein niedriges
     * Ziel sonst null Punkte, und eine Challenge ohne Belohnung wird nie
     * ausgeschuettet. Ein Ziel von null bleibt bei null; das ist keine
     * Challenge.
     */
    fun calculateTotalChallengePoints(type: ChallengeType, targetGoal: Double): Int {
        if (targetGoal <= 0.0) return 0
        return (targetGoal * type.rewardMultiplier).roundToInt().coerceAtLeast(1)
    }

    /**
     * Verteilt den Belohnungstopf nach Leistung.
     *
     * Wer neunzig Prozent des Ziels geschafft hat, bekommt neunzig Prozent der
     * Punkte. Vorher wurde gleichmaessig geteilt, also bekam auch etwas, wer
     * nichts beigetragen hatte - bei einer Challenge, die eine Person allein
     * gestemmt hat, war das die Haelfte an jemanden, der zugesehen hat.
     *
     * Der Topf wird **auf den Punkt genau** ausgeschuettet. Einzeln zu runden
     * ergaebe je nach Aufteilung ein oder zwei Punkte zu viel oder zu wenig -
     * bei einem Topf von 100 sind das sichtbare Prozente. Deshalb bekommt
     * zuerst jeder seinen abgerundeten Anteil, und die dabei liegengebliebenen
     * Punkte gehen an die mit dem groessten abgeschnittenen Rest. Bei gleichem
     * Rest entscheidet der groessere Beitrag, danach die Reihenfolge - damit
     * dieselbe Eingabe immer dieselbe Aufteilung ergibt.
     *
     * @param contributions Beitraege in der Reihenfolge der Mitglieder.
     * @return Punkte in derselben Reihenfolge. Wer nichts beigetragen hat,
     *         bekommt null.
     */
    fun shareOut(totalPoints: Int, contributions: List<Int>): List<Int> {
        val positive = contributions.map { it.coerceAtLeast(0) }
        val total = positive.sum()
        if (totalPoints <= 0 || total <= 0) return List(contributions.size) { 0 }

        val exact = positive.map { totalPoints.toDouble() * it / total }
        val shares = exact.map { it.toInt() }.toMutableList()

        // Die Punkte, die beim Abrunden liegengeblieben sind.
        var left = totalPoints - shares.sum()

        // Nur, wer beigetragen hat: sonst faenden sich Restpunkte bei
        // jemandem wieder, der nichts getan hat.
        val order = exact.indices
            .filter { positive[it] > 0 }
            .sortedWith(
                compareByDescending<Int> { exact[it] - shares[it] }
                    .thenByDescending { positive[it] }
                    .thenBy { it }
            )

        // Reihum, falls eine Runde nicht reicht - das kommt bei vielen
        // Mitgliedern und einem kleinen Topf vor.
        var index = 0
        while (left > 0) {
            shares[order[index % order.size]]++
            left--
            index++
        }

        return shares
    }

    /**
     * Erkennt Distanz-Challenges. "Running" ist der Typname aus einer frueheren
     * Version und steht noch in bestehenden Datensaetzen.
     */
    fun isDistanceChallenge(type: String): Boolean =
        type == ChallengeType.DISTANCE.name || type == Sports.RUNNING

    /**
     * Der Beitrag einer Person zu einer Challenge.
     *
     * Bekommt bereits gefilterte Daten: nur die dieser Person, und nur die, die
     * innerhalb der Frist liegen. Die Auswahl trifft [ChallengeManager], hier
     * wird nur gezaehlt.
     *
     * Bei Distanz-Challenges werden die Kilometer als Kommazahlen summiert und
     * erst am Ende abgeschnitten. Vorher wurde jede Aktivitaet einzeln auf eine
     * ganze Zahl gekuerzt, wodurch drei Laeufe von je 900 Metern als null
     * Kilometer zaehlten statt als zwei.
     */
    fun progressOf(
        type: ChallengeType,
        activities: List<Activity>,
        stepDays: List<StepDay> = emptyList()
    ): Int = when (type) {
        ChallengeType.DISTANCE -> activities.sumOf { it.distance }.toInt()

        ChallengeType.WORKOUT_COUNT -> activities.count { it.sport == Sports.GYM }

        ChallengeType.POINTS -> activities.sumOf {
            PointsCalculator.calculateWorkoutPoints(
                it.duration,
                WorkoutIntensity.fromName(it.intensity)
            )
        }

        ChallengeType.MINUTES -> activities.sumOf { it.duration }

        ChallengeType.STEPS -> stepDays.sumOf { it.steps }

        // Mehrere Workouts an einem Tag sind ein Trainingstag. Ein Eintrag mit
        // unlesbarem Zeitstempel gehoert zu keinem Tag und faellt heraus, statt
        // als eigener zu zaehlen.
        ChallengeType.ACTIVE_DAYS -> activities
            .map { ActivityTime.dayOf(it.timestamp) }
            .filter { it.isNotEmpty() }
            .distinct()
            .size
    }
}
