package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer die Zeitstempel-Behandlung.
 *
 * Der wichtigste Test ist `sorting is chronological across month boundaries`:
 * Er haelt genau den Fehler fest, der vor der Bereinigung im Aktivitaets-Feed
 * steckte. Zeitstempel wurden im Format "dd.MM.yyyy HH:mm" gespeichert und
 * als Text sortiert, wodurch zuerst nach Tag und erst danach nach Monat und
 * Jahr sortiert wurde.
 */
class ActivityTimeTest {

    @Test
    fun `iso timestamps are rendered in german display format`() {
        assertEquals("13.08.2026 18:30", ActivityTime.toDisplay("2026-08-13T18:30:00"))
    }

    @Test
    fun `legacy german timestamps are still readable`() {
        assertEquals("13.08.2026 18:30", ActivityTime.toDisplay("13.08.2026 18:30"))
    }

    @Test
    fun `unknown and empty values are passed through unchanged`() {
        assertEquals("", ActivityTime.toDisplay(""))
        assertEquals("not a date", ActivityTime.toDisplay("not a date"))
    }

    @Test
    fun `sorting is chronological across month boundaries`() {
        val august = ActivityTime.sortKey("2026-08-13T18:00:00")
        val september = ActivityTime.sortKey("2026-09-05T08:00:00")

        // Der 5. September liegt nach dem 13. August. Bei einem Textvergleich
        // der Anzeigeform ("13.08.2026" vs "05.09.2026") waere es umgekehrt.
        assertTrue("september must sort after august", september > august)
        assertTrue("display format sorts wrong - this is the bug we fixed", "13.08.2026" > "05.09.2026")
    }

    @Test
    fun `legacy and iso timestamps sort correctly against each other`() {
        val legacyAugust = ActivityTime.sortKey("13.08.2026 18:00")
        val isoSeptember = ActivityTime.sortKey("2026-09-05T08:00:00")

        assertTrue(isoSeptember > legacyAugust)
    }

    @Test
    fun `now produces a value that can be parsed back`() {
        val now = ActivityTime.now()
        assertTrue("now() must be sortable", ActivityTime.sortKey(now).isNotEmpty())
        assertTrue("now() must be displayable", ActivityTime.toDisplay(now).contains("."))
    }
}
