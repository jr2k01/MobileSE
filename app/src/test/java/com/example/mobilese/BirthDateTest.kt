package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit-Tests fuer Geburtsdatum und Altersberechnung.
 *
 * Der wichtigste Fall ist `birthday later this year`: wer dieses Jahr noch
 * Geburtstag hat, ist ein Jahr juenger als die blosse Differenz der
 * Jahreszahlen. Genau hier liegt der Fehler, den man beim Rechnen mit
 * Jahreszahlen leicht macht.
 */
class BirthDateTest {

    /** Fester Bezugstag, damit die Tests nicht vom heutigen Datum abhaengen. */
    private fun on(year: Int, monthZeroBased: Int, day: Int): Calendar =
        Calendar.getInstance().apply { clear(); set(year, monthZeroBased, day) }

    @Test
    fun `age counts only completed years`() {
        // Geburtstag war schon: 15.03. liegt vor dem 14.08.
        assertEquals(26, BirthDate.ageFrom("15.03.2000", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `birthday later this year does not count yet`() {
        // 15.11. kommt erst noch - also 25, nicht 26.
        assertEquals(25, BirthDate.ageFrom("15.11.2000", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `on the birthday itself the new year counts`() {
        assertEquals(26, BirthDate.ageFrom("14.08.2000", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `the day before the birthday it does not`() {
        assertEquals(25, BirthDate.ageFrom("15.08.2000", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `someone born today is zero`() {
        assertEquals(0, BirthDate.ageFrom("14.08.2026", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `a date in the future has no age`() {
        assertNull(BirthDate.ageFrom("01.01.2030", on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `unparseable and empty values are rejected instead of throwing`() {
        assertNull(BirthDate.ageFrom(null))
        assertNull(BirthDate.ageFrom(""))
        assertNull(BirthDate.ageFrom("irgendwas"))
        // Freitext-Altbestaende aus der Zeit vor dem Kalenderfeld
        assertNull(BirthDate.ageFrom("2000-03-15"))
        assertEquals("", BirthDate.ageTextFrom("keine Angabe"))
    }

    @Test
    fun `impossible dates are not silently rolled over`() {
        // Ohne isLenient=false wuerde daraus der 01.03. bzw. der Januar 2001.
        assertFalse(BirthDate.isValid("30.02.2000"))
        assertFalse(BirthDate.isValid("01.13.2000"))
        assertTrue(BirthDate.isValid("29.02.2000"))
    }

    @Test
    fun `formatting produces the documented pattern and can be read back`() {
        val formatted = BirthDate.format(2000, Calendar.MARCH, 15)

        assertEquals("15.03.2000", formatted)
        assertEquals(26, BirthDate.ageFrom(formatted, on(2026, Calendar.AUGUST, 14)))
    }

    @Test
    fun `the calendar starts at the stored date when there is one`() {
        val start = BirthDate.calendarFor("15.03.2000")

        assertEquals(2000, start.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, start.get(Calendar.MONTH))
        assertEquals(15, start.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * Der Material-Kalender rechnet in UTC, das Geraet steht in einer anderen
     * Zeitzone. Wird das ueberlesen, kommt ein anderes Datum zurueck als das
     * angetippte - am deutlichsten am Monatsersten, der zum Letzten des
     * Vormonats wird. Deshalb Hin- und Rueckweg fuer mehrere Zeitzonen.
     */
    @Test
    fun `a date survives the trip through the calendar in every time zone`() {
        val zones = listOf("UTC", "Europe/Berlin", "Pacific/Kiritimati", "Pacific/Midway")
        val dates = listOf("01.01.2000", "31.12.1999", "29.02.2004", "15.03.2000")
        val original = TimeZone.getDefault()

        try {
            zones.forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                dates.forEach { date ->
                    assertEquals(
                        "$date in $zone",
                        date,
                        BirthDate.fromUtcMillis(BirthDate.toUtcMillis(date))
                    )
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `the selectable range covers a lifetime and stops today`() {
        val earliest = BirthDate.earliestSelectableUtcMillis()
        val latest = BirthDate.latestSelectableUtcMillis()

        assertTrue(earliest < latest)
        // Heute ist waehlbar, morgen nicht mehr.
        assertEquals(
            BirthDate.format(
                Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH),
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            ),
            BirthDate.fromUtcMillis(latest)
        )
        assertEquals(120, BirthDate.ageFrom(BirthDate.fromUtcMillis(earliest)))
    }
}
