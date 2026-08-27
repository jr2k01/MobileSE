package com.example.mobilese

/**
 * Eine noch nicht ausgeschuettete Belohnung: wer bekommt fuer welche Challenge
 * wie viele Punkte.
 *
 * Die Punkte stehen je Person und nicht als ein Wert fuer alle: seit der Topf
 * nach Leistung verteilt wird, bekommt nicht mehr jeder dasselbe.
 */
data class PendingAward(
    val challengeId: String,
    val shares: List<Share>
) {
    data class Share(val userId: String, val points: Int)
}

object ChallengeManager {

    /**
     * Der Beitrag jedes Crew-Mitglieds zu einer Challenge, absteigend sortiert.
     * Grundlage sind die bereits geladenen Daten aus dem Snapshot.
     *
     * Gezaehlt wird je Person und danach zusammengezaehlt. Die Summe der
     * Beitraege ist deshalb immer der Gesamtstand - auch bei Trainingstagen,
     * wo zwei Mitglieder am selben Tag als zwei Tage zaehlen. Anders liesse
     * sich der Balken nicht in Beitraege zerlegen.
     */
    fun progressByMember(challenge: Challenge, snapshot: CrewSnapshot): List<Pair<UserProfile, Int>> {
        val type = ChallengeType.fromStored(challenge.type)

        // Nach Ablauf der Frist zaehlt nichts mehr dazu. Damit bleibt ein
        // Stand, der bis zum Stichtag unter dem Ziel lag, fuer immer darunter -
        // und die Belohnung wird nie faellig. Genau das ist der Sinn einer
        // Frist, und sie braucht dafuer keine eigene Pruefung weiter unten.
        val activitiesByUser = snapshot.activities
            .filter {
                ChallengeDeadline.countsTowards(challenge.startsAt, challenge.deadline, it.timestamp)
            }
            .groupBy { it.userId }

        // Schritte tragen ihren Tag schon als Datum, brauchen also keinen
        // Umweg ueber den Zeitstempel.
        val stepsByUser = snapshot.stepDays
            .filter { day ->
                ChallengeDeadline.countsOnDay(challenge.startsAt, challenge.deadline, day.day)
            }
            .groupBy { it.userId }

        return snapshot.members
            .map { member ->
                member to ChallengeCalculator.progressOf(
                    type,
                    activitiesByUser[member.id].orEmpty(),
                    stepsByUser[member.id].orEmpty()
                )
            }
            .sortedByDescending { it.second }
    }

    /**
     * Der Stand der gegnerischen Crew in einem Battle.
     *
     * Gerechnet wie bei der eigenen Crew: erst je Mitglied, dann
     * zusammengezaehlt. Der Umweg ueber die einzelnen Personen ist noetig, weil
     * sich nicht jede Art einfach aufsummieren laesst - bei Trainingstagen
     * zaehlen zwei Mitglieder am selben Tag als zwei Tage. Ueber alle
     * Aktivitaeten auf einmal gerechnet waere es einer, und der Gegner stuende
     * schlechter da, als er ist.
     */
    fun progressOfOpponent(challenge: Challenge, opponent: OpponentProgress): Int {
        val type = ChallengeType.fromStored(challenge.type)

        val activitiesByUser = opponent.activities
            .filter {
                ChallengeDeadline.countsTowards(challenge.startsAt, challenge.deadline, it.timestamp)
            }
            .groupBy { it.userId }

        val stepsByUser = opponent.stepDays
            .filter { day ->
                ChallengeDeadline.countsOnDay(challenge.startsAt, challenge.deadline, day.day)
            }
            .groupBy { it.userId }

        return opponent.memberIds.sumOf { id ->
            ChallengeCalculator.progressOf(
                type,
                activitiesByUser[id].orEmpty(),
                stepsByUser[id].orEmpty()
            )
        }
    }

    /**
     * Ermittelt, wer fuer eine Challenge noch wie viele Punkte bekommt.
     * Gibt null zurueck, wenn nichts zu tun ist - das Ziel ist noch nicht
     * erreicht, alle wurden bereits bedacht, oder der Topf ist leer.
     *
     * Der Topf geht nach Leistung: wer neun Zehntel des Ziels geschafft hat,
     * bekommt neun Zehntel der Punkte. Wer nichts beigetragen hat, geht leer
     * aus - siehe [ChallengeCalculator.shareOut].
     *
     * Die Trennung von der Datenbank ist Absicht: vorher fragte diese Logik
     * fuer jede teilnehmende Person einzeln nach, ob sie schon belohnt wurde.
     * Bei fuenf Mitgliedern und drei Challenges waren das fuenfzehn Abfragen
     * bei jedem Oeffnen der Rangliste. Der Stand der Belohnungen kommt jetzt
     * aus dem bereits geladenen [CrewSnapshot].
     */
    fun pendingAward(
        challenge: Challenge,
        contributions: List<Pair<UserProfile, Int>>,
        snapshot: CrewSnapshot
    ): PendingAward? {
        if (contributions.isEmpty()) return null
        if (contributions.sumOf { it.second } < challenge.goal) return null

        val memberIds = contributions.map { it.first.id }

        val alreadyRewarded = snapshot.rewards
            .filter { it.challengeId == challenge.id }
            .map { it.userId }
            .toSet()

        // Ein Battle ist entschieden, sobald irgendwo ausgeschuettet wurde -
        // und wenn das nicht an die eigenen Mitglieder ging, hat die andere
        // Crew ihn gewonnen. Ohne diese Zeile bekaeme auch die zweite Crew
        // ihre Punkte, sobald sie das Ziel spaeter noch erreicht, und "wer
        // zuerst da ist" waere bedeutungslos.
        //
        // Bei einem Battle sind [contributions] ohnehin nur die Mitglieder der
        // eigenen Crew - die Gegenseite bekommt hier also nichts, auch nicht
        // anteilig.
        if (challenge.isBattle && alreadyRewarded.any { it !in memberIds }) return null

        val shares = ChallengeCalculator.shareOut(
            challenge.reward,
            contributions.map { it.second }
        )

        val pending = contributions.mapIndexedNotNull { index, (member, _) ->
            // Wer nichts beigetragen hat, bekommt nichts - und wer schon
            // bedacht wurde, kein zweites Mal.
            val points = shares[index]
            if (points <= 0 || member.id in alreadyRewarded) null
            else PendingAward.Share(member.id, points)
        }

        return if (pending.isEmpty()) null else PendingAward(challenge.id, pending)
    }
}
