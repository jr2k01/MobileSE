package com.example.mobilese

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Die Frist einer Challenge: bis wann sie geschafft sein muss.
 *
 * Eine Challenge ohne Frist laeuft weiter wie bisher - deshalb ist ueberall
 * null erlaubt und bedeutet "keine Frist". Bestehende Challenges haben keine
 * und aendern ihr Verhalten dadurch nicht.
 *
 * Reine Logik ueber Datumsangaben, ohne Android und ohne Datenbank, damit sich
 * gerade der Grenzfall - der letzte Tag - ohne Emulator pruefen laesst.
 *
 * Gespeichert wird als ISO-Datum ("2026-08-31"), angezeigt deutsch. ISO, weil
 * es in der Datenbank in einer date-Spalte steht und weil sich ISO-Daten als
 * Text in derselben Reihenfolge vergleichen lassen wie chronologisch - genau
 * das braucht der Vergleich mit dem Tag einer Aktivitaet.
 */
object ChallengeDeadline {

    private val display = DateTimeFormatter.ofPattern(BirthDate.PATTERN)

    /** Liest eine gespeicherte Frist, oder null, wenn keine oder unlesbar. */
    fun parse(deadline: String?): LocalDate? {
        if (deadline.isNullOrBlank()) return null
        return try {
            LocalDate.parse(deadline)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ob eine Aktivitaet noch auf die Challenge einzahlt.
     *
     * Der Stichtag zaehlt mit: wer am letzten Tag noch laeuft, hat es
     * geschafft. Ohne Frist zaehlt alles.
     *
     * Laesst sich der Zeitstempel nicht lesen, zaehlt die Aktivitaet nicht -
     * bei einer Frist ist das die vorsichtigere Antwort, denn sonst koennte ein
     * kaputter Eintrag eine abgelaufene Challenge doch noch erfuellen.
     */
    fun countsTowards(deadline: String?, timestamp: String): Boolean {
        val limit = parse(deadline) ?: return true
        val day = ActivityTime.dayOf(timestamp)
        if (day.isEmpty()) return false
        return day <= limit.toString()
    }

    /**
     * Wie [countsTowards], aber fuer Daten, die ihren Tag schon als ISO-Datum
     * tragen - etwa die Schrittzahl. Erspart den Umweg ueber einen
     * Zeitstempel, den es dort gar nicht gibt.
     */
    fun countsOnDay(deadline: String?, day: String): Boolean {
        val limit = parse(deadline) ?: return true
        if (day.isEmpty()) return false
        return day <= limit.toString()
    }

    /**
     * Ob die Frist abgelaufen ist. Am Stichtag selbst noch nicht - erst am Tag
     * danach.
     */
    fun isOver(deadline: String?, today: LocalDate = LocalDate.now()): Boolean {
        val limit = parse(deadline) ?: return false
        return today.isAfter(limit)
    }

    /**
     * Verbleibende Tage, oder null ohne Frist. Am Stichtag null Tage, danach
     * negativ.
     */
    fun daysLeft(deadline: String?, today: LocalDate = LocalDate.now()): Long? {
        val limit = parse(deadline) ?: return null
        return ChronoUnit.DAYS.between(today, limit)
    }

    /** Die Frist in deutscher Schreibweise, oder leer ohne Frist. */
    fun toDisplay(deadline: String?): String =
        parse(deadline)?.format(display).orEmpty()

    /** Fuer den Kalender, der in UTC-Millisekunden rechnet. */
    fun toUtcMillis(deadline: String?, fallback: LocalDate): Long =
        (parse(deadline) ?: fallback).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcMillis(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
}
