package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-Tests fuer den Fortschritt einer Challenge und die Ausschuettung.
 *
 * `short runs are not rounded away` haelt einen Fehler fest, der vorher in der
 * Rangliste steckte: jede Aktivitaet wurde einzeln auf ganze Kilometer
 * gekuerzt, bevor summiert wurde.
 */
class ChallengeProgressTest {

    @Test
    fun `short runs are not rounded away before they are added up`() {
        val runs = listOf(
            TestData.activity("u1", distance = 0.9),
            TestData.activity("u1", distance = 0.9),
            TestData.activity("u1", distance = 0.9)
        )

        // 2.7 km. Einzeln gekuerzt waeren es 0 + 0 + 0 gewesen.
        assertEquals(2, ChallengeCalculator.progressOf(ChallengeType.DISTANCE, runs))
    }

    @Test
    fun `workout count challenges count gym sessions only`() {
        val activities = listOf(
            TestData.activity("u1", sport = Sports.GYM),
            TestData.activity("u1", sport = Sports.GYM),
            TestData.activity("u1", sport = Sports.RUNNING)
        )

        assertEquals(2, ChallengeCalculator.progressOf(ChallengeType.WORKOUT_COUNT, activities))
    }

    @Test
    fun `the legacy type name Running is still recognised as a distance challenge`() {
        // In bestehenden Datensaetzen steht noch der alte Typname.
        assertEquals(true, ChallengeCalculator.isDistanceChallenge("Running"))
        assertEquals(true, ChallengeCalculator.isDistanceChallenge(ChallengeType.DISTANCE.name))
        assertEquals(false, ChallengeCalculator.isDistanceChallenge(ChallengeType.WORKOUT_COUNT.name))
    }

    @Test
    fun `nothing is awarded while the goal has not been reached`() {
        val challenge = TestData.challenge(goal = 10, reward = 20)
        val snapshot = TestData.snapshot(members = listOf(TestData.profile("u1", "Ada")))

        assertNull(ChallengeManager.pendingAward(challenge, 9, listOf("u1"), snapshot))
    }

    @Test
    fun `a completed challenge is split among all members`() {
        val challenge = TestData.challenge(goal = 10, reward = 20)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Linus"))
        )

        val award = ChallengeManager.pendingAward(challenge, 10, listOf("u1", "u2"), snapshot)

        assertEquals(10, award?.pointsPerUser)
        assertEquals(listOf("u1", "u2"), award?.userIds)
    }

    @Test
    fun `members who were already rewarded are skipped`() {
        val challenge = TestData.challenge(id = "c1", goal = 10, reward = 20)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Linus")),
            rewards = listOf(ChallengeReward("c1", "u1", 10))
        )

        val award = ChallengeManager.pendingAward(challenge, 12, listOf("u1", "u2"), snapshot)

        assertEquals(listOf("u2"), award?.userIds)
        // Der Anteil bleibt am Belohnungstopf und der Crew-Groesse haengen,
        // nicht an der Zahl der noch offenen Ausschuettungen.
        assertEquals(10, award?.pointsPerUser)
    }

    @Test
    fun `a challenge is not paid out twice`() {
        val challenge = TestData.challenge(id = "c1", goal = 10, reward = 20)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            rewards = listOf(ChallengeReward("c1", "u1", 20))
        )

        assertNull(ChallengeManager.pendingAward(challenge, 50, listOf("u1"), snapshot))
    }

    @Test
    fun `member contributions are listed with the strongest first`() {
        val challenge = TestData.challenge(type = ChallengeType.DISTANCE, goal = 10)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Linus")),
            activities = listOf(
                TestData.activity("u1", distance = 3.0),
                TestData.activity("u2", distance = 8.0)
            )
        )

        val contributions = ChallengeManager.progressByMember(challenge, snapshot)

        assertEquals(listOf("Linus", "Ada"), contributions.map { it.first.name })
        assertEquals(listOf(8, 3), contributions.map { it.second })
    }
}
