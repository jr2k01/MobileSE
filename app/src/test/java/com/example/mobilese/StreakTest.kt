package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit-Tests fuer die persoenliche Serie.
 *
 * Serien bestehen fast nur aus Grenzfaellen: der Tag, an dem noch nichts war,
 * die Luecke von genau einem Tag, der Sprung auf die naechste Stufe. Jeder
 * davon aendert Punkte, und zwar rueckwirkend fuer alles, was an dem Tag
 * eingetragen wurde - deshalb steht hier mehr als anderswo.
 */
class StreakTest {

    private val today = LocalDate.of(2026, 8, 19)

    private fun days(vararg iso: String) = iso.toSet()

    // === Was einen Tag aktiv macht ===

    @Test
    fun `a workout makes the day count`() {
        val active = Streak.activeDays(
            "u1",
            listOf(TestData.activity("u1", timestamp = "2026-08-19T07:00:00")),
            emptyList()
        )

        assertEquals(setOf("2026-08-19"), active)
    }

    @Test
    fun `reaching the step goal makes the day count too`() {
        val active = Streak.activeDays(
            "u1",
            emptyList(),
            listOf(TestData.stepDay("u1", "2026-08-19", 10_000))
        )

        assertEquals(setOf("2026-08-19"), active)
    }

    /** Unter dem Ziel ist der Tag nicht aktiv - sonst zaehlte jeder Gang zum Kuehlschrank. */
    @Test
    fun `steps below the goal do not make the day count`() {
        val active = Streak.activeDays(
            "u1",
            emptyList(),
            listOf(TestData.stepDay("u1", "2026-08-19", 9_999))
        )

        assertTrue(active.isEmpty())
    }

    @Test
    fun `only the own days count`() {
        val active = Streak.activeDays(
            "u1",
            listOf(TestData.activity("u2", timestamp = "2026-08-19T07:00:00")),
            listOf(TestData.stepDay("u2", "2026-08-18", 12_000))
        )

        assertTrue(active.isEmpty())
    }

    @Test
    fun `an unreadable timestamp is no day of its own`() {
        val active = Streak.activeDays(
            "u1",
            listOf(TestData.activity("u1", timestamp = "irgendwas")),
            emptyList()
        )

        assertTrue(active.isEmpty())
    }

    // === Laenge ===

    @Test
    fun `consecutive days add up`() {
        val active = days("2026-08-17", "2026-08-18", "2026-08-19")
        assertEquals(3, Streak.current(active, today))
    }

    /** Eine Luecke von einem Tag beendet die Serie. */
    @Test
    fun `a gap ends the streak`() {
        val active = days("2026-08-15", "2026-08-16", "2026-08-18", "2026-08-19")
        assertEquals(2, Streak.current(active, today))
    }

    /**
     * Am Vormittag ist meist noch nichts eingetragen. Die Serie darf deshalb
     * nicht schon auf null stehen, solange gestern etwas war.
     */
    @Test
    fun `a streak survives a day that has not been used yet`() {
        val active = days("2026-08-17", "2026-08-18")
        assertEquals(2, Streak.current(active, today))
    }

    @Test
    fun `two silent days end it after all`() {
        val active = days("2026-08-16", "2026-08-17")
        assertEquals(0, Streak.current(active, today))
    }

    @Test
    fun `without any day there is no streak`() {
        assertEquals(0, Streak.current(emptySet(), today))
    }

    /**
     * Der Stand von damals: was am 17. galt, bleibt dabei - auch wenn die Serie
     * bis heute weitergelaufen ist.
     */
    @Test
    fun `the length on an earlier day is the length of that day`() {
        val active = days("2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19")

        assertEquals(1, Streak.endingOn(active, LocalDate.of(2026, 8, 15)))
        assertEquals(3, Streak.endingOn(active, LocalDate.of(2026, 8, 17)))
        assertEquals(5, Streak.endingOn(active, today))
    }

    @Test
    fun `a day that was not used has no length`() {
        assertEquals(0, Streak.endingOn(days("2026-08-19"), LocalDate.of(2026, 8, 18)))
    }

    // === Aufschlag ===

    @Test
    fun `below the first tier there is no bonus`() {
        (0 until Streak.FIRST_TIER_DAYS).forEach { day ->
            assertEquals("Tag $day", 1.0, Streak.multiplierFor(day), 0.0001)
        }
    }

    @Test
    fun `each tier applies from its own day on`() {
        Streak.TIERS.forEach { tier ->
            assertEquals(
                "Stufe ${tier.days}",
                tier.multiplier,
                Streak.multiplierFor(tier.days),
                0.0001
            )
            // Einen Tag davor gilt sie noch nicht.
            assertTrue(Streak.multiplierFor(tier.days - 1) < tier.multiplier)
        }
    }

    @Test
    fun `the multiplier never falls back on a longer streak`() {
        var previous = 1.0
        (0..60).forEach { day ->
            val current = Streak.multiplierFor(day)
            assertTrue("Tag $day fiel von $previous auf $current", current >= previous)
            previous = current
        }
    }

    @Test
    fun `the way to the next tier shrinks day by day`() {
        assertEquals(Streak.FIRST_TIER_DAYS, Streak.daysToNextTier(0))
        assertEquals(1, Streak.daysToNextTier(Streak.FIRST_TIER_DAYS - 1))
        // Auf der hoechsten Stufe gibt es keine naechste mehr.
        assertNull(Streak.daysToNextTier(Streak.TIERS.maxOf { it.days }))
    }

    // === Punkte ===

    @Test
    fun `points grow with the multiplier and are rounded`() {
        assertEquals(20, Streak.applyMultiplier(20, 1.0))
        // 20 * 1.1 = 22
        assertEquals(22, Streak.applyMultiplier(20, 1.1))
        // 15 * 1.1 = 16.5, kaufmaennisch 17 - abgeschnitten waere der
        // Aufschlag bei kleinen Punktzahlen regelmaessig verschwunden.
        assertEquals(17, Streak.applyMultiplier(15, 1.1))
    }

    // === Zusammenspiel mit der Rangliste ===

    /**
     * Fuenf Tage in Folge trainiert: die ersten vier zaehlen einfach, der
     * fuenfte mit Aufschlag. Ein Workout von 30 Minuten bei hoher Intensitaet
     * gibt 14 Punkte, der fuenfte also 15.
     */
    @Test
    fun `the ranking pays the bonus only from the day the tier is reached`() {
        val activities = (15..19).map { day ->
            TestData.activity("u1", duration = 30, timestamp = "2026-08-%02dT07:00:00".format(day))
        }
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = activities
        )

        // 4 * 14 + round(14 * 1.1) = 56 + 15 = 71
        assertEquals(71, Scoreboard.build(snapshot, today = today.toString()).single().points)
    }

    @Test
    fun `without a streak the ranking is unchanged`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = listOf(
                TestData.activity("u1", duration = 30, timestamp = "2026-08-19T07:00:00")
            )
        )

        assertEquals(14, Scoreboard.build(snapshot, today = today.toString()).single().points)
    }

    /** Der Balken der Rangliste muss sich weiterhin zum Punktestand summieren. */
    @Test
    fun `the points split still adds up to what the ranking shows`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = (14..19).map { day ->
                TestData.activity("u1", duration = 45, timestamp = "2026-08-%02dT07:00:00".format(day))
            },
            rewards = listOf(ChallengeReward("c1", "u1", 30)),
            stepDays = listOf(TestData.stepDay("u1", "2026-08-18", 11_000))
        )

        assertEquals(
            Scoreboard.build(snapshot, today = today.toString()).single().points,
            CrewStats.pointsSplit("u1", snapshot).total
        )
    }
}
