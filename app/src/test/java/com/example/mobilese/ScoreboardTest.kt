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
}
