package com.example.mobilese

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Zentrale Behandlung von Zeitstempeln fuer Aktivitaeten.
 *
 * Gespeichert wird ausschliesslich im ISO-8601-Format (yyyy-MM-dd'T'HH:mm:ss),
 * weil dieses Format lexikografisch in derselben Reihenfolge sortiert wie
 * chronologisch. Das vorher verwendete deutsche Format "dd.MM.yyyy HH:mm"
 * sortierte als Text zuerst nach Tag und erst danach nach Monat und Jahr,
 * wodurch der Aktivitaets-Feed bei Eintraegen aus mehreren Monaten eine
 * falsche Reihenfolge zeigte.
 *
 * Die Anzeige wird erst in der UI ins deutsche Format uebersetzt.
 * Alte Datensaetze im deutschen Format werden weiterhin gelesen.
 *
 * Verwendet SimpleDateFormat statt java.time. Der Grund dafuer ist entfallen:
 * java.time gibt es ab API 26, und seit der Anbindung an Health Connect liegt
 * minSdk dort. Umgestellt ist es noch nicht - hier steht funktionierender, von
 * Tests abgedeckter Code, und eine Umstellung ohne Anlass birgt mehr Risiko als
 * Nutzen. Neuer Code darf java.time nehmen, [HealthSteps] tut es bereits.
 */
object ActivityTime {

    private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    private const val DISPLAY_PATTERN = "dd.MM.yyyy HH:mm"

    private val berlin: TimeZone get() = TimeZone.getTimeZone("Europe/Berlin")

    private fun isoFormat() = SimpleDateFormat(ISO_PATTERN, Locale.GERMANY).apply { timeZone = berlin }
    private fun displayFormat() = SimpleDateFormat(DISPLAY_PATTERN, Locale.GERMANY).apply { timeZone = berlin }

    /** Aktueller Zeitpunkt im Speicherformat. */
    fun now(): String = isoFormat().format(Date())

    /**
     * Wandelt einen gespeicherten Zeitstempel in die Anzeigeform um.
     * Faellt auf den Rohwert zurueck, wenn das Format unbekannt ist.
     */
    fun toDisplay(stored: String): String {
        if (stored.isEmpty()) return ""
        parseIso(stored)?.let { return displayFormat().format(it) }
        parseLegacy(stored)?.let { return displayFormat().format(it) }
        return stored
    }

    /**
     * Liefert einen Schluessel, nach dem sich Zeitstempel korrekt sortieren
     * lassen. Alte Datensaetze im deutschen Format werden dafuer nach ISO
     * umgerechnet, damit gemischte Bestaende richtig sortieren.
     */
    fun sortKey(stored: String): String {
        parseIso(stored)?.let { return isoFormat().format(it) }
        parseLegacy(stored)?.let { return isoFormat().format(it) }
        return ""
    }

    private fun parseIso(value: String): Date? = try {
        isoFormat().apply { isLenient = false }.parse(value)
    } catch (e: Exception) {
        null
    }

    private fun parseLegacy(value: String): Date? = try {
        displayFormat().apply { isLenient = false }.parse(value)
    } catch (e: Exception) {
        null
    }
}
