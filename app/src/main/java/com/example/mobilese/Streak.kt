package com.example.mobilese

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Die persoenliche Serie: Tage in Folge, an denen etwas getan wurde.
 *
 * Ein Tag zaehlt, wenn an ihm ein Workout eingetragen **oder** das Schrittziel
 * erreicht wurde. Beides zusammen zu verlangen waere zu streng - an einem Tag
 * mit zwei Stunden Training laeuft man nicht zwangslaeufig zehntausend
 * Schritte, und umgekehrt.
 *
 * Wer eine Serie haelt, bekommt ab der ersten Stufe einen Aufschlag auf die
 * Punkte, die er an diesem Tag ertrainiert. Der Aufschlag gilt **je Tag mit dem
 * Stand von damals**: Punkte vom Dienstag werden nicht nachtraeglich mehr, weil
 * die Serie bis Freitag weitergelaufen ist. Sonst waere der Punktestand nicht
 * mehr das, was man sich an dem Tag verdient hat, sondern ein bewegliches Ziel.
 *
 * Reine Logik ueber Datumsangaben, ohne Android und ohne Datenbank - die
 * Stufengrenzen und der Umgang mit Luecken lassen sich damit ohne Emulator
 * pruefen.
 */
object Streak {

    /** Eine Stufe: ab [days] Tagen in Folge gilt [multiplier]. */
    data class Tier(val days: Int, val multiplier: Double)

    /**
     * Die Stufen, laengste zuerst - so trifft die erste passende.
     *
     * Bewusst flach: der hoechste Aufschlag ist die Haelfte obendrauf. Wer
     * einen Monat durchhaelt, soll vorne liegen, aber nicht uneinholbar sein -
     * sonst lohnt sich fuer alle anderen das Mitmachen nicht mehr.
     */
    val TIERS = listOf(
        Tier(30, 1.5),
        Tier(20, 1.3),
        Tier(10, 1.2),
        Tier(5, 1.1)
    )

    /** Ab so vielen Tagen gibt es ueberhaupt einen Aufschlag. */
    val FIRST_TIER_DAYS = TIERS.minOf { it.days }

    /**
     * Die Tage, an denen jemand aktiv war, als ISO-Datum.
     *
     * Aktivitaeten mit unlesbarem Zeitstempel gehoeren zu keinem Tag und
     * fallen heraus, statt als eigener zu zaehlen.
     */
    fun activeDays(
        userId: String,
        activities: List<Activity>,
        stepDays: List<StepDay>
    ): Set<String> {
        val trained = activities
            .filter { it.userId == userId }
            .map { ActivityTime.dayOf(it.timestamp) }
            .filter { it.isNotEmpty() }

        val walked = stepDays
            .filter { it.userId == userId && StepGoal.isReached(it.steps.toLong()) }
            .map { it.day }

        return (trained + walked).toSet()
    }

    /**
     * Die Laenge der Serie, die an [day] endet - also einschliesslich [day]
     * selbst. Null, wenn an dem Tag nichts war.
     */
    fun endingOn(activeDays: Set<String>, day: LocalDate): Int {
        var length = 0
        var current = day
        while (current.toString() in activeDays) {
            length++
            current = current.minusDays(1)
        }
        return length
    }

    /**
     * Die Serie, die heute zaehlt.
     *
     * Ist heute noch nichts eingetragen, zaehlt die Serie bis gestern weiter.
     * Andernfalls stuende sie jeden Morgen auf null und spraenge erst mit dem
     * ersten Workout des Tages zurueck - und wer abends trainiert, saehe den
     * ganzen Tag lang eine Serie, die er gar nicht verloren hat.
     */
    fun current(activeDays: Set<String>, today: LocalDate = LocalDate.now()): Int {
        val fromToday = endingOn(activeDays, today)
        if (fromToday > 0) return fromToday
        return endingOn(activeDays, today.minusDays(1))
    }

    /** Der Aufschlag bei dieser Serienlaenge. Ohne erreichte Stufe schlicht 1. */
    fun multiplierFor(streakDays: Int): Double =
        TIERS.firstOrNull { streakDays >= it.days }?.multiplier ?: 1.0

    /**
     * Wie viele Tage noch bis zur naechsten Stufe fehlen, oder null wenn die
     * hoechste erreicht ist.
     */
    fun daysToNextTier(streakDays: Int): Int? =
        TIERS.map { it.days }.filter { it > streakDays }.minOrNull()?.minus(streakDays)

    /**
     * Punkte mit dem Aufschlag des Tages, kaufmaennisch gerundet.
     *
     * Gerundet und nicht abgeschnitten: bei kleinen Punktzahlen fiele ein
     * Aufschlag von zehn Prozent sonst regelmaessig unter den Tisch.
     */
    fun applyMultiplier(basePoints: Int, multiplier: Double): Int =
        (basePoints * multiplier).roundToInt()
}
