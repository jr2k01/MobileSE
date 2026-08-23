package com.example.mobilese

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Sagt der Uhr, dass ihr Workout in der Crew angekommen ist.
 *
 * Der Rueckweg ueber dieselbe Datenschicht, die das Workout hergebracht hat -
 * nur andersherum. Bis dahin endete das Training auf der Uhr mit "abgegeben",
 * und ob daraus ein Eintrag wurde oder ob es auf dem Telefon noch auf Foto und
 * Ort wartet, war von dort aus nicht zu erkennen.
 *
 * Verschickt wird erst, wenn die Aktivitaet wirklich gespeichert ist. Eine
 * Bestaetigung beim Oeffnen des Formulars waere leichter zu haben und wertlos:
 * wer abbricht, hat nichts eingetragen.
 *
 * Ohne auf das Ergebnis zu warten. Kommt die Nachricht nicht an, fehlt der Uhr
 * eine Zeile - der Eintrag in der Crew steht trotzdem, und das ist die Sache,
 * um die es geht.
 */
object WatchAck {

    fun confirm(context: Context, endedAt: Long, sport: String, minutes: Int, points: Int) {
        val request = PutDataMapRequest.create("${WatchProtocol.PATH_LOGGED}/$endedAt").apply {
            dataMap.putString(WatchProtocol.KEY_SPORT, sport)
            dataMap.putInt(WatchProtocol.KEY_MINUTES, minutes)
            dataMap.putInt(WatchProtocol.KEY_POINTS, points)
            dataMap.putLong(WatchProtocol.KEY_ENDED_AT, endedAt)
        }.asPutDataRequest()
            // Sofort und nicht bei Gelegenheit: der Nutzer hat das Telefon
            // gerade in der Hand und die Uhr am Arm.
            .setUrgent()

        Wearable.getDataClient(context).putDataItem(request)
            .addOnFailureListener { error ->
                Log.w("WatchAck", "Could not confirm the workout: ${error.message}")
            }
    }
}
