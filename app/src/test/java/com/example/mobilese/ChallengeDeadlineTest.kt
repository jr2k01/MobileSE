package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit-Tests fuer die Frist einer Challenge.
 *
 * Der wichtigste Fall ist der Stichtag selbst: an ihm zaehlt ein Training noch,
 * am Tag danach nicht mehr. Ein Fehler um einen Tag waere hier nicht kosmetisch,
 * sondern haette Punkte zur Folge, die es nicht geben duerfte - oder umgekehrt.
 */
class ChallengeDeadlineTest {

    private val deadline = "2026-08-31"
    private val theDay = LocalDate.of(2026, 8, 31)

    // === Was noch zaehlt ===

    @Test
    fun `a workout on the deadline itself still counts`() {
        assertTrue(ChallengeDeadline.countsTowards(deadline, "2026-08-31T23:30:00"))
    }

    @Test
    fun `a workout before the deadline counts`() {
        assertTrue(ChallengeDeadline.countsTowards(deadline, "2026-08-01T07:00:00"))
    }

    @Test
    fun `a workout after the deadline does not count`() {
        assertFalse(ChallengeDeadline.countsTowards(deadline, "2026-09-01T00:10:00"))
    }

    /** Bestehende Challenges haben keine Frist und duerfen sich nicht aendern. */
    @Test
    fun `without a deadline everything counts`() {
        assertTrue(ChallengeDeadline.countsTowards(null, "2026-09-01T00:10:00"))
        assertTrue(ChallengeDeadline.countsTowards("", "2020-01-01T00:00:00"))
    }

    /** Ein unlesbarer Zeitstempel darf eine abgelaufene Frist nicht retten. */
    @Test
    fun `an unreadable timestamp does not count towards a deadline`() {
        assertFalse(ChallengeDeadline.countsTowards(deadline, "irgendwas"))
        // Ohne Frist bleibt es dabei, dass alles zaehlt.
        assertTrue(ChallengeDeadline.countsTowards(null, "irgendwas"))
    }

    // === Abgelaufen oder nicht ===

    @Test
    fun `the deadline is not over on the day itself`() {
        assertFalse(ChallengeDeadline.isOver(deadline, theDay))
    }

    @Test
    fun `the deadline is over on the next day`() {
        assertTrue(ChallengeDeadline.isOver(deadline, theDay.plusDays(1)))
    }

    @Test
    fun `a challenge without a deadline never runs out`() {
        assertFalse(ChallengeDeadline.isOver(null, theDay.plusYears(5)))
    }

    // === Verbleibende Tage ===

    @Test
    fun `days left counts down to zero on the day itself`() {
        assertEquals(7L, ChallengeDeadline.daysLeft(deadline, theDay.minusDays(7)))
        assertEquals(1L, ChallengeDeadline.daysLeft(deadline, theDay.minusDays(1)))
        assertEquals(0L, ChallengeDeadline.daysLeft(deadline, theDay))
        assertEquals(-1L, ChallengeDeadline.daysLeft(deadline, theDay.plusDays(1)))
    }

    @Test
    fun `without a deadline there are no days left to show`() {
        assertEquals(null, ChallengeDeadline.daysLeft(null, theDay))
    }

    // === Anzeige ===

    @Test
    fun `the deadline is shown in german notation`() {
        assertEquals("31.08.2026", ChallengeDeadline.toDisplay(deadline))
        assertEquals("", ChallengeDeadline.toDisplay(null))
        assertEquals("", ChallengeDeadline.toDisplay("kein datum"))
    }
}
