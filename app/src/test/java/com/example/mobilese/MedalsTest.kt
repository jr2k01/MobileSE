package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer die Medaillen.
 *
 * Zwei Dinge stehen hier im Vordergrund: dass eine Medaille genau an ihrer
 * Grenze kippt, und dass die Serie wirklich aufeinanderfolgende Tage verlangt -
 * das ist die einzige Bedingung mit eigener Rechnung dahinter.
 */
class MedalsTest {

    private val ada = TestData.profile("u1", "Ada")
    private val bo = TestData.profile("u2", "Bo")

    @Test
    fun `the first workout earns the first medal`() {
        val without = TestData.snapshot(members = listOf(ada))
        assertFalse(Medal.FIRST_WORKOUT in Medals.earnedBy("u1", without))

        val with = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(TestData.activity("u1"))
        )
        assertTrue(Medal.FIRST_WORKOUT in Medals.earnedBy("u1", with))
    }

    @Test
    fun `the workout medals tip over exactly at their count`() {
        fun medalsFor(count: Int) = Medals.earnedBy(
            "u1",
            TestData.snapshot(
                members = listOf(ada),
                activities = List(count) { TestData.activity("u1") }
            )
        )

        assertFalse(Medal.TEN_WORKOUTS in medalsFor(9))
        assertTrue(Medal.TEN_WORKOUTS in medalsFor(10))
        assertFalse(Medal.FIFTY_WORKOUTS in medalsFor(49))
        assertTrue(Medal.FIFTY_WORKOUTS in medalsFor(50))
    }

    @Test
    fun `the marathon counts the total distance across workouts`() {
        val short = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 20.0),
                TestData.activity("u1", distance = 22.0)
            )
        )
        assertFalse(Medal.MARATHON in Medals.earnedBy("u1", short))

        val far = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 20.0),
                TestData.activity("u1", distance = 22.195)
            )
        )
        assertTrue(Medal.MARATHON in Medals.earnedBy("u1", far))
    }

    // === Serie ===

    @Test
    fun `ten days in a row earn the streak, nine do not`() {
        fun streakOf(days: Int) = TestData.snapshot(
            members = listOf(ada),
            stepDays = List(days) { TestData.stepDay("u1", "2026-08-%02d".format(it + 1), 12_000) }
        )

        assertFalse(Medal.STEP_STREAK in Medals.earnedBy("u1", streakOf(9)))
        assertTrue(Medal.STEP_STREAK in Medals.earnedBy("u1", streakOf(10)))
    }

    /** Zehn erreichte Tage sind noch keine Serie, wenn einer dazwischen fehlt. */
    @Test
    fun `a gap breaks the streak even with enough days overall`() {
        val days = (1..11).filter { it != 6 }.map {
            TestData.stepDay("u1", "2026-08-%02d".format(it), 12_000)
        }
        assertEquals(10, days.size)

        assertFalse(
            Medal.STEP_STREAK in Medals.earnedBy(
                "u1",
                TestData.snapshot(members = listOf(ada), stepDays = days)
            )
        )
    }

    /** Ein Tag unter dem Ziel zaehlt nicht mit, auch wenn eine Zeile da ist. */
    @Test
    fun `a day below the goal breaks the streak`() {
        val days = (1..10).map {
            TestData.stepDay("u1", "2026-08-%02d".format(it), if (it == 5) 3_000 else 12_000)
        }

        assertFalse(
            Medal.STEP_STREAK in Medals.earnedBy(
                "u1",
                TestData.snapshot(members = listOf(ada), stepDays = days)
            )
        )
    }

    @Test
    fun `the streak spans the turn of the month`() {
        val days = listOf(
            "2026-07-28", "2026-07-29", "2026-07-30", "2026-07-31",
            "2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04",
            "2026-08-05", "2026-08-06"
        )
        assertEquals(10, Medals.longestStreak(days))
    }

    @Test
    fun `a day listed twice does not lengthen the streak`() {
        val days = listOf("2026-08-01", "2026-08-01", "2026-08-02")
        assertEquals(2, Medals.longestStreak(days))
    }

    @Test
    fun `an unreadable date is skipped instead of crashing`() {
        val days = listOf("2026-08-01", "not a date", "2026-08-02")
        assertEquals(2, Medals.longestStreak(days))
    }

    // === Abgrenzung ===

    /** Die Leistungen anderer duerfen nicht auf das eigene Konto gehen. */
    @Test
    fun `medals are kept apart by member`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = List(10) { TestData.activity("u1") },
            stepDays = listOf(TestData.stepDay("u1", "2026-08-17", 12_000))
        )

        assertTrue(Medal.TEN_WORKOUTS in Medals.earnedBy("u1", snapshot))
        assertTrue(Medals.earnedBy("u2", snapshot).isEmpty())
    }

    @Test
    fun `the status lists every medal, earned or not`() {
        val status = Medals.statusFor("u1", TestData.snapshot(members = listOf(ada)))

        assertEquals(Medal.entries.size, status.size)
        assertTrue(status.values.none { it })
    }
}
