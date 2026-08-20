package com.example.mobilese

/**
 * Die Zeichen, mit denen auf ein Workout reagiert werden kann.
 *
 * Eine feste, kurze Auswahl und keine freie Eingabe: fuenf Zeichen passen in
 * eine Reihe, sind ohne Nachdenken zu treffen, und die Zaehlung darunter
 * bleibt lesbar. Bei freier Auswahl stuenden unter einem Workout schnell
 * fuenfzehn verschiedene Zeichen mit je einer Eins.
 *
 * Bewusst keine Bewertung dabei - kein Daumen nach unten. Die App soll zum
 * Training anstacheln, und ein Werkzeug zum Danebenschiessen braucht dafuer
 * niemand.
 *
 * Die Zeichen werden als Text gespeichert, nicht als Zahl. Ein gespeichertes
 * "💪" bleibt lesbar, auch wenn diese Liste spaeter umsortiert wird;
 * eine Zahl waere danach dem falschen Zeichen zugeordnet.
 */
object Reactions {

    const val STRONG = "💪"
    const val FIRE = "🔥"
    const val CLAP = "👏"
    const val PARTY = "🎉"
    const val THUMBS_UP = "👍"

    val ALL = listOf(STRONG, FIRE, CLAP, PARTY, THUMBS_UP)

    /** Ob das Zeichen eines der vorgesehenen ist. */
    fun isKnown(emoji: String): Boolean = emoji in ALL

    /**
     * Zaehlt, wie oft jedes Zeichen vergeben wurde.
     *
     * Nur die bekannten Zeichen: taucht in der Datenbank etwas anderes auf -
     * aus einer aelteren Fassung oder von Hand eingetragen -, wird es nicht
     * gezeigt, statt eine Reihe zu sprengen, die auf fuenf Spalten ausgelegt
     * ist.
     */
    fun countsOf(reactions: List<ActivityReaction>): Map<String, Int> =
        reactions.map { it.emoji }
            .filter { isKnown(it) }
            .groupingBy { it }
            .eachCount()

    /** Das Zeichen, das diese Person vergeben hat, oder null. */
    fun chosenBy(reactions: List<ActivityReaction>, userId: String?): String? =
        userId?.let { id -> reactions.firstOrNull { it.userId == id }?.emoji }
}
