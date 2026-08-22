package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Crew-Level: was es antreibt, und was es bewusst nicht tut.
 */
class CrewLevelTest {

    @Test
    fun `die Erfahrung ist der Durchschnitt der Mitglieder`() {
        assertEquals(100, CrewLevel.xpOf(crewPoints = 400, memberCount = 4, battlesWon = 0))
    }

    /**
     * Sonst waere das Crew-Level vor allem eine Anzeige der Crew-Groesse, und
     * eine Crew stiege auf, indem sie jemanden aufnimmt.
     */
    @Test
    fun `mehr Mitglieder allein bringen kein Level`() {
        val kleine = CrewLevel.xpOf(crewPoints = 300, memberCount = 3, battlesWon = 0)
        val grosse = CrewLevel.xpOf(crewPoints = 900, memberCount = 9, battlesWon = 0)

        assertEquals(kleine, grosse)
    }

    @Test
    fun `ein gewonnener Battle bringt den Bonus`() {
        val ohne = CrewLevel.xpOf(crewPoints = 400, memberCount = 4, battlesWon = 0)
        val mit = CrewLevel.xpOf(crewPoints = 400, memberCount = 4, battlesWon = 1)

        assertEquals(ohne + CrewBattle.WIN_BONUS, mit)
    }

    @Test
    fun `Battles heben das Level sichtbar`() {
        val ohne = CrewLevel.of(crewPoints = 400, memberCount = 4, battlesWon = 0)
        val mit = CrewLevel.of(crewPoints = 400, memberCount = 4, battlesWon = 2)

        assertTrue("Zwei Siege muessen mehrere Level bringen", mit.level > ohne.level + 1)
    }

    /** Die Zahlen kommen am Ende aus der Datenbank und koennen krumm sein. */
    @Test
    fun `keine Division durch null ohne Mitglieder`() {
        assertEquals(0, CrewLevel.xpOf(crewPoints = 0, memberCount = 0, battlesWon = 0))
        assertEquals(1, CrewLevel.of(crewPoints = -50, memberCount = 0, battlesWon = 0).level)
    }
}
