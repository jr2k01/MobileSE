package com.example.mobilese.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sieht nach, ob das Telefon gerade in Reichweite ist.
 *
 * Gefragt wird nach den *verbundenen* Geraeten und davon nach denen, die
 * `isNearby` melden: nur zu diesen besteht eine direkte Verbindung. Ein Geraet,
 * das nur ueber Googles Server erreichbar ist, gilt hier als abwesend - dorthin
 * kann das Workout Stunden brauchen, und genau das soll der Hinweis ja sagen.
 *
 * Der genauere Weg waere der CapabilityClient: das Telefon meldet eine
 * Faehigkeit an, und die Uhr fragt danach: dann waere auch ein gekoppeltes
 * Telefon *ohne* CrewFit als abwesend erkannt. Der Weg wurde ausprobiert und
 * wieder verworfen. Die Meldung erreicht die Uhr erst, nachdem die
 * Play-Dienste sie uebertragen haben, und auf einem frisch eingerichteten Paar
 * dauert das lange genug, dass die Uhr ein danebenliegendes Telefon als
 * unerreichbar bezeichnet - eine falsche Auskunft ist schlechter als eine
 * ungenaue. Der Fall, den der CapabilityClient zusaetzlich abdeckte, kommt
 * ausserdem kaum vor: die Uhr-App installiert sich niemand ohne die auf dem
 * Telefon.
 *
 * Das Ergebnis aendert nichts am Ablauf - ein Workout wird immer abgelegt und
 * wartet notfalls. Es aendert nur, was der Uhr an Text zusteht: "liegt auf
 * deinem Telefon" ist gelogen, solange das Telefon zu Hause liegt.
 */
object PhoneReach {

    /**
     * @return ob ein Geraet in direkter Reichweite ist. Bei einem Fehler
     *         `true` - eine kaputte Abfrage darf nicht dazu fuehren, dass die
     *         Uhr eine funktionierende Verbindung als Stoerung meldet.
     */
    suspend fun isPhoneNearby(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.awaitOrNull()
                ?: return@withContext true

            nodes.any { it.isNearby }
        } catch (e: Exception) {
            Log.w("CrewFitWear", "Could not ask for the phone: ${e.message}")
            true
        }
    }
}
