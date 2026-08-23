package com.example.mobilese.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * Nimmt die Bestaetigung des Telefons entgegen.
 *
 * Der Gegenpart zu WatchWorkoutService auf dem Telefon, und der zweite Weg
 * ueber dieselbe Datenschicht - diesmal vom Telefon zur Uhr. Er wird von den
 * Play-Diensten gestartet, sobald etwas hereinkommt, auch wenn auf der Uhr
 * gerade nichts offen ist.
 *
 * Warum das ueberhaupt jemand braucht: bis hierher endete das Training auf der
 * Uhr mit "abgegeben" und danach kam nichts mehr. Ob aus dem Workout ein
 * Eintrag in der Crew wurde oder ob es auf dem Telefon noch auf Foto und Ort
 * wartet, war von der Uhr aus nicht zu erkennen. Jetzt schliesst sich der
 * Kreis: die Meldung kommt, wenn es wirklich gespeichert ist.
 */
class PhoneAckService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            // Geloeschtes ignorieren: das sind die Elemente, die dieser Dienst
            // selbst gleich unten wieder abraeumt.
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (!event.dataItem.uri.path.orEmpty().startsWith(WatchProtocol.PATH_LOGGED)) continue

            val data = DataMapItem.fromDataItem(event.dataItem).dataMap
            val sport = data.getString(WatchProtocol.KEY_SPORT).orEmpty()
            val minutes = data.getInt(WatchProtocol.KEY_MINUTES, 0)
            if (sport.isEmpty() || minutes <= 0) {
                Log.w("CrewFitWear", "Confirmation without sport or duration, ignored")
                continue
            }

            val entry = LastLogged.Entry(
                sport = sport,
                minutes = minutes,
                points = data.getInt(WatchProtocol.KEY_POINTS, 0)
            )

            LastLogged.save(this, entry)
            announce(entry)

            // Aufraeumen, wie es das Telefon mit dem Workout auch tut: das
            // Element liegt sonst dauerhaft auf beiden Geraeten.
            Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri)
        }
    }

    private fun announce(entry: LastLogged.Entry) {
        WatchNotifications.showLogged(
            context = this,
            title = getString(R.string.logged_title),
            text = summary(this, entry)
        )
    }

    companion object {

        /**
         * Die Zeile, die auf der Uhr steht - in der Meldung wie auf dem
         * Startbildschirm.
         *
         * Ohne Punkte, wenn es keine gab: ein Training unter zehn Minuten ist
         * in der Rangliste nichts wert, und "+0 points" liest sich wie ein
         * Fehler statt wie eine Regel.
         */
        fun summary(context: Context, entry: LastLogged.Entry): String =
            if (entry.points > 0) {
                context.getString(
                    R.string.logged_text_points,
                    entry.sport,
                    entry.minutes,
                    entry.points
                )
            } else {
                context.getString(R.string.logged_text, entry.sport, entry.minutes)
            }
    }
}
