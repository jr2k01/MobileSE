package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer die Eingabepruefungen.
 *
 * Alle Regeln sind reine Funktionen ueber Zeichenketten und lassen sich
 * deshalb ohne Emulator pruefen - genau die Faelle, die sich sonst nur durch
 * muehsames Tippen auf dem Geraet nachstellen liessen.
 */
class InputRulesTest {

    // --- Passwort ---

    @Test
    fun `a password must satisfy every single rule`() {
        assertTrue(PasswordPolicy.isValid("Crew!Fit1"))

        assertFalse("too short", PasswordPolicy.isValid("Ab1!def"))
        assertFalse("no uppercase", PasswordPolicy.isValid("crewfit1!"))
        assertFalse("no lowercase", PasswordPolicy.isValid("CREWFIT1!"))
        assertFalse("no digit", PasswordPolicy.isValid("CrewFit!!"))
        assertFalse("no special character", PasswordPolicy.isValid("CrewFit12"))
    }

    @Test
    fun `the unmet rules are reported individually so the UI can list them`() {
        // Nur Kleinbuchstaben, zu kurz: alles ausser LOWERCASE fehlt.
        assertEquals(
            setOf(
                PasswordPolicy.Rule.LENGTH,
                PasswordPolicy.Rule.UPPERCASE,
                PasswordPolicy.Rule.DIGIT,
                PasswordPolicy.Rule.SPECIAL
            ),
            PasswordPolicy.unmetRules("abc")
        )
        assertTrue(PasswordPolicy.unmetRules("Crew!Fit1").isEmpty())
    }

    @Test
    fun `an empty password fails every rule but does not throw`() {
        assertEquals(PasswordPolicy.Rule.entries.size, PasswordPolicy.unmetRules("").size)
    }

    @Test
    fun `a space alone does not count as a special character`() {
        assertFalse(PasswordPolicy.isMet(PasswordPolicy.Rule.SPECIAL, "Crew Fit 1"))
        assertTrue(PasswordPolicy.isMet(PasswordPolicy.Rule.SPECIAL, "Crew-Fit1"))
    }

    @Test
    fun `umlauts count as letters`() {
        assertTrue(PasswordPolicy.isMet(PasswordPolicy.Rule.UPPERCASE, "Ärger1!x"))
        assertTrue(PasswordPolicy.isMet(PasswordPolicy.Rule.LOWERCASE, "ÄRGERü1!"))
    }

    // --- Namen ---

    @Test
    fun `names allow hyphens and umlauts but not empty or digits only`() {
        assertTrue(InputRules.isValidName("Jannik"))
        assertTrue(InputRules.isValidName("Anna-Lena"))
        assertTrue(InputRules.isValidName("Jörg"))

        assertFalse(InputRules.isValidName(""))
        assertFalse(InputRules.isValidName(" "))
        assertFalse("single character", InputRules.isValidName("J"))
        assertFalse("no letter at all", InputRules.isValidName("123"))
    }

    // --- Koerpermasse ---

    @Test
    fun `height and weight only accept plausible values`() {
        assertEquals(182, InputRules.heightOrNull("182"))
        assertNull("a person is not 3 cm tall", InputRules.heightOrNull("3"))
        assertNull("nor 400 cm", InputRules.heightOrNull("400"))
        assertNull(InputRules.heightOrNull("gross"))

        assertEquals(78.5, InputRules.weightOrNull("78,5"))
        assertEquals(78.5, InputRules.weightOrNull("78.5"))
        assertNull(InputRules.weightOrNull("5"))
        assertNull(InputRules.weightOrNull("500"))
    }

    // --- Workout ---

    @Test
    fun `a workout lasts at most one day`() {
        assertEquals(35, InputRules.durationOrNull("35"))
        assertEquals(1440, InputRules.durationOrNull("1440"))

        // Genau der Wert, der in den Testdaten stand und die Rangliste verzerrte.
        assertNull("5000 minutes is three and a half days", InputRules.durationOrNull("5000"))
        assertNull(InputRules.durationOrNull("0"))
        assertNull(InputRules.durationOrNull("-10"))
        assertNull(InputRules.durationOrNull(""))
    }

    @Test
    fun `distances stay within what a person can cover`() {
        assertEquals(7.5, InputRules.distanceOrNull("7,5"))
        assertNull(InputRules.distanceOrNull("0"))
        assertNull(InputRules.distanceOrNull("1000"))
    }

    // --- Crew und Challenge ---

    @Test
    fun `crew names need a sensible length`() {
        assertTrue(InputRules.isValidCrewName("MobileSE"))
        assertFalse("too short", InputRules.isValidCrewName("AB"))
        assertFalse("too long", InputRules.isValidCrewName("A".repeat(21)))
        assertFalse(InputRules.isValidCrewName("   "))
    }

    @Test
    fun `challenge goals are positive and bounded`() {
        assertEquals(20, InputRules.challengeGoalOrNull("20"))
        assertEquals(20, InputRules.challengeGoalOrNull("20,4"))
        assertNull(InputRules.challengeGoalOrNull("0"))
        assertNull(InputRules.challengeGoalOrNull("-5"))
        assertNull(InputRules.challengeGoalOrNull("999999"))
        assertNull(InputRules.challengeGoalOrNull("viel"))
    }
}
