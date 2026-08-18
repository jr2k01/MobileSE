package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(40, week.last().amount)
        assertEquals(0, week[0].amount)
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
        assertEquals(50, week[5].amount)
    }

    @Test
    fun `workouts outside the week are left out`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", duration = 99, timestamp = "2026-07-01T09:00:00")
            )
        )

        assertEquals(0, CrewStats.lastWeek(snapshot, LocalDate.of(2026, 8, 17)).sumOf { it.amount })
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

    // === Schritte der Woche ===

    @Test
    fun `steps of one day are added up across members`() {
        val snapshot = TestData.snapshot(
            stepDays = listOf(
                TestData.stepDay("u1", "2026-08-16", 8_000),
                TestData.stepDay("u2", "2026-08-16", 3_500),
                TestData.stepDay("u1", "2026-08-17", 1_000)
            )
        )

        val week = CrewStats.lastWeekSteps(snapshot, LocalDate.of(2026, 8, 17))
        assertEquals(7, week.size)
        assertEquals(11_500, week[5].amount)
        assertEquals(1_000, week.last().amount)
    }

    /** Ohne Schrittdaten steht die Reihe leer da, statt zu fehlen. */
    @Test
    fun `a week without steps still has seven days`() {
        val week = CrewStats.lastWeekSteps(TestData.snapshot(), LocalDate.of(2026, 8, 17))

        assertEquals(7, week.size)
        assertEquals(0, week.sumOf { it.amount })
    }

    // === Mitglieder ===

    @Test
    fun `members are ranked by minutes trained`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = listOf(
                TestData.activity("u1", duration = 20, distance = 3.0),
                TestData.activity("u2", duration = 90, distance = 12.0),
                TestData.activity("u2", duration = 10)
            )
        )

        val shares = CrewStats.memberShares(snapshot)
        assertEquals("u2", shares.first().userId)
        assertEquals(100, shares.first().minutes)
        assertEquals(2, shares.first().workouts)
        assertEquals(12.0, shares.first().kilometres, 0.001)
    }

    /** Wer nichts getan hat, verschwindet nicht - das ist die Aussage. */
    @Test
    fun `a member without workouts stays in the list with zero`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = listOf(TestData.activity("u1", duration = 30))
        )

        val shares = CrewStats.memberShares(snapshot)
        assertEquals(2, shares.size)
        assertEquals("u2", shares.last().userId)
        assertEquals(0, shares.last().minutes)
    }

    /** Aktivitaeten Ausgetretener gehoeren zu keinem Mitglied mehr. */
    @Test
    fun `workouts of former members are left out`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", duration = 30),
                TestData.activity("gone", duration = 500)
            )
        )

        val shares = CrewStats.memberShares(snapshot)
        assertEquals(1, shares.size)
        assertEquals(30, shares.single().minutes)
    }

    // === Bestwerte ===

    @Test
    fun `the longest workout is the one with the most minutes`() {
        val snapshot = TestData.snapshot(
            members = listOf(ada, bo),
            activities = listOf(
                TestData.activity("u1", duration = 30),
                TestData.activity("u2", sport = Sports.YOGA, duration = 75),
                TestData.activity("u1", duration = 60)
            )
        )

        val longest = CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).longest
        assertEquals(75, longest?.duration)
        assertEquals("u2", longest?.userId)
    }

    @Test
    fun `the average is the mean length of a workout`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", duration = 30),
                TestData.activity("u1", duration = 50)
            )
        )

        assertEquals(40, CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).averageMinutes)
    }

    /** Ohne Aktivitaeten darf nichts rechnen und nichts abstuerzen. */
    @Test
    fun `highlights of an empty crew are empty, not broken`() {
        val highlights = CrewStats.highlights(TestData.snapshot(), LocalDate.of(2026, 8, 17))

        assertNull(highlights.longest)
        assertEquals(0, highlights.streakDays)
        assertEquals(0, highlights.averageMinutes)
    }

    // === Serie ===

    @Test
    fun `the streak counts consecutive days back from today`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", timestamp = "2026-08-17T09:00:00"),
                TestData.activity("u1", timestamp = "2026-08-16T09:00:00"),
                TestData.activity("u2", timestamp = "2026-08-15T09:00:00"),
                // Die Luecke am 14. beendet die Serie.
                TestData.activity("u1", timestamp = "2026-08-13T09:00:00")
            )
        )

        assertEquals(3, CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).streakDays)
    }

    /**
     * Am Vormittag hat oft noch niemand trainiert. Die Serie darf deshalb nicht
     * schon auf null stehen, solange gestern trainiert wurde.
     */
    @Test
    fun `a streak survives a day that has not been trained yet`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", timestamp = "2026-08-16T09:00:00"),
                TestData.activity("u1", timestamp = "2026-08-15T09:00:00")
            )
        )

        assertEquals(2, CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).streakDays)
    }

    /** Zwei Tage ohne Training beenden sie dann aber wirklich. */
    @Test
    fun `the streak is over after two silent days`() {
        val snapshot = TestData.snapshot(
            activities = listOf(TestData.activity("u1", timestamp = "2026-08-15T09:00:00"))
        )

        assertEquals(0, CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).streakDays)
    }

    /** Mehrere Trainings an einem Tag sind ein Tag, nicht mehrere. */
    @Test
    fun `several workouts on one day count as one day of the streak`() {
        val snapshot = TestData.snapshot(
            activities = listOf(
                TestData.activity("u1", timestamp = "2026-08-17T07:00:00"),
                TestData.activity("u1", timestamp = "2026-08-17T18:00:00"),
                TestData.activity("u2", timestamp = "2026-08-17T20:00:00")
            )
        )

        assertEquals(1, CrewStats.highlights(snapshot, LocalDate.of(2026, 8, 17)).streakDays)
    }
}
