package com.example.mobilese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Crew-Battle: wer sieht welchen Gegner, wie steht es, und wer bekommt am
 * Ende die Punkte.
 */
class CrewBattleTest {

    private val us = "CRW100"
    private val them = "CRW200"

    @Test
    fun `eine gewoehnliche Challenge ist kein Battle`() {
        val challenge = TestData.challenge()

        assertFalse(challenge.isBattle)
        assertNull(CrewBattle.opponentOf(challenge, us))
    }

    @Test
    fun `beide Seiten sehen einander als Gegner`() {
        val battle = TestData.battle(crewId = us, opponentCrewId = them)

        assertEquals(them, CrewBattle.opponentOf(battle, us))
        assertEquals(us, CrewBattle.opponentOf(battle, them))
    }

    /** Eine unbeteiligte Crew hat in einem fremden Battle keinen Gegner. */
    @Test
    fun `wer nicht beteiligt ist sieht keinen Gegner`() {
        val battle = TestData.battle(crewId = us, opponentCrewId = them)

        assertNull(CrewBattle.opponentOf(battle, "CRW999"))
    }

    @Test
    fun `nur die herausgeforderte Crew darf annehmen`() {
        val battle = TestData.battle(crewId = us, opponentCrewId = them)

        assertFalse(CrewBattle.wasChallenged(battle, us))
        assertTrue(CrewBattle.wasChallenged(battle, them))
    }

    @Test
    fun `ein Battle laeuft erst nach dem Annehmen`() {
        assertFalse(CrewBattle.isRunning(TestData.battle(status = CrewBattle.STATUS_PENDING)))
        assertFalse(CrewBattle.isRunning(TestData.battle(status = CrewBattle.STATUS_DECLINED)))
        assertTrue(CrewBattle.isRunning(TestData.battle(status = CrewBattle.STATUS_ACCEPTED)))
    }

    /**
     * Zeilen aus einer Datenbank ohne die Statusspalte duerfen nicht
     * stillschweigend als abgelehnt gelten.
     */
    @Test
    fun `ohne Status gilt ein Battle als laufend`() {
        val battle = TestData.battle().copy(battleStatus = null)

        assertTrue(CrewBattle.isRunning(battle))
    }

    @Test
    fun `der Stand richtet sich nach dem Vorsprung`() {
        assertEquals(CrewBattle.Standing.LEADING, CrewBattle.standingOf(mine = 7, theirs = 5, goal = 10))
        assertEquals(CrewBattle.Standing.BEHIND, CrewBattle.standingOf(mine = 3, theirs = 5, goal = 10))
        assertEquals(CrewBattle.Standing.TIED, CrewBattle.standingOf(mine = 5, theirs = 5, goal = 10))
    }

    @Test
    fun `wer das Ziel erreicht hat gewinnt`() {
        assertEquals(CrewBattle.Standing.WON, CrewBattle.standingOf(mine = 10, theirs = 9, goal = 10))
        assertEquals(CrewBattle.Standing.LOST, CrewBattle.standingOf(mine = 9, theirs = 10, goal = 10))
    }

    /** Am Ziel zaehlt die eigene Crew zuerst - die Anzeige soll nicht luegen. */
    @Test
    fun `beide am Ziel gilt als gewonnen`() {
        assertEquals(CrewBattle.Standing.WON, CrewBattle.standingOf(mine = 12, theirs = 11, goal = 10))
    }

    @Test
    fun `gewonnen ist ein Battle wenn die eigenen Mitglieder ausgezahlt wurden`() {
        val battle = TestData.battle(id = "b1")
        val rewards = listOf(ChallengeReward("b1", "u1", 20))

        assertEquals(1, CrewBattle.wonBattles(listOf(battle), rewards, listOf("u1", "u2")))
    }

    @Test
    fun `die Auszahlung an die andere Crew zaehlt nicht als Sieg`() {
        val battle = TestData.battle(id = "b1")
        val rewards = listOf(ChallengeReward("b1", "fremd", 20))

        assertEquals(0, CrewBattle.wonBattles(listOf(battle), rewards, listOf("u1", "u2")))
    }

    /**
     * Im Battle bekommt nur die Siegercrew etwas - und dort nach Leistung.
     * Die Beitraege, die hereingegeben werden, sind ohnehin nur die der
     * eigenen Crew.
     */
    @Test
    fun `der Topf eines gewonnenen Battles geht nach Leistung`() {
        val battle = TestData.battle(id = "b1", goal = 10, reward = 100)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada"), TestData.profile("u2", "Linus")),
            challenges = listOf(battle)
        )

        val award = ChallengeManager.pendingAward(
            battle,
            listOf(TestData.profile("u1", "Ada") to 9, TestData.profile("u2", "Linus") to 1),
            snapshot
        )

        assertEquals(
            listOf(PendingAward.Share("u1", 90), PendingAward.Share("u2", 10)),
            award?.shares
        )
    }

    /** Hat die andere Crew schon kassiert, ist der Battle verloren. */
    @Test
    fun `nach der Auszahlung an die andere Crew gibt es nichts mehr`() {
        val battle = TestData.battle(id = "b1", goal = 10, reward = 100)
        val snapshot = TestData.snapshot(
            members = listOf(TestData.profile("u1", "Ada")),
            challenges = listOf(battle),
            rewards = listOf(ChallengeReward("b1", "fremd", 100))
        )

        assertNull(
            ChallengeManager.pendingAward(
                battle,
                listOf(TestData.profile("u1", "Ada") to 20),
                snapshot
            )
        )
    }

    /** Eine gewoehnliche Challenge ist kein gewonnener Battle. */
    @Test
    fun `nur Battles zaehlen als gewonnene Battles`() {
        val challenge = TestData.challenge(id = "c1")
        val rewards = listOf(ChallengeReward("c1", "u1", 20))

        assertEquals(0, CrewBattle.wonBattles(listOf(challenge), rewards, listOf("u1")))
    }
}
