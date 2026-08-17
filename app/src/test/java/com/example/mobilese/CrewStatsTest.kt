package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Unit-Tests fuer die Auswertung auf dem Ranglisten-Bildschirm.
 *
 * Am wichtigsten sind die Faelle, in denen ein Diagramm etwas Falsches
 * behaupten koennte: eine Woche mit Luecken, Prozente die nicht aufgehen, und
 * die Zuordnung der Punkte zu ihrer Herkunft.
 */
class CrewStatsTest {

    private val ada = TestData.profile("u1", "Ada")
    private val bo = TestData.profile("u2", "Bo")

    @Test
    fun `the totals add up across all members`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = listOf(
                TestData.activity("u1", duration = 30, distance = 5.0),
                TestData.activity("u2", duration = 45, distance = 7.5)
            ),
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-16", 12_000),
                TestData.stepDay("u2", "2026-08-16", 11_000),
                TestData.stepDay("u1", "2026-08-17", 3_000)
            )
        )

        val totals = CrewStats.totals(snapshot)
        assertEquals(2, totals.workouts)
        assertEquals(75, totals.minutes)
        assertEquals(12.5, totals.kilometres, 0.001)
        // Zwei Mitglieder am selben Tag sind ein Tag, nicht zwei.
        assertEquals(1, totals.goalDays)
    }

    // === Woche ===

    @Test
    fun `the week always has seven days, ending today`() {
        val week = CrewStats.lastWeek(TestData.snapshot(), LocalDate.of(2026, 8, 17))

        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 8, 11), week.first().day)
        assertEquals(LocalDate.of(2026, 8, 17), week.last().day)
    }

    /** Ein Tag ohne Training faellt nicht weg, sondern steht mit null da. */
    @Test
    fun `days without training stay in the week with zero minutes`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", duration = 40, timestamp = "2026-08-17T09:00:00")
            )
        )

        val week = CrewStats.lastWeek(snapshot, LocalDate.of(2026, 8, 17))
        assertEquals(7, week.size)
        assertEquals(40, week.last().minutes)
        assertEquals(0, week[0].minutes)
    }

    @Test
    fun `minutes of one day are added up across members`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", duration = 30, timestamp = "2026-08-16T07:00:00"),
                TestData.activity("u2", duration = 20, timestamp = "2026-08-16T19:00:00")
            )
        )

        val week = CrewStats.lastWeek(snapshot, LocalDate.of(2026, 8, 17))
        assertEquals(50, week[5].minutes)
    }

    @Test
    fun `workouts outside the week are left out`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", duration = 99, timestamp = "2026-07-01T09:00:00")
            )
        )

        assertEquals(0, CrewStats.lastWeek(snapshot, LocalDate.of(2026, 8, 17)).sumOf { it.minutes })
    }

    // === Sportarten ===

    @Test
    fun `sports are ranked by minutes, not by number of entries`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", sport = Sports.YOGA, duration = 10),
                TestData.activity("u1", sport = Sports.YOGA, duration = 10),
                TestData.activity("u1", sport = Sports.RUNNING, duration = 90)
            )
        )

        val shares = CrewStats.sportShares(snapshot)
        assertEquals(Sports.RUNNING, shares.first().sport)
        assertEquals(90, shares.first().minutes)
        assertEquals(20, shares[1].minutes)
    }

    /** Die Legende darf nicht sichtbar bei 99 Prozent enden. */
    @Test
    fun `the percentages always add up to a hundred`() {
        listOf(
            listOf(CrewStats.SportShare("A", 854), CrewStats.SportShare("B", 146)),
            listOf(
                CrewStats.SportShare("A", 1),
                CrewStats.SportShare("B", 1),
                CrewStats.SportShare("C", 1)
            ),
            listOf(CrewStats.SportShare("A", 7)),
            listOf(
                CrewStats.SportShare("A", 100),
                CrewStats.SportShare("B", 33),
                CrewStats.SportShare("C", 33),
                CrewStats.SportShare("D", 34)
            )
        ).forEach { shares ->
            assertEquals(
                "Summe fuer $shares",
                100,
                CrewStats.sharePercentages(shares).sum()
            )
        }
    }

    @Test
    fun `without workouts there is nothing to distribute`() {
        assertEquals(emptyList<CrewStats.SportShare>(), CrewStats.sportShares(TestData.snapshot()))
    }

    // === Herkunft der Punkte ===

    @Test
    fun `points are split into workouts, challenges and steps`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", duration = 30, intensity = WorkoutIntensity.MEDIUM)
            ),
            rewards = listOf(ChallengeReward("c1", "u1", 20)),
            stepDays = listOf(TestData.stepDay("u1", "2026-08-17", 12_000))
        )

        val split = CrewStats.pointsSplit("u1", snapshot)
        // (30 / 10) * 2 + 5 = 11
        assertEquals(11, split.workouts)
        assertEquals(20, split.challenges)
        assertEquals(StepGoal.BONUS_POINTS, split.steps)
        assertEquals(11 + 20 + StepGoal.BONUS_POINTS, split.total)
    }

    /** Die Summe der Aufteilung muss der Punktzahl der Rangliste entsprechen. */
    @Test
    fun `the split adds up to what the ranking shows`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", duration = 45, intensity = WorkoutIntensity.HIGH),
                TestData.activity("u1", duration = 20, intensity = WorkoutIntensity.LOW)
            ),
            rewards = listOf(ChallengeReward("c1", "u1", 30)),
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-16", 10_500),
                TestData.stepDay("u1", "2026-08-17", 2_000)
            )
        )

        assertEquals(
            Scoreboard.build(snapshot, today = "2026-08-17").single().points,
            CrewStats.pointsSplit("u1", snapshot).total
        )
    }

    @Test
    fun `the split of another member does not leak in`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = listOf(TestData.activity("u2", duration = 60)),
            rewards = listOf(ChallengeReward("c1", "u2", 50))
        )

        val split = CrewStats.pointsSplit("u1", snapshot)
        assertEquals(0, split.total)
    }
}
