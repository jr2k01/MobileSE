package com.example.mobilese

/**
 * Das Level einer ganzen Crew.
 *
 * Das Gegenstueck zu [Levels], das eine einzelne Person bewertet. Eine Crew
 * soll ebenfalls wachsen koennen, und gewonnene Battles sollen daran den
 * groessten Anteil haben - sonst waere ein Battle nur eine Challenge mit
 * Zuschauern.
 *
 * Gerechnet wird mit dem **Durchschnitt** der Mitgliederpunkte und nicht mit
 * ihrer Summe. Sonst waere das Crew-Level vor allem eine Anzeige der
 * Crew-Groesse: zehn gemuetliche Mitglieder haetten mehr als drei sehr fleissige,
 * und eine Crew koennte aufsteigen, indem sie jemanden aufnimmt. So zaehlt,
 * wie aktiv die Crew ist, nicht wie viele sie ist.
 *
 * Die Stufen selbst kommen aus [Levels]. Eine eigene Kurve waere eine zweite
 * Zahlenreihe zum Pflegen, ohne dass sich die Anzeige unterschiede.
 */
object CrewLevel {

    /**
     * Die Erfahrung einer Crew.
     *
     * @param crewPoints Punktestaende aller Mitglieder zusammen, wie sie die
     *        Rangliste ausweist.
     * @param memberCount Zahl der Mitglieder. Null Mitglieder gibt es nicht,
     *        wird aber abgefangen: die Zahl kommt aus geladenen Daten.
     * @param battlesWon gewonnene Crew-Battles, siehe [CrewBattle.wonBattles].
     */
    fun xpOf(crewPoints: Int, memberCount: Int, battlesWon: Int): Int {
        val average = crewPoints.coerceAtLeast(0) / memberCount.coerceAtLeast(1)
        return average + CrewBattle.WIN_BONUS * battlesWon.coerceAtLeast(0)
    }

    /** Level und Fortschritt der Crew, auf derselben Kurve wie bei Personen. */
    fun of(crewPoints: Int, memberCount: Int, battlesWon: Int): LevelProgress =
        Levels.of(xpOf(crewPoints, memberCount, battlesWon))

    /** Das Crew-Level direkt aus einem geladenen Bestand. */
    fun of(snapshot: CrewSnapshot): LevelProgress {
        val entries = Scoreboard.build(snapshot)
        return of(
            crewPoints = entries.sumOf { it.points },
            memberCount = snapshot.members.size,
            battlesWon = CrewBattle.wonBattles(
                snapshot.challenges,
                snapshot.rewards,
                snapshot.members.map { it.id }
            )
        )
    }
}
