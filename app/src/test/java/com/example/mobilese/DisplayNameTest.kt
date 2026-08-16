package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer das Kuerzel, unter dem ein Mitglied in der Crew erscheint.
 *
 * Wichtig ist vor allem die Reihenfolge der Rueckfaelle: bestehende Konten haben
 * kein Kuerzel, und in der Rangliste darf trotzdem keine leere Zeile stehen.
 */
class DisplayNameTest {

    @Test
    fun `the chosen short name wins over everything else`() {
        assertEquals(
            "JR",
            DisplayName.resolve("JR", "Jannik Rikazewski", "jannik@example.com")
        )
    }

    @Test
    fun `without a short name the full name is shortened`() {
        assertEquals(
            "Jannik R.",
            DisplayName.resolve(null, "Jannik Rikazewski", "jannik@example.com")
        )
        // Auch ein leeres oder nur aus Leerzeichen bestehendes Feld zaehlt nicht.
        assertEquals("Jannik R.", DisplayName.resolve("   ", "Jannik Rikazewski", null))
    }

    @Test
    fun `without any name the mail address carries it`() {
        assertEquals("jannik", DisplayName.resolve(null, null, "jannik@example.com"))
        assertEquals("jannik", DisplayName.resolve(null, "", "jannik@example.com"))
    }

    @Test
    fun `with nothing at all the result is empty so the caller can say Unknown`() {
        assertEquals("", DisplayName.resolve(null, null, null))
        assertEquals("", DisplayName.resolve("", "  ", ""))
    }

    @Test
    fun `shortening keeps a single name and handles extra spaces`() {
        assertEquals("Cher", DisplayName.shorten("Cher"))
        assertEquals("Jannik R.", DisplayName.shorten("  Jannik   Rikazewski  "))
        // Drei Teile: der Anfangsbuchstabe kommt vom Nachnamen, nicht vom
        // zweiten Vornamen.
        assertEquals("Ana S.", DisplayName.shorten("Ana Maria Silva"))
        assertEquals(null, DisplayName.shorten("   "))
        assertEquals(null, DisplayName.shorten(null))
    }

    /** Ein bereits abgekuerzter Nachname soll nicht zu "R.." werden. */
    @Test
    fun `an already abbreviated surname is left alone`() {
        assertEquals("Jannik R.", DisplayName.shorten("Jannik R."))
    }

    @Test
    fun `a short name has to fit into a ranking row`() {
        assertTrue(DisplayName.isValid("JR"))
        assertTrue(DisplayName.isValid("Jannik R."))
        assertTrue(DisplayName.isValid("Der Boss"))

        assertFalse("zu kurz", DisplayName.isValid("J"))
        assertFalse("zu lang", DisplayName.isValid("Maximilian Mustermann"))
        assertFalse("leer", DisplayName.isValid("   "))
        assertFalse("Zeilenumbruch", DisplayName.isValid("Ja\nnik"))
    }

    @Test
    fun `a profile without a short name still shows something in the ranking`() {
        val profile = UserProfile(
            id = "1",
            email = "timo@example.com",
            name = "Timo Kosowski"
        )
        assertEquals("Timo K.", DisplayName.of(profile))

        val chosen = profile.copy(displayName = "Timo")
        assertEquals("Timo", DisplayName.of(chosen))
    }
}
