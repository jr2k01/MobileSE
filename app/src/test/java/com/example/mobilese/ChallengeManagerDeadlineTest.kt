package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Zusammenspiel von Frist und Belohnung.
 *
 * Hier haengt Geld dran, im uebertragenen Sinne: wird nach Ablauf noch
 * mitgezaehlt, gibt es Punkte fuer etwas, das nicht rechtzeitig geschafft
 * wurde. Wird zu frueh abgeschnitten, fehlen sie jemandem, der es geschafft
 * hat.
 */
class ChallengeManagerDeadlineTest {

    private val ada = TestData.profile("u1", "Ada")

    private fun challengeWithDeadline(deadline: String?) = Challenge(
        id = "c1",
        crewId = "CRW100",
        type = ChallengeType.DISTANCE.name,
        goal = 50,
        reward = 100,
        deadline = deadline
    )

    @Test
    fun `kilometres after the deadline do not count towards the goal`() {
        val challenge = challengeWithDeadline("2026-08-31")
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 30.0, timestamp = "2026-08-30T09:00:00"),
                TestData.activity("u1", distance = 40.0, timestamp = "2026-09-02T09:00:00")
            ),
            challenges = listOf(challenge)
        )

        val contributions = ChallengeManager.progressByMember(challenge, snapshot)
        assertEquals(30, contributions.sumOf { it.second })
        assertNull(ChallengeManager.pendingAward(challenge, contributions, snapshot))
    }

    /** Rechtzeitig geschafft heisst Punkte - auch wenn erst spaeter nachgesehen wird. */
    @Test
    fun `a goal reached in time is still awarded after the deadline`() {
        val challenge = challengeWithDeadline("2026-08-31")
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 55.0, timestamp = "2026-08-20T09:00:00")
            ),
            challenges = listOf(challenge)
        )

        val contributions = ChallengeManager.progressByMember(challenge, snapshot)
        assertEquals(55, contributions.sumOf { it.second })

        val award = ChallengeManager.pendingAward(challenge, contributions, snapshot)
        assertEquals(listOf("u1"), award?.shares?.map { it.userId })
    }

    /** Ohne Frist bleibt es beim bisherigen Verhalten. */
    @Test
    fun `without a deadline late kilometres still count`() {
        val challenge = challengeWithDeadline(null)
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 30.0, timestamp = "2026-08-30T09:00:00"),
                TestData.activity("u1", distance = 40.0, timestamp = "2027-01-02T09:00:00")
            ),
            challenges = listOf(challenge)
        )

        val contributions = ChallengeManager.progressByMember(challenge, snapshot)
        assertEquals(70, contributions.sumOf { it.second })
        assertEquals(
            listOf("u1"),
            ChallengeManager.pendingAward(challenge, contributions, snapshot)?.shares?.map { it.userId }
        )
    }

    @Test
    fun `a workout on the deadline itself still gets the crew there`() {
        val challenge = challengeWithDeadline("2026-08-31")
        val snapshot = TestData.snapshot(
            members = listOf(ada),
            activities = listOf(
                TestData.activity("u1", distance = 50.0, timestamp = "2026-08-31T22:00:00")
            ),
            challenges = listOf(challenge)
        )

        val contributions = ChallengeManager.progressByMember(challenge, snapshot)
        assertEquals(50, contributions.sumOf { it.second })
        assertEquals(
            listOf("u1"),
            ChallengeManager.pendingAward(challenge, contributions, snapshot)?.shares?.map { it.userId }
        )
    }
}
