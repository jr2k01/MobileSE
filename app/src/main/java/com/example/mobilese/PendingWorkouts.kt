package com.example.mobilese

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Ein auf der Uhr aufgezeichnetes Workout, das noch nicht in der Crew steht.
 *
 * Die Uhr weiss Sportart, Dauer, Puls und Schritte. Foto und Ort fehlen ihr -
 * beides traegt das Telefon nach, und erst dann wird daraus eine Aktivitaet.
 */
@Serializable
data class PendingWorkout(
    val sport: String,
    val minutes: Int,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    /**
     * Schritte waehrend des Workouts, oder null wenn die Uhr keine zaehlen
     * konnte.
     *
     * Mit Vorgabe, damit die Eintraege lesbar bleiben, die vor dieser Messung
     * abgelegt wurden - eine Warteschlange auf dem Telefon darf eine
     * Aktualisierung der App ueberstehen.
     */
    val steps: Int? = null,
    /** Ende des Workouts in Millisekunden. Dient zugleich als Kennung. */
    val endedAt: Long
)

/**
 * Die Warteschlange der Workouts von der Uhr.
 *
 * Liegt in den SharedPreferences und nicht in der Datenbank: solange Foto und
 * Ort fehlen, ist das kein Eintrag fuer die Crew, sondern eine Notiz an genau
 * dieses Telefon. Ginge sie ueber Supabase, muesste eine halbfertige Aktivitaet
 * dort mit hochgeladen und wieder geloescht werden - und beim Abmelden bliebe
 * sie als Leiche stehen.
 *
 * Eine Liste und nicht ein einzelner Platz: wer morgens laeuft und abends ins
 * Gym geht, ohne dazwischen das Telefon anzufassen, soll nicht das erste
 * Workout verlieren.
 */
object PendingWorkouts {

    private const val PREFS = "pending_workouts"
    private const val KEY_LIST = "list"

    /** Mehr als das braeuchte niemand; die aeltesten fallen hinten heraus. */
    private const val MAX_ENTRIES = 20

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Legt ein Workout ab.
     *
     * Kommt dieselbe Nachricht zweimal an - die Uhr wiederholt sie, wenn die
     * Verbindung wackelt -, bleibt es bei einem Eintrag: [PendingWorkout.endedAt]
     * ist bei einer Wiederholung derselbe Zeitpunkt.
     */
    fun add(context: Context, workout: PendingWorkout) {
        val kept = all(context).filter { it.endedAt != workout.endedAt }
        save(context, (kept + workout).takeLast(MAX_ENTRIES))
    }

    /** Alle wartenden Workouts, aeltestes zuerst. */
    fun all(context: Context): List<PendingWorkout> {
        val stored = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<PendingWorkout>>(stored)
        } catch (e: Exception) {
            // Kaputte Ablage darf die App nicht am Start hindern - im
            // schlimmsten Fall ist eine Notiz weg.
            Log.w("PendingWorkouts", "Could not read the stored workouts: ${e.message}")
            emptyList()
        }
    }

    /** Das aelteste wartende Workout, oder null wenn keines wartet. */
    fun oldest(context: Context): PendingWorkout? = all(context).firstOrNull()

    fun find(context: Context, endedAt: Long): PendingWorkout? =
        all(context).firstOrNull { it.endedAt == endedAt }

    /** Nimmt ein Workout heraus, sobald es als Aktivitaet gespeichert ist. */
    fun remove(context: Context, endedAt: Long) {
        save(context, all(context).filter { it.endedAt != endedAt })
    }

    private fun save(context: Context, workouts: List<PendingWorkout>) {
        // Der Serialisierer ausgeschrieben: die kurze Fassung ohne ihn lief in
        // die gleichnamige Methode von Json und uebersetzte nicht.
        val text = json.encodeToString(ListSerializer(PendingWorkout.serializer()), workouts)
        prefs(context).edit().putString(KEY_LIST, text).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
