package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests fuer die Arten von Team-Challenges.
 *
 * Zwei Dinge sind hier heikel. Erstens zaehlt jede Art etwas anderes, und ein
 * Fehler dabei faellt nicht auf - die Challenge steht dann einfach zu frueh
 * oder nie auf erledigt. Zweitens haengt an jedem Ziel ein Belohnungstopf, und
 * bei den kleinen Faktoren der Schritt-Challenge landet man schnell bei null
 * Punkten, womit sie nie ausgeschuettet wuerde.
 */
class ChallengeTypeTest {

    // === Was gezaehlt wird ===

    @Test
    fun `points count the workout points of any sport`() {
        val activities = listOf(
            // (30 / 10) * 3 + 5 = 14
            TestData.activity("u1", sport = Sports.RUNNING, duration = 30),
            // (60 / 10) * 3 + 5 = 23
            TestData.activity("u1", sport = "Padel", duration = 60)
        )

        assertEquals(37, ChallengeCalculator.progressOf(ChallengeType.POINTS, activities))
    }

    /** Kurze Einheiten geben keine Punkte - auch nicht auf eine Challenge. */
    @Test
    fun `a workout under ten minutes contributes no points`() {
        val activities = listOf(TestData.activity("u1", duration = 9))
        assertEquals(0, ChallengeCalculator.progressOf(ChallengeType.POINTS, activities))
    }

    @Test
    fun `minutes add up across every sport`() {
        val activities = listOf(
            TestData.activity("u1", sport = Sports.YOGA, duration = 20),
            TestData.activity("u1", sport = "Padel", duration = 45)
        )

        assertEquals(65, ChallengeCalculator.progressOf(ChallengeType.MINUTES, activities))
    }

    @Test
    fun `steps come from the step days, not from workouts`() {
        val steps = listOf(
            TestData.stepDay("u1", "2026-08-17", 8_000),
            TestData.stepDay("u1", "2026-08-18", 6_500)
        )

        assertEquals(
            14_500,
            ChallengeCalculator.progressOf(
                ChallengeType.STEPS,
                listOf(TestData.activity("u1", duration = 90)),
                steps
            )
        )
    }

    /** Zweimal am selben Tag ist ein Trainingstag. */
    @Test
    fun `training days count a day once, however often it was trained`() {
        val activities = listOf(
            TestData.activity("u1", timestamp = "2026-08-17T07:00:00"),
            TestData.activity("u1", timestamp = "2026-08-17T19:00:00"),
            TestData.activity("u1", timestamp = "2026-08-18T08:00:00")
        )

        assertEquals(2, ChallengeCalculator.progressOf(ChallengeType.ACTIVE_DAYS, activities))
    }

    /** Ein unlesbarer Zeitstempel gehoert zu keinem Tag. */
    @Test
    fun `an unreadable timestamp is not a training day of its own`() {
        val activities = listOf(
            TestData.activity("u1", timestamp = "2026-08-17T07:00:00"),
            TestData.activity("u1", timestamp = "irgendwas")
        )

        assertEquals(1, ChallengeCalculator.progressOf(ChallengeType.ACTIVE_DAYS, activities))
    }

    /** Ohne Daten steht jede Art bei null, statt zu stolpern. */
    @Test
    fun `every type starts at zero`() {
        ChallengeType.entries.forEach { type ->
            assertEquals(
                "Art $type",
                0,
                ChallengeCalculator.progressOf(type, emptyList(), emptyList())
            )
        }
    }

    // === Belohnung ===

    /**
     * Der Topf muss bei einem ueblichen Ziel in einer Groessenordnung landen,
     * die sich lohnt, ohne die Rangliste umzuwerfen.
     */
    @Test
    fun `a typical goal is worth a comparable reward in every type`() {
        val typical = mapOf(
            ChallengeType.DISTANCE to 50.0,
            ChallengeType.WORKOUT_COUNT to 20.0,
            ChallengeType.POINTS to 400.0,
            ChallengeType.MINUTES to 1_000.0,
            ChallengeType.STEPS to 200_000.0,
            ChallengeType.ACTIVE_DAYS to 20.0
        )

        typical.forEach { (type, goal) ->
            val reward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)
            assertTrue("Art $type ergab $reward", reward in 50..150)
        }
    }

    /** Sonst gaebe es eine Challenge, die sich nie ausschuetten laesst. */
    @Test
    fun `a small goal still yields at least one point`() {
        ChallengeType.entries.forEach { type ->
            assertTrue(
                "Art $type",
                ChallengeCalculator.calculateTotalChallengePoints(type, 1.0) >= 1
            )
        }
    }

    @Test
    fun `a goal of zero stays without a reward`() {
        ChallengeType.entries.forEach { type ->
            assertEquals(0, ChallengeCalculator.calculateTotalChallengePoints(type, 0.0))
        }
    }

    // === Gespeicherter Name ===

    @Test
    fun `a stored type is read back`() {
        ChallengeType.entries.forEach { type ->
            assertEquals(type, ChallengeType.fromStored(type.name))
        }
    }

    /** In bestehenden Zeilen steht noch der alte Name. */
    @Test
    fun `the legacy name Running is read as a distance challenge`() {
        assertEquals(ChallengeType.DISTANCE, ChallengeType.fromStored("Running"))
    }

    @Test
    fun `an unknown name falls back instead of failing`() {
        assertEquals(ChallengeType.DISTANCE, ChallengeType.fromStored(null))
        assertEquals(ChallengeType.DISTANCE, ChallengeType.fromStored("PADDLE_BOARDING"))
    }

    /** Die Namen stehen in der Datenbank und duerfen sich nicht verschieben. */
    @Test
    fun `the stored names are stable`() {
        assertEquals("DISTANCE", ChallengeType.DISTANCE.name)
        assertEquals("WORKOUT_COUNT", ChallengeType.WORKOUT_COUNT.name)
        assertEquals("POINTS", ChallengeType.POINTS.name)
        assertEquals("MINUTES", ChallengeType.MINUTES.name)
        assertEquals("STEPS", ChallengeType.STEPS.name)
        assertEquals("ACTIVE_DAYS", ChallengeType.ACTIVE_DAYS.name)
    }
}
