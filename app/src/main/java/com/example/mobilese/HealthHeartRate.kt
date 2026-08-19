package com.example.mobilese

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Der Puls aus Health Connect - also das, was die Uhr aufgezeichnet hat.
 *
 * **Es gibt in Android keine Schnittstelle, um sich mit einer Uhr zu
 * verbinden.** Eine Uhr haengt an ihrer eigenen App - Wear OS, Samsung Health,
 * Garmin Connect, Fitbit - und schreibt ihre Messwerte nach Health Connect.
 * Andere Apps lesen von dort. Genau diesen Weg geht CrewFit schon bei den
 * Schritten; der Puls ist derselbe Weg mit einem anderen Datentyp.
 *
 * Der Vorteil: es funktioniert mit jeder Uhr, die nach Health Connect
 * schreibt, ohne dass CrewFit einen einzigen Hersteller kennen muss. Eine
 * eigene Anbindung je Hersteller waere pro Marke ein eigenes SDK - und fuer
 * eine Apple Watch gaebe es unter Android ohnehin keinen Weg.
 *
 * Gelesen wird zum Zeitpunkt des Speicherns fuer den Zeitraum des Workouts.
 * Der Wert wird mit der Aktivitaet abgelegt und nicht bei jeder Anzeige neu
 * geholt: er aendert sich nicht mehr, und so steht er auch ohne Health Connect
 * und ohne Uhr noch in der Historie.
 */
object HealthHeartRate {

    /** Nur lesen. CrewFit schreibt nichts in die Gesundheitsdaten. */
    val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(HeartRateRecord::class))

    /** Durchschnitt und Hoechstwert eines Zeitraums, in Schlaegen je Minute. */
    data class Beats(val average: Int, val max: Int)

    /**
     * Der Puls im angegebenen Zeitraum, oder null.
     *
     * Null heisst schlicht "dazu liegt nichts vor" - keine Uhr getragen, keine
     * Erlaubnis, Health Connect nicht vorhanden oder die Abfrage schlug fehl.
     * Fuer den Aufrufer ist das dasselbe: das Workout wird ohne Puls
     * gespeichert. Ein Fehler waere es nur, wenn deswegen gar nichts
     * gespeichert wuerde.
     */
    suspend fun forWindow(context: Context, start: Instant, end: Instant): Beats? =
        withContext(Dispatchers.IO) {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                return@withContext null
            }

            try {
                val client = HealthConnectClient.getOrCreate(context)
                if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
                    return@withContext null
                }

                // Health Connect rechnet selbst zusammen. Die Einzelmessungen
                // kaemen sonst zu Hunderten herein, und mehrere Quellen -
                // Uhr und Brustgurt - muessten von Hand entflochten werden.
                val result = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX),
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )

                val average = result[HeartRateRecord.BPM_AVG] ?: return@withContext null
                val max = result[HeartRateRecord.BPM_MAX] ?: average
                Beats(average.toInt(), max.toInt())
            } catch (e: Exception) {
                Log.e("HealthConnect", "Could not read the heart rate: ${e.message}")
                null
            }
        }
}

/**
 * Die Berechtigungen, um die CrewFit auf einmal bittet.
 *
 * Health Connect zeigt einen Dialog mit allen angefragten Datentypen. Zweimal
 * nacheinander zu fragen - erst Schritte, dann Puls - waere fuer den Nutzer
 * dieselbe Erlaubnis in zwei Schritten.
 */
object HealthAccess {

    val ALL: Set<String> = HealthSteps.PERMISSIONS + HealthHeartRate.PERMISSIONS

    /**
     * Ob ueberhaupt etwas erlaubt ist.
     *
     * Bewusst nicht "alles": Health Connect laesst den Nutzer einzelne
     * Datentypen abwaehlen. Wer nur die Schritte freigibt, hat die Anbindung
     * eingerichtet - die Anzeige soll ihn dann nicht auffordern, sie noch
     * einmal einzurichten.
     */
    suspend fun anyGranted(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return@withContext false
        }
        try {
            HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
                .any { it in ALL }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Could not read the granted permissions: ${e.message}")
            false
        }
    }
}
