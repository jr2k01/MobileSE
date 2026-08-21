package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Zeile "Together: ...", die die ganze Crew unter einem gemeinsam
 * absolvierten Workout sieht.
 */
class JointWorkoutTest {

    private val names = mapOf(
        "a" to "Timo",
        "b" to "Jannik",
        "c" to "Mia"
    )

    @Test
    fun `an activity without partners is not joint`() {
        assertFalse(JointWorkout.isJoint(TestData.activity("a")))
        assertFalse(JointWorkout.isJoint(TestData.activity("a", partnerIds = emptyList())))
    }

    @Test
    fun `the author is named first, then the others`() {
        val activity = TestData.activity("a", partnerIds = listOf("b", "c"))

        assertTrue(JointWorkout.isJoint(activity))
        assertEquals(
            listOf("Timo", "Jannik", "Mia"),
            JointWorkout.participants(activity, names, "Unknown")
        )
    }

    /**
     * Wer die Crew verlassen hat, steht nicht mehr im Nachschlagewerk. Er wird
     * trotzdem mitgezaehlt: die Zahl der Namen soll der Groesse der Gruppe
     * entsprechen, sonst sieht ein Training zu dritt hinterher aus wie eines zu
     * zweit.
     */
    @Test
    fun `an unknown participant keeps his place`() {
        val activity = TestData.activity("a", partnerIds = listOf("b", "gone"))

        assertEquals(
            listOf("Timo", "Jannik", "Unknown"),
            JointWorkout.participants(activity, names, "Unknown")
        )
    }

    /**
     * Sollte der Verfasser aus Versehen in seiner eigenen Partnerliste stehen -
     * etwa weil ein Geraet seine eigene Kennung mit ausgesendet hat - darf er
     * nicht doppelt in der Zeile auftauchen.
     */
    @Test
    fun `nobody is named twice`() {
        val activity = TestData.activity("a", partnerIds = listOf("a", "b"))

        assertEquals(
            listOf("Timo", "Jannik"),
            JointWorkout.participants(activity, names, "Unknown")
        )
    }

    @Test
    fun `a group of six is named in full`() {
        val many = (1..5).map { "p$it" }
        val activity = TestData.activity("a", partnerIds = many)

        assertEquals(6, JointWorkout.participants(activity, names, "Unknown").size)
    }
}
