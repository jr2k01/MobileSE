package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun `starts at level one without points`() {
        val progress = Levels.of(0)

        assertEquals(0, progress.prestige)
        assertEquals(1, progress.level)
        assertEquals(0, progress.pointsIntoLevel)
    }

    /** Ein Punkt zu wenig ist noch kein Aufstieg. */
    @Test
    fun `stays on level one just below the cost`() {
        val cost = Levels.costOf(1)
        val progress = Levels.of(cost - 1)

        assertEquals(1, progress.level)
        assertEquals(cost - 1, progress.pointsIntoLevel)
    }

    @Test
    fun `reaches level two exactly at the cost`() {
        val progress = Levels.of(Levels.costOf(1))

        assertEquals(2, progress.level)
        assertEquals(0, progress.pointsIntoLevel)
    }

    /** Der Kern der Anforderung: spaetere Level kosten mehr. */
    @Test
    fun `every level costs more than the one before`() {
        (2..Levels.MAX_LEVEL).forEach { level ->
            assertTrue(
                "Level $level must cost more than ${level - 1}",
                Levels.costOf(level) > Levels.costOf(level - 1)
            )
        }
    }

    @Test
    fun `does not go past level one hundred before prestige`() {
        // Alles bis kurz vor den vollen Durchlauf bleibt bei Prestige 0.
        val progress = Levels.of(Levels.prestigeCost - 1)

        assertEquals(0, progress.prestige)
        assertEquals(Levels.MAX_LEVEL, progress.level)
    }

    @Test
    fun `a full run starts over at prestige one level one`() {
        val progress = Levels.of(Levels.prestigeCost)

        assertEquals(1, progress.prestige)
        assertEquals(1, progress.level)
        assertEquals(0, progress.pointsIntoLevel)
    }

    @Test
    fun `counts several prestiges`() {
        val progress = Levels.of(Levels.prestigeCost * 3 + Levels.costOf(1))

        assertEquals(3, progress.prestige)
        assertEquals(2, progress.level)
    }

    /**
     * Punkte duerfen unterwegs nicht verschwinden: was in die Level gesteckt
     * wurde plus der Rest muss die Summe wieder ergeben.
     */
    @Test
    fun `spends every point exactly once`() {
        listOf(0, 1, 29, 30, 500, 12345, Levels.prestigeCost * 2 + 77).forEach { total ->
            val progress = Levels.of(total)
            val spentOnLevels = (1 until progress.level).sumOf { Levels.costOf(it) }
            val spentOnPrestige = progress.prestige * Levels.prestigeCost

            assertEquals(
                "total $total",
                total,
                spentOnPrestige + spentOnLevels + progress.pointsIntoLevel
            )
        }
    }

    /** Eine Punktzahl aus der Datenbank kann alles sein - auch Unsinn. */
    @Test
    fun `treats negative points as none`() {
        val progress = Levels.of(-500)

        assertEquals(0, progress.prestige)
        assertEquals(1, progress.level)
        assertEquals(0, progress.pointsIntoLevel)
    }

    @Test
    fun `clamps the cost of levels outside the range`() {
        assertEquals(Levels.costOf(1), Levels.costOf(0))
        assertEquals(Levels.costOf(1), Levels.costOf(-7))
        assertEquals(Levels.costOf(Levels.MAX_LEVEL), Levels.costOf(Levels.MAX_LEVEL + 5))
    }

    @Test
    fun `fills the bar in proportion`() {
        val cost = Levels.costOf(1)

        assertEquals(0, Levels.of(0).percent)
        assertEquals(50, Levels.of(cost / 2).percent)
        assertEquals(0, Levels.of(cost).percent)
    }

    /** Bei sehr vielen Punkten darf die Rechnung nicht ins Rutschen kommen. */
    @Test
    fun `survives an absurd number of points`() {
        val progress = Levels.of(Int.MAX_VALUE)

        assertTrue(progress.prestige > 0)
        assertTrue(progress.level in 1..Levels.MAX_LEVEL)
        assertTrue(progress.percent in 0..100)
    }
}
