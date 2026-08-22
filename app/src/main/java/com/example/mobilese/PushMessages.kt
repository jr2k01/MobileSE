package com.example.mobilese

/**
 * Uebersetzt eine hereingekommene Push-Nachricht in das, was auf dem Bildschirm
 * steht.
 *
 * Reine Logik ueber der Datenfracht, ohne Android - deshalb ohne Emulator
 * testbar. Wichtig ist hier vor allem, was bei unvollstaendigen Daten passiert:
 * die Nachricht kommt vom Server, und eine fehlende Angabe darf hoechstens eine
 * duerftige Meldung ergeben, niemals einen Absturz beim Empfang.
 */
object PushMessages {

    /** Die Arten von Nachrichten, die der Server schickt. */
    const val TYPE_ACTIVITY = "activity"
    const val TYPE_OVERTAKE = "overtake"
    const val TYPE_LEAD = "lead"

    /** Eine andere Crew hat die eigene zu einem Battle herausgefordert. */
    const val TYPE_BATTLE = "battle"

    data class Content(val channelId: String, val title: String, val text: String, val id: Int)

    /**
     * @param data die Nutzdaten der Push-Nachricht
     * @param strings Zugriff auf die Texte der App
     */
    fun from(data: Map<String, String>, strings: Strings): Content? {
        val name = data["name"].orEmpty().ifBlank { strings.unknownMember() }

        return when (data["type"]) {
            TYPE_ACTIVITY -> Content(
                channelId = Notifications.CHANNEL_ACTIVITIES,
                title = strings.activityTitle(),
                text = strings.activityText(name, data["sport"].orEmpty(), data["duration"].orEmpty()),
                // Feste Kennung je Art: es soll die letzte Meldung ersetzen und
                // nicht ein Stapel aus zwanzig Workouts entstehen.
                id = ID_ACTIVITY
            )

            TYPE_OVERTAKE -> Content(
                channelId = Notifications.CHANNEL_RANKING,
                title = strings.rankingTitle(),
                text = strings.overtakeText(name, data["rank"]?.toIntOrNull() ?: 0),
                id = ID_RANKING
            )

            TYPE_LEAD -> Content(
                channelId = Notifications.CHANNEL_RANKING,
                title = strings.rankingTitle(),
                text = strings.leadText(name),
                id = ID_RANKING
            )

            TYPE_BATTLE -> Content(
                channelId = Notifications.CHANNEL_BATTLE,
                title = strings.battleTitle(),
                // Der Name der Crew steht in "crew" und nicht in "name": es ist
                // keine Person, die herausfordert, sondern eine Crew.
                text = strings.battleText(
                    data["crew"].orEmpty().ifBlank { strings.unknownCrew() },
                    data["challenge_type"].orEmpty(),
                    data["goal"]?.toIntOrNull() ?: 0
                ),
                id = ID_BATTLE
            )

            // Eine Art, die diese Version nicht kennt - etwa weil der Server
            // schon weiter ist als die installierte App. Nichts anzeigen ist
            // besser als eine leere Meldung.
            else -> null
        }
    }

    /** Die Texte, die der Inhalt braucht. Als Schnittstelle, damit die Zuordnung ohne Android geprueft werden kann. */
    interface Strings {
        fun unknownMember(): String
        fun activityTitle(): String
        fun activityText(name: String, sport: String, duration: String): String
        fun rankingTitle(): String
        fun overtakeText(name: String, rank: Int): String
        fun leadText(name: String): String
        fun unknownCrew(): String
        fun battleTitle(): String

        /**
         * @param type der in der Datenbank gespeicherte Name der Challenge-Art.
         *        Uebersetzt wird er erst hier - der Server kennt die
         *        Sprachdateien der App nicht.
         */
        fun battleText(crew: String, type: String, goal: Int): String
    }

    private const val ID_ACTIVITY = 3001
    private const val ID_RANKING = 3002
    private const val ID_BATTLE = 3003
}
