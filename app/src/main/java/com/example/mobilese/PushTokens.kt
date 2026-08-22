package com.example.mobilese

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Meldet dieses Geraet fuer Push an und wieder ab.
 *
 * Alles hier faengt den Fall ab, dass Firebase gar nicht eingerichtet ist. Ohne
 * google-services.json wird das Plugin nicht angewendet (siehe
 * app/build.gradle.kts), die Bibliothek findet beim Zugriff keine Konfiguration
 * und wirft. Das darf die Anmeldung nicht aufhalten: ohne Push ist die App
 * vollstaendig benutzbar, sie sagt einem nur nichts von selbst.
 */
object PushTokens {

    /**
     * Eigener Bereich statt des lifecycleScope einer Activity.
     *
     * Das Anmelden geschieht auf dem Login-Bildschirm, und der beendet sich
     * unmittelbar danach. An seinen Lebenszyklus gehaengt wurde das Holen der
     * Kennung mitten im Lauf abgebrochen - sichtbar als "Job was cancelled",
     * und in der Datenbank stand nie eine Zeile. Die Anmeldung des Geraets
     * gehoert zu keinem Bildschirm; sie darf ihn ueberleben.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Holt die Kennung dieses Geraets und hinterlegt sie beim angemeldeten
     * Nutzer. Nach jeder Anmeldung aufzurufen - die Kennung gilt pro Geraet,
     * die Zuordnung zum Konto aber nicht.
     *
     * Laeuft im Hintergrund weiter und haelt nichts auf.
     */
    fun register(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val token = currentToken() ?: return@launch
            val stored = AppRepository.get(app).savePushToken(token)
            // Ohne diese Zeile war beim Suchen nicht zu unterscheiden, ob die
            // Anmeldung gar nicht lief, keine Kennung bekam oder an der
            // Datenbank scheiterte - alle drei Faelle sahen im Log gleich
            // still aus.
            Log.i("CrewFitPush", if (stored) "Device registered for push" else "Not registered, nobody is signed in")
        }
    }

    /** Nimmt die Kennung wieder aus der Datenbank, damit das Geraet nichts mehr bekommt. */
    suspend fun unregister(context: Context) {
        val token = currentToken() ?: return
        AppRepository.get(context).deletePushToken(token)
    }

    /**
     * Die aktuelle Kennung, oder null wenn Firebase nicht eingerichtet ist oder
     * gerade nicht erreichbar war.
     *
     * FirebaseMessaging arbeitet mit Task und nicht mit Coroutinen; das
     * Umhaengen von Hand erspart die zusaetzliche Abhaengigkeit
     * kotlinx-coroutines-play-services, die genau diese acht Zeilen mitbraechte.
     */
    private suspend fun currentToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        Log.w("CrewFitPush", "No push token: ${task.exception?.message}")
                        continuation.resume(null)
                    }
                }
        }
    } catch (e: Exception) {
        // Kein Firebase-Projekt hinterlegt. Kein Grund, irgendetwas abzubrechen.
        Log.w("CrewFitPush", "Push is not set up: ${e.message}")
        null
    }
}
