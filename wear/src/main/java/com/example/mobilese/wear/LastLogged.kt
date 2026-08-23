package com.example.mobilese.wear

import android.content.Context

/**
 * Die letzte Rueckmeldung des Telefons: dieses Workout steht in der Crew.
 *
 * Bis hierher wusste die Uhr nur, dass sie etwas abgegeben hat. Ob daraus ein
 * Eintrag wurde - oder ob es noch als Karte auf dem Startbildschirm des
 * Telefons wartet - erfuhr sie nie. Der Weg dorthin fuehrt ueber dieselbe
 * Datenschicht, nur andersherum: [WatchProtocol.PATH_LOGGED].
 *
 * Genau ein Eintrag, kein Verlauf. Die Uhr ist keine zweite Historie - die
 * steht auf dem Telefon. Hier geht es um die eine Frage nach dem Training:
 * "ist es angekommen?"
 *
 * Er verschwindet, sobald das naechste Workout beginnt: dann ist die Frage
 * beantwortet und die Zeile nur noch Platz, den die Uhr nicht hat.
 */
object LastLogged {

    /** Was von der Bestaetigung uebrig bleibt - genug fuer eine Zeile. */
    data class Entry(val sport: String, val minutes: Int, val points: Int)

    private const val PREFS = "last_logged"
    private const val KEY_SPORT = "sport"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_POINTS = "points"

    fun save(context: Context, entry: Entry) {
        prefs(context).edit()
            .putString(KEY_SPORT, entry.sport)
            .putInt(KEY_MINUTES, entry.minutes)
            .putInt(KEY_POINTS, entry.points)
            .apply()
    }

    fun get(context: Context): Entry? {
        val stored = prefs(context)
        val sport = stored.getString(KEY_SPORT, null) ?: return null
        return Entry(
            sport = sport,
            minutes = stored.getInt(KEY_MINUTES, 0),
            points = stored.getInt(KEY_POINTS, 0)
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
