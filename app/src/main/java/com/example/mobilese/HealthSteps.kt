package com.example.mobilese

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die heutige Schrittzahl aus Health Connect.
 *
 * Health Connect ist die Gesundheitsdatenbank von Android: andere Apps - die
 * Uhr, Google Fit, Samsung Health - schreiben ihre Messwerte dort hinein, und
 * wer die Erlaubnis hat, darf lesen. CrewFit zaehlt also nicht selbst mit,
 * sondern zeigt an, was auf dem Geraet ohnehin schon erfasst wird. Das ist
 * genauer als ein eigener Zaehler und kostet keinen Akku.
 *
 * Die Bibliothek ist an dieser einen Stelle gekapselt. Die Bildschirme sehen
 * nur [Reading] und wissen nichts von Health Connect - sollte die Anbindung
 * spaeter anders geloest werden, bleibt es bei dieser Datei.
 *
 * Zugegriffen wird ueber einen Systemdienst, nicht ueber eine offene
 * Schnittstelle; ohne androidx.health.connect ist da nicht heranzukommen.
 */
object HealthSteps {

    /** Nur lesen. CrewFit schreibt nichts in die Gesundheitsdaten. */
    val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** Was beim Abfragen herauskam. */
    sealed interface Reading {
        /** Health Connect gibt es auf diesem Geraet nicht. */
        data object Unavailable : Reading

        /** Vorhanden, aber der Nutzer hat den Zugriff noch nicht erlaubt. */
        data object NotAllowed : Reading

        /** Erlaubt und gelesen - moeglicherweise null Schritte. */
        data class Steps(val count: Long) : Reading

        /** Erlaubt, aber die Abfrage schlug fehl. */
        data object Failed : Reading
    }

    /**
     * Ob Health Connect auf diesem Geraet ueberhaupt zur Verfuegung steht.
     *
     * Ab Android 14 gehoert es zum System, davor ist es eine eigene App aus dem
     * Play Store. Fehlt es, darf die Abfrage nicht einmal versucht werden -
     * [HealthConnectClient.getOrCreate] wirft dann.
     */
    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Ob der Nutzer den Lesezugriff bereits erteilt hat. */
    suspend fun isAllowed(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext false
        try {
            HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
                .containsAll(PERMISSIONS)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Could not read the granted permissions: ${e.message}")
            false
        }
    }

    /**
     * Die Schritte seit Mitternacht Ortszeit.
     *
     * Summiert wird von Health Connect selbst statt hier: die Datenbank kann
     * mehrere Quellen enthalten - Handy und Uhr zaehlen denselben Schritt - und
     * rechnet Ueberschneidungen heraus. Wer die Einzelsaetze selbst
     * zusammenzaehlt, bekommt zu viele Schritte.
     */
    suspend fun today(context: Context): Reading = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext Reading.Unavailable

        try {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)) {
                return@withContext Reading.NotAllowed
            }

            val result: AggregationResult = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now())
                )
            )
            // Kein Wert heisst nicht "Fehler", sondern "heute noch nichts
            // erfasst" - etwa frueh am Morgen oder ohne getragene Uhr.
            Reading.Steps(result[StepsRecord.COUNT_TOTAL] ?: 0L)
        } catch (e: Exception) {
            Log.e("HealthConnect", "Could not read today's steps: ${e.message}")
            Reading.Failed
        }
    }

    /**
     * Mitternacht in der Zeitzone des Geraets.
     *
     * Bewusst nicht "vor 24 Stunden": gefragt ist, was der Nutzer heute
     * gelaufen ist, und ein Tag hat bei Zeitumstellung 23 oder 25 Stunden.
     */
    private fun startOfToday(): Instant =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
}
