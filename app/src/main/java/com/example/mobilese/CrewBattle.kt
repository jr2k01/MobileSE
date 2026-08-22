package com.example.mobilese

/**
 * Challenges zwischen zwei Crews.
 *
 * Eine gewoehnliche Challenge ist ein gemeinsames Ziel: die Crew zieht an einem
 * Strang und bekommt am Ende alle Punkte. Ein Battle stellt dasselbe Ziel zwei
 * Crews nebeneinander - wer es zuerst erreicht, gewinnt, und nur dort gibt es
 * Punkte.
 *
 * Gespeichert wird das als eine einzige Zeile in `challenges`, mit der
 * herausgeforderten Crew in `opponent_crew_id`. Zwei Zeilen - eine je Crew -
 * waeren die naheliegende Alternative gewesen und die schlechtere: dann
 * koennten Ziel, Frist oder Art auseinanderlaufen, und der Battle waere kein
 * Battle mehr, sondern zwei Challenges, die sich zufaellig aehneln.
 *
 * Reine Rechnung ohne Android- und Netzzugriff, wie [Scoreboard] und
 * [Levels] - und damit ohne Emulator zu pruefen.
 */
object CrewBattle {

    /** Herausgefordert, aber noch nicht beantwortet. Es zaehlt noch nichts. */
    const val STATUS_PENDING = "pending"

    /** Angenommen - ab jetzt laeuft der Battle. */
    const val STATUS_ACCEPTED = "accepted"

    /** Abgelehnt. Die Zeile bleibt stehen, damit beide Seiten sehen, was war. */
    const val STATUS_DECLINED = "declined"

    /**
     * Was ein Battle fuer die eigene Crew wert ist, wenn sie ihn gewinnt.
     *
     * Kommt oben auf die Punkte, aus denen sich das Crew-Level ergibt. Bewusst
     * ein spuerbarer Sprung: ein Battle soll das Level der Crew bewegen, sonst
     * waere er nur eine Challenge mit Publikum.
     */
    const val WIN_BONUS = 250

    /**
     * Die gegnerische Crew aus Sicht von [myCrewCode].
     *
     * Beide Seiten lesen dieselbe Zeile, sehen darin aber verschiedene Gegner:
     * fuer die herausfordernde Crew ist es `opponent_crew_id`, fuer die
     * herausgeforderte die Crew in `crew_id`. Null, wenn die Challenge kein
     * Battle ist oder die eigene Crew gar nicht beteiligt.
     */
    fun opponentOf(challenge: Challenge, myCrewCode: String): String? = when {
        !challenge.isBattle -> null
        challenge.crewId == myCrewCode -> challenge.opponentCrewId
        challenge.opponentCrewId == myCrewCode -> challenge.crewId
        else -> null
    }

    /** Ob die eigene Crew die herausgeforderte ist - nur sie darf annehmen. */
    fun wasChallenged(challenge: Challenge, myCrewCode: String): Boolean =
        challenge.isBattle && challenge.opponentCrewId == myCrewCode

    /**
     * Ob der Battle laeuft.
     *
     * Ein fehlender Status gilt als angenommen: er steht nur an Battles, und
     * eine Zeile aus einer Datenbank ohne diese Spalte soll nicht stillschweigend
     * als abgelehnt gelten.
     */
    fun isRunning(challenge: Challenge): Boolean =
        challenge.battleStatus == null || challenge.battleStatus == STATUS_ACCEPTED

    fun isPending(challenge: Challenge): Boolean = challenge.battleStatus == STATUS_PENDING

    fun isDeclined(challenge: Challenge): Boolean = challenge.battleStatus == STATUS_DECLINED

    /** Wie ein Battle gerade steht. */
    enum class Standing { LEADING, BEHIND, TIED, WON, LOST }

    /**
     * Der Stand aus Sicht der eigenen Crew.
     *
     * Entschieden ist erst, wenn eine Seite das Ziel erreicht hat. Ein
     * Gleichstand am Ziel - beide erreichen es zwischen zwei Aufrufen - geht
     * an die eigene Crew nicht verloren: sie hat das Ziel ebenfalls geschafft,
     * und wer zuerst ausgezahlt wird, entscheidet [ChallengeManager] anhand der
     * bereits vergebenen Belohnungen. Hier geht es nur um die Anzeige.
     */
    fun standingOf(mine: Int, theirs: Int, goal: Int): Standing = when {
        mine >= goal -> Standing.WON
        theirs >= goal -> Standing.LOST
        mine > theirs -> Standing.LEADING
        mine < theirs -> Standing.BEHIND
        else -> Standing.TIED
    }

    /**
     * Die Battles, die eine Crew gewonnen hat.
     *
     * Gewonnen heisst: fuer diesen Battle wurde bereits an Mitglieder dieser
     * Crew ausgeschuettet. Das steht ohnehin in den Belohnungen, es braucht
     * also keine eigene Spalte fuer den Sieger - und damit auch keine, die mit
     * den Belohnungen aus dem Tritt geraten koennte.
     */
    fun wonBattles(
        challenges: List<Challenge>,
        rewards: List<ChallengeReward>,
        memberIds: Collection<String>
    ): Int {
        val members = memberIds.toSet()
        return challenges.count { challenge ->
            challenge.isBattle && rewards.any {
                it.challengeId == challenge.id && it.userId in members
            }
        }
    }
}
