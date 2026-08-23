package com.example.mobilese

import android.os.Bundle
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Nimmt die Workouts entgegen, die auf der Uhr beendet wurden.
 *
 * Der Dienst wird von den Play-Diensten gestartet, sobald etwas hereinkommt -
 * auch wenn die App gerade nicht offen ist. Deshalb wird hier nichts
 * hochgeladen: das Workout wandert in die Warteschlange, und der Nutzer
 * bekommt eine Benachrichtigung. Foto und Ort fehlen noch, und die kann nur er
 * beisteuern.
 *
 * Die Uhr legt jedes Workout als eigenes Element der Datenschicht ab. Was hier
 * ankommt, kann also auch von gestern sein - etwa wenn das Telefon beim
 * Training zu Hause lag.
 */
class WatchWorkoutService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            // Geloeschtes ignorieren: das sind die Elemente, die diese App
            // selbst gleich unten wieder abraeumt.
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (!event.dataItem.uri.path.orEmpty().startsWith(WatchProtocol.PATH_WORKOUT)) continue

            val data = DataMapItem.fromDataItem(event.dataItem).dataMap
            val sport = data.getString(WatchProtocol.KEY_SPORT).orEmpty()
            val minutes = data.getInt(WatchProtocol.KEY_MINUTES, 0)
            if (sport.isEmpty() || minutes <= 0) {
                Log.w("WatchWorkout", "Entry without sport or duration, ignored")
                continue
            }

            val workout = PendingWorkout(
                sport = sport,
                minutes = minutes,
                // Aus der 0 der Uhr wird hier null: die App kennt "kein Puls
                // gemessen" als fehlenden Wert, nicht als Herz, das stillsteht.
                avgHeartRate = data.getInt(WatchProtocol.KEY_AVG_BPM, WatchProtocol.NO_BPM)
                    .takeIf { it > WatchProtocol.NO_BPM },
                maxHeartRate = data.getInt(WatchProtocol.KEY_MAX_BPM, WatchProtocol.NO_BPM)
                    .takeIf { it > WatchProtocol.NO_BPM },
                // Bei den Schritten zaehlt die Null mit: beim Yoga ist sie eine
                // richtige Messung. Fehlend ist nur, was die Uhr als
                // NO_STEPS geschickt hat - oder was von einer aelteren Fassung
                // der Uhr-App stammt, die das Feld noch nicht kannte.
                steps = data.getInt(WatchProtocol.KEY_STEPS, WatchProtocol.NO_STEPS)
                    .takeIf { it > WatchProtocol.NO_STEPS },
                endedAt = data.getLong(WatchProtocol.KEY_ENDED_AT, System.currentTimeMillis())
            )

            PendingWorkouts.add(this, workout)
            announce(workout)

            // Aufraeumen: das Element liegt sonst dauerhaft auf beiden Geraeten.
            // Erst nach dem Ablegen in der Warteschlange - andersherum waere das
            // Workout weg, wenn der Dienst dazwischen abgeschossen wird.
            Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri)
        }
    }

    private fun announce(workout: PendingWorkout) {
        Notifications.show(
            context = this,
            channelId = Notifications.CHANNEL_WATCH,
            title = getString(R.string.watch_workout_title),
            text = resources.getQuantityString(
                R.plurals.watch_workout_text, workout.minutes, workout.sport, workout.minutes
            ),
            // Feste Kennung: kommen zwei Workouts an, bevor das erste ergaenzt
            // wurde, ersetzt die Meldung die alte. Zwei Zeilen im Schacht, die
            // beide in dieselbe Warteschlange fuehren, waeren Verdopplung ohne
            // Nutzen.
            id = WATCH_NOTIFICATION_ID,
            // Damit das Formular weiss, welches Workout gemeint ist.
            extras = Bundle().apply {
                putLong(WorkoutTrackingActivity.EXTRA_PENDING_AT, workout.endedAt)
            }
        )
    }

    private companion object {
        const val WATCH_NOTIFICATION_ID = 4711
    }
}
