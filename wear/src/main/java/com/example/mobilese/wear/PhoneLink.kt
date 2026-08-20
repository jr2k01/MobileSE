package com.example.mobilese.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Legt das fertige Workout fuer das Telefon ab.
 *
 * Ueber [Wearable.getDataClient] und nicht ueber den MessageClient: eine
 * Nachricht kommt nur an, solange das Telefon gerade in Reichweite ist -
 * sonst ist sie weg. Beim Laufen hat man das Telefon aber oft nicht dabei,
 * und genau dafuer ist die Uhr da. Ein Datensatz bleibt liegen und wird
 * uebertragen, sobald sich beide wiedersehen.
 *
 * Jedes Workout bekommt einen eigenen Pfad mit seinem Endzeitpunkt darin.
 * Zwei Workouts unter demselben Pfad waeren fuer die Datenschicht dasselbe
 * Element, und das zweite ueberschriebe das erste, bevor das Telefon es
 * gesehen hat.
 */
object PhoneLink {

    /**
     * @return ob das Workout abgelegt werden konnte. Das heisst noch nicht,
     *         dass das Telefon es schon hat - nur, dass es nicht verloren geht.
     */
    suspend fun send(
        context: Context,
        sport: String,
        minutes: Int,
        averageBpm: Int,
        maxBpm: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val endedAt = System.currentTimeMillis()
        val request = PutDataMapRequest.create("${WatchProtocol.PATH_WORKOUT}/$endedAt").apply {
            dataMap.putString(WatchProtocol.KEY_SPORT, sport)
            dataMap.putInt(WatchProtocol.KEY_MINUTES, minutes)
            dataMap.putInt(WatchProtocol.KEY_AVG_BPM, averageBpm)
            dataMap.putInt(WatchProtocol.KEY_MAX_BPM, maxBpm)
            dataMap.putLong(WatchProtocol.KEY_ENDED_AT, endedAt)
        }.asPutDataRequest()
            // Sofort uebertragen, statt auf einen guenstigen Zeitpunkt zu
            // warten: der Nutzer steht gerade daneben und will es im Telefon
            // sehen.
            .setUrgent()

        try {
            await(Wearable.getDataClient(context).putDataItem(request)) != null
        } catch (e: Exception) {
            Log.e("CrewFitWear", "Could not store the workout: ${e.message}")
            false
        }
    }

    /**
     * Macht aus einem Task eine suspend-Funktion.
     *
     * Von Hand statt ueber kotlinx-coroutines-play-services: die Bibliothek
     * braechte fuer genau diese zehn Zeilen eine weitere Abhaengigkeit mit.
     */
    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T? =
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { finished ->
                if (finished.isSuccessful) {
                    continuation.resume(finished.result)
                } else {
                    Log.w("CrewFitWear", "Task failed: ${finished.exception?.message}")
                    continuation.resume(null)
                }
            }
        }
}
