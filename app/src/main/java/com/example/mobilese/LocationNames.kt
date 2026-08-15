package com.example.mobilese

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wandelt Koordinaten in lesbare Ortsangaben um.
 *
 * [Geocoder] gehoert zum Android-Framework, es kommt also keine Bibliothek
 * hinzu. Gespeichert wird kuenftig der Name des Ortes statt "Lat: 52.1548,
 * Lon: 9.9580" - eine Zahlenreihe sagt niemandem im Feed, wo trainiert wurde.
 *
 * Die synchrone Abfrage ist seit API 33 als veraltet markiert, weil sie auf
 * das Netz wartet. Ab dort gibt es eine Variante mit Rueckruf, die hier
 * verwendet und in eine Coroutine verpackt wird; darunter bleibt der alte Weg,
 * ausgelagert auf [Dispatchers.IO].
 */
object LocationNames {

    private const val MAX_RESULTS = 5

    /** Ob auf diesem Geraet ueberhaupt ein Geocoder-Dienst vorhanden ist. */
    fun isAvailable(): Boolean = Geocoder.isPresent()

    /**
     * Vorschlaege fuer eine Position, beste zuerst. Leere Liste, wenn nichts
     * gefunden wurde oder kein Dienst erreichbar ist.
     */
    suspend fun suggestionsFor(context: Context, latitude: Double, longitude: Double): List<String> {
        if (!isAvailable()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())

        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchWithListener(geocoder, latitude, longitude)
            } else {
                fetchBlocking(geocoder, latitude, longitude)
            }
        } catch (e: Exception) {
            Log.e("LocationNames", "Geocoding failed: ${e.message}")
            emptyList()
        }

        return addresses.mapNotNull { describe(it) }.distinct()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun fetchWithListener(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(latitude, longitude, MAX_RESULTS,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    Log.e("LocationNames", "Geocoding failed: $errorMessage")
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            })
    }

    @Suppress("DEPRECATION")
    private suspend fun fetchBlocking(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address> = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(latitude, longitude, MAX_RESULTS).orEmpty()
    }

    /**
     * Baut aus einer Adresse eine Zeile fuer die Auswahl.
     *
     * getAddressLine(0) ist die vom System bereits passend zusammengesetzte
     * Fassung. Traegt die Adresse zusaetzlich einen Namen - bei Sporthallen
     * oder Studios steht der oft in featureName - wird er vorangestellt, damit
     * aus "Kaiserstrasse 12" ein "McFit, Kaiserstrasse 12" wird.
     */
    private fun describe(address: Address): String? {
        val line = address.getAddressLine(0)?.trim()
        val name = address.featureName?.trim()

        return when {
            line.isNullOrEmpty() -> name?.takeIf { it.isNotEmpty() }
            // featureName ist oft nur die Hausnummer - dann bringt er nichts.
            name.isNullOrEmpty() || name.all { it.isDigit() } || line.startsWith(name) -> line
            else -> "$name, $line"
        }
    }

    /** Rueckfallanzeige, wenn keine Adresse ermittelt werden konnte. */
    fun coordinatesLabel(latitude: Double, longitude: Double): String =
        String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", latitude, longitude)
}
