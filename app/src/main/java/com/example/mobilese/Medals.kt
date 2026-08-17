package com.example.mobilese

import java.time.LocalDate

/**
 * Entscheidet, welche Medaillen ein Mitglied verdient hat.
 *
 * Wie bei den Bonuspunkten wird nichts gutgeschrieben, sondern aus den
 * vorhandenen Daten *abgeleitet*. Eine Medaille kann so nicht verloren gehen und
 * nicht doppelt vergeben werden, und es braucht keine Tabelle, in der steht, wer
 * was schon hat. Wird spaeter eine Bedingung geaendert, gilt sie rueckwirkend
 * fuer alle - was bei Auszeichnungen richtig ist: sie sollen ueberall dasselbe
 * bedeuten.
 *
 * Reine Logik ohne Android-Bezug, also ohne Emulator testbar.
 */
object Medals {

    /** Aufeinanderfolgende Tage mit erreichtem Schrittziel fuer die Serie. */
    const val STREAK_DAYS = 10

    const val TEN = 10
    const val FIFTY = 50

    /** Die klassische Marathondistanz in Kilometern. */
    const val MARATHON_KM = 42.195

    /**
     * Alle Medaillen eines Mitglieds - erreichte wie noch offene.
     *
     * Auch die offenen zurueckzugeben ist Absicht: das Profil zeigt sie
     * abgeblendet mit, damit sichtbar ist, was es ueberhaupt zu holen gibt.
     */
    fun statusFor(userId: String, snapshot: CrewSnapshot): Map<Medal, Boolean> {
        val earned = earnedBy(userId, snapshot)
        return Medal.entries.associateWith { it in earned }
    }

    fun earnedBy(userId: String, snapshot: CrewSnapshot): Set<Medal> {
        val activities = snapshot.activities.filter { it.userId == userId }
        val stepDays = snapshot.stepDays.filter { it.userId == userId }
        val rewards = snapshot.rewards.filter { it.userId == userId }

        val goalDays = stepDays.filter { StepGoal.isReached(it.steps.toLong()) }
        val totalKm = activities.sumOf { it.distance }

        return Medal.entries.filterTo(mutableSetOf()) { medal ->
            when (medal) {
                Medal.FIRST_WORKOUT -> activities.isNotEmpty()
                Medal.TEN_WORKOUTS -> activities.size >= TEN
                Medal.FIFTY_WORKOUTS -> activities.size >= FIFTY
                Medal.FIRST_STEP_GOAL -> goalDays.isNotEmpty()
                Medal.STEP_STREAK -> longestStreak(goalDays.map { it.day }) >= STREAK_DAYS
                Medal.CHALLENGE_WINNER -> rewards.isNotEmpty()
                Medal.MARATHON -> totalKm >= MARATHON_KM
            }
        }
    }

    /**
     * Die laengste Reihe unmittelbar aufeinanderfolgender Kalendertage.
     *
     * Doppelte Tage koennen nicht mitzaehlen - sonst haette eine zweimal
     * gespeicherte Zeile die Serie verlaengert. Unlesbare Datumsangaben werden
     * uebergangen statt zu einem Absturz zu fuehren: die Zeilen kommen aus der
     * Datenbank und koennen von Hand geaendert worden sein.
     */
    fun longestStreak(days: List<String>): Int {
        val dates = days.mapNotNull(::parseDay).distinct().sorted()
        if (dates.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (i in 1 until dates.size) {
            current = if (dates[i - 1].plusDays(1) == dates[i]) current + 1 else 1
            if (current > longest) longest = current
        }
        return longest
    }

    private fun parseDay(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: Exception) {
        null
    }
}
