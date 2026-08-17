package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-Tests fuer die Aufteilung des Namens auf zwei Eingabefelder.
 *
 * Der wichtigste Fall ist der Rundlauf: was aus der Datenbank kommt, auf zwei
 * Felder verteilt und wieder zusammengesetzt wird, muss unveraendert sein -
 * sonst aendert schon das blosse Oeffnen und Speichern des Profils den Namen.
 */
class PersonNameTest {

    @Test
    fun `a name is split at the last part`() {
        assertEquals("Jannik", PersonName.firstOf("Jannik Rikazewski"))
        assertEquals("Rikazewski", PersonName.lastOf("Jannik Rikazewski"))
    }

    /** Mehrere Vornamen gehoeren nach links, nicht der zweite nach rechts. */
    @Test
    fun `everything before the last part counts as the first name`() {
        assertEquals("Ana Maria", PersonName.firstOf("Ana Maria Silva"))
        assertEquals("Silva", PersonName.lastOf("Ana Maria Silva"))
    }

    @Test
    fun `a single name goes into the first field`() {
        assertEquals("Cher", PersonName.firstOf("Cher"))
        assertEquals("", PersonName.lastOf("Cher"))
    }

    @Test
    fun `nothing in means nothing out`() {
        assertEquals("", PersonName.firstOf(null))
        assertEquals("", PersonName.lastOf(null))
        assertEquals("", PersonName.firstOf("   "))
        assertEquals("", PersonName.lastOf("   "))
    }

    @Test
    fun `an empty field leaves no stray space behind`() {
        assertEquals("Cher", PersonName.join("Cher", ""))
        assertEquals("Silva", PersonName.join("", "Silva"))
        assertEquals("", PersonName.join("", ""))
        assertEquals("Jannik Rikazewski", PersonName.join("  Jannik ", " Rikazewski "))
    }

    @Test
    fun `opening and saving the profile leaves the name untouched`() {
        listOf(
            "Jannik Rikazewski",
            "Ana Maria Silva",
            "Cher",
            "Timo Kosowski"
        ).forEach { stored ->
            assertEquals(
                stored,
                PersonName.join(PersonName.firstOf(stored), PersonName.lastOf(stored))
            )
        }
    }

    /** Mehrfache Leerzeichen werden dabei zusammengefasst - das ist gewollt. */
    @Test
    fun `extra spaces are cleaned up on the way through`() {
        val stored = "Jannik   Rikazewski"

        assertEquals(
            "Jannik Rikazewski",
            PersonName.join(PersonName.firstOf(stored), PersonName.lastOf(stored))
        )
    }
}
