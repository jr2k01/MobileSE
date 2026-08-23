package com.example.mobilese

import android.content.Context
import java.text.NumberFormat

/**
 * Die Zeile, die zusammenfasst, was die Uhr gemessen hat.
 *
 * Dauer, Puls und Schritte an zwei Stellen: auf der Karte des Startbildschirms
 * und im Hinweis ueber dem Formular. Puls und Schritte koennen jeweils fehlen -
 * eine Uhr ohne Sensor, eine verweigerte Erlaubnis, ein Yoga-Abend ohne einen
 * einzigen Schritt.
 *
 * Zusammengesetzt statt als fertige Texte in den strings: fuer jede Kombination
 * aus vorhanden und fehlend einen eigenen Text zu pflegen waeren vier je
 * Stelle, und mit der naechsten Messgroesse acht. Uebersetzt werden muessen
 * ohnehin nur die Einheiten.
 */
object WatchFacts {

    /** Auf der Karte, wo die Angaben nebeneinander stehen. */
    const val SEPARATOR_CARD = " · "

    /** Im Hinweis, wo die Zeile Teil eines Satzes ist. */
    const val SEPARATOR_SENTENCE = ", "

    fun line(
        context: Context,
        workout: PendingWorkout,
        separator: String = SEPARATOR_CARD
    ): String {
        val facts = buildList {
            add(context.getString(R.string.watch_fact_minutes, count(workout.minutes)))
            workout.avgHeartRate?.let {
                add(context.getString(R.string.watch_fact_bpm, count(it)))
            }
            // Null Schritte werden nicht erwaehnt: gemessen sind sie zwar, aber
            // "0 steps" sagt niemandem etwas, das er nicht schon weiss.
            workout.steps?.takeIf { it > 0 }?.let {
                add(context.getString(R.string.watch_fact_steps, count(it)))
            }
        }
        return facts.joinToString(separator)
    }

    /**
     * Wie ueberall sonst in der App ueber NumberFormat: 3.240 in Deutschland,
     * 3,240 im Englischen.
     */
    private fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)
}
