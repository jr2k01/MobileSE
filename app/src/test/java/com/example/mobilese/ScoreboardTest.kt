package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-Tests fuer die Rangliste.
 *
 * Diese Berechnung lag vorher verteilt in zwei Activities und stellte pro
 * Mitglied mehrere Datenbankabfragen. Als reine Funktion ueber einem
 * [CrewSnapshot] laesst sie sich ohne Emulator und ohne Netz pruefen.
 */
class ScoreboardTest {

    @Test
    fun `workout points and challenge rewards are added up`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = listOf(
                // (30 / 10) * 3 + 5 = 14
                TestData.activity("u1", duration = 30, intensity = WorkoutIntensity.HIGH),
                // (20 / 10) * 1 + 5 = 7
                TestData.activity("u1", duration = 20, intensity = WorkoutIntensity.LOW)
            ),
            rewards = listOf(ChallengeReward("c1", "u1", 9))
        )

        assertEquals(30, Scoreboard.build(snapshot).single().points)
    }

    @Test
    fun `members are ordered by points, highest first`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Linus")),
            activities = listOf(
                TestData.activity("u1", duration = 10, intensity = WorkoutIntensity.LOW),
                TestData.activity("u2", duration = 60, intensity = WorkoutIntensity.HIGH)
            )
        )

        assertEquals(listOf("Linus", "Ada"), Scoreboard.build(snapshot).map { it.name })
    }

    @Test
    fun `equal scores keep a stable order instead of jumping around`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u2", "Linus"), TestData.profile("u1", "Ada")),
            activities = listOf(
                TestData.activity("u1", duration = 30),
                TestData.activity("u2", duration = 30)
            )
        )

        assertEquals(listOf("Ada", "Linus"), Scoreboard.build(snapshot).map { it.name })
    }

    @Test
    fun `activities of former members do not count`() {
        // Die Zeile bleibt beim Verlassen der Crew in der Datenbank stehen,
        // gehoert aber zu niemandem mehr in dieser Crew.
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = listOf(
                TestData.activity("u1", duration = 30),
                TestData.activity("gone", duration = 120)
            )
        )

        val board = Scoreboard.build(snapshot)
        assertEquals(1, board.size)
        assertEquals(14, board.single().points)
    }

    @Test
    fun `an unknown intensity falls back to medium instead of throwing`() {
        val broken = TestData.activity("u1", duration = 30).copy(intensity = "EXTREME")
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            activities = listOf(broken)
        )

        // (30 / 10) * 2 + 5 = 11
        assertEquals(11, Scoreboard.build(snapshot).single().points)
    }

    @Test
    fun `a member without activities appears with zero points`() {
        val snapshot = TestData.snapshot(members = listOf(TestData.profile("u1", "Ada")))

        assertEquals(0, Scoreboard.build(snapshot).single().points)
    }

    // === Schritte ===

    @Test
    fun `each day with the step goal reached adds bonus points`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-15", 12_000),
                TestData.stepDay("u1", "2026-08-16", 4_000),
                TestData.stepDay("u1", "2026-08-17", 10_000)
            )
        )

        // Zwei erreichte Tage, keine Aktivitaeten.
        assertEquals(
            2 * StepGoal.BONUS_POINTS,
            Scoreboard.build(snapshot, today = "2026-08-17").single().points
        )
    }

    /** Der Ring zeigt den heutigen Tag, nicht die Summe aller Tage. */
    @Test
    fun `the ring shows only today's steps`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-16", 9_000),
                TestData.stepDay("u1", "2026-08-17", 3_500)
            )
        )

        assertEquals(3_500, Scoreboard.build(snapshot, today = "2026-08-17").single().todaySteps)
    }

    @Test
    fun `a member without a row for today shows an empty ring`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Bo")),
            stepDays = listOf(TestData.stepDay("u1", "2026-08-17", 8_000))
        )

        val board = Scoreboard.build(snapshot, today = "2026-08-17").associateBy { it.userId }
        assertEquals(8_000, board.getValue("u1").todaySteps)
        assertEquals(0, board.getValue("u2").todaySteps)
    }

    /** Schritte anderer Mitglieder duerfen nicht auf das eigene Konto gehen. */
    @Test
    fun `step days are kept apart by member`() {
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Bo")),
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-17", 11_000),
                TestData.stepDay("u2", "2026-08-17", 500)
            )
        )

        val board = Scoreboard.build(snapshot, today = "2026-08-17").associateBy { it.userId }
        assertEquals(StepGoal.BONUS_POINTS, board.getValue("u1").points)
        assertEquals(0, board.getValue("u2").points)
    }
}
