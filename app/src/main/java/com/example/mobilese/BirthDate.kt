package com.example.mobilese

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Geburtsdatum und das daraus abgeleitete Alter.
 *
 * Das Alter wird nicht mehr getrennt eingegeben, sondern immer aus dem
 * Geburtsdatum berechnet. Vorher waren beides freie Textfelder, die
 * auseinanderlaufen konnten - und ein einmal eingetragenes Alter veraltete mit
 * jedem Geburtstag stillschweigend.
 *
 * Bewusst Calendar statt java.time: minSdk ist 24, java.time gibt es erst ab
 * API 26 und Core Library Desugaring ist im Projekt nicht aktiviert. Dieselbe
 * Begruendung wie bei [ActivityTime].
 */
object BirthDate {

    /** Anzeige- und Speicherformat, wie im Registrierungsformular angekuendigt. */
    const val PATTERN = "dd.MM.yyyy"

    /** Aelter als das ist niemand - begrenzt die Auswahl im Kalender. */
    private const val MAX_AGE_YEARS = 120

    /** Startwert des Kalenders, wenn noch kein Datum hinterlegt ist. */
    private const val DEFAULT_AGE_YEARS = 25

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.GERMANY).apply { isLenient = false }

    fun format(year: Int, monthZeroBased: Int, dayOfMonth: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, monthZeroBased, dayOfMonth)
        }
        return formatter().format(calendar.time)
    }

    /**
     * Liest ein gespeichertes Datum. Gibt null zurueck, wenn der Wert kein
     * gueltiges Datum ist - in der Datenbank koennen noch frei getippte
     * Eintraege aus der Zeit vor dem Kalenderfeld stehen.
     */
    fun parse(value: String?): Calendar? {
        if (value.isNullOrBlank()) return null
        return try {
            val parsed = formatter().parse(value) ?: return null
            Calendar.getInstance().apply { time = parsed }
        } catch (e: Exception) {
            null
        }
    }

    fun isValid(value: String?): Boolean = parse(value) != null

    /**
     * Das vollendete Lebensalter in Jahren.
     *
     * Der Geburtstag muss in diesem Jahr bereits gewesen sein, sonst zaehlt ein
     * Jahr weniger. Ein blosser Vergleich der Jahreszahlen waere fuer alle zu
     * hoch, die noch Geburtstag haben.
     *
     * Gibt null zurueck, wenn kein gueltiges Datum vorliegt oder es in der
     * Zukunft liegt.
     */
    fun ageFrom(value: String?, today: Calendar = Calendar.getInstance()): Int? {
        val birth = parse(value) ?: return null
        if (birth.after(today)) return null

        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)

        val birthMonth = birth.get(Calendar.MONTH)
        val todayMonth = today.get(Calendar.MONTH)
        val birthdayStillAhead = todayMonth < birthMonth ||
                (todayMonth == birthMonth &&
                        today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))
        if (birthdayStillAhead) age--

        return age.coerceAtLeast(0)
    }

    /** Das Alter als Text fuer das Profilfeld, leer wenn kein Datum vorliegt. */
    fun ageTextFrom(value: String?): String = ageFrom(value)?.toString() ?: ""

    /** Vorbelegung des Kalenders: das hinterlegte Datum, sonst ein Startwert. */
    fun calendarFor(value: String?): Calendar =
        parse(value) ?: Calendar.getInstance().apply {
            add(Calendar.YEAR, -DEFAULT_AGE_YEARS)
        }

    /** Frueheste im Kalender waehlbare Zeit. */
    fun earliestSelectableMillis(): Long =
        Calendar.getInstance().apply { add(Calendar.YEAR, -MAX_AGE_YEARS) }.timeInMillis
}
