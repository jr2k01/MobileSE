package com.example.mobilese

/**
 * Wer bei einem Workout zusammen trainiert hat.
 *
 * Steht fuer sich und ohne Android-Bezug, weil dieselbe Zeile an drei Stellen
 * gebraucht wird: im Feed der Crew, in der Historie und in der Einzelansicht.
 * Als dreimal abgetippte Schleife waeren die drei Anzeigen frueher oder spaeter
 * auseinandergelaufen - und dass in der Crew jemand anderes genannt wird als
 * im Detail, waere schlimmer als gar keine Anzeige.
 *
 * Genannt werden alle Beteiligten einschliesslich des Verfassers, denn die
 * gespeicherte Liste enthaelt nur die jeweils anderen. Wer die Zeile in der
 * Crew liest, soll die Gruppe sehen und nicht raten muessen, wer noch dazu
 * gehoert.
 */
object JointWorkout {

    /** Ob das Workout gemeinsam absolviert wurde und damit doppelt zaehlt. */
    fun isJoint(activity: Activity): Boolean = !activity.partnerIds.isNullOrEmpty()

    /**
     * Die Namen aller Beteiligten, beginnend mit dem Verfasser.
     *
     * @param nameById Nachschlagewerk aus den bereits geladenen Profilen.
     *        Wer darin fehlt - etwa weil die Person die Crew verlassen hat -
     *        erscheint als [unknown]. Weggelassen wird niemand: die Zahl der
     *        Namen soll der Groesse der Gruppe entsprechen.
     */
    fun participants(
        activity: Activity,
        nameById: Map<String, String>,
        unknown: String
    ): List<String> {
        if (!isJoint(activity)) return emptyList()

        val ids = listOf(activity.userId) + activity.partnerIds.orEmpty()
        return ids.distinct().map { id ->
            nameById[id]?.takeIf { it.isNotBlank() } ?: unknown
        }
    }
}
