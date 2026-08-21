package com.example.mobilese

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * Die Laufzeitberechtigungen, die CrewFit ueberhaupt anfragt.
 *
 * Vorher stand jede Pruefung dort, wo sie gebraucht wurde: die Kamera im
 * Workout-Bildschirm, das Mikrofon daneben, der Standort wieder eine Methode
 * weiter. Solange nur ein Bildschirm fragt, geht das - sobald die Einstellungen
 * denselben Stand anzeigen sollen, gaebe es die Abfrage zweimal in leicht
 * verschiedener Fassung. Hier steht sie einmal.
 *
 * Bewusst kein Zugriff auf Android-Klassen ausser [Context]: welche Namen zu
 * welcher Android-Fassung gehoeren, ist reine Zuordnung und laesst sich so
 * ohne Geraet betrachten.
 */
enum class AppPermission(
    @StringRes val labelRes: Int,
    @StringRes val purposeRes: Int
) {

    CAMERA(R.string.permission_camera, R.string.permission_camera_purpose),

    MICROPHONE(R.string.permission_microphone, R.string.permission_microphone_purpose),

    LOCATION(R.string.permission_location, R.string.permission_location_purpose),

    /**
     * Die Galerie.
     *
     * Der Systemname dafuer hat sich zweimal geaendert: bis Android 12 hiess er
     * READ_EXTERNAL_STORAGE, ab Android 13 READ_MEDIA_IMAGES, und ab Android 14
     * kommt READ_MEDIA_VISUAL_USER_SELECTED dazu - damit waehlt jemand im
     * Systemdialog einzelne Bilder aus, statt die ganze Galerie freizugeben.
     * Diese Teilfreigabe zaehlt hier als erteilt: die App bekommt genau das,
     * was sie braucht, und darf deshalb nicht weiter nachfragen.
     */
    GALLERY(R.string.permission_gallery, R.string.permission_gallery_purpose),

    /**
     * Geraete in der Naehe, fuer das gemeinsame Training.
     *
     * Der Bruch liegt bei Android 12: davor gab es nur BLUETOOTH und
     * BLUETOOTH_ADMIN, die das System ohne Nachfrage erteilt - dafuer verlangte
     * es fuer jeden Bluetooth-Suchlauf den **Standort**, weil sich aus
     * sichtbaren Geraeten der Aufenthaltsort ableiten laesst. Ab Android 12
     * gibt es eigene Rechte zum Suchen und Senden, und der Standort entfaellt.
     *
     * Deshalb steht hier auf alten Geraeten der Standort und auf neuen die
     * beiden Bluetooth-Rechte - dieselbe Funktion, zwei Wege dorthin.
     */
    NEARBY(R.string.permission_nearby, R.string.permission_nearby_purpose);

    /**
     * Die Systemnamen, die zu dieser Berechtigung auf *diesem* Geraet gehoeren.
     *
     * Mehrere, wo Android mehrere kennt: beim Standort die genaue und die grobe
     * Angabe, bei der Galerie die volle und die auf einzelne Bilder beschraenkte.
     */
    val manifestNames: Array<String>
        get() = when (this) {
            CAMERA -> arrayOf(Manifest.permission.CAMERA)
            MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
            LOCATION -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            GALLERY -> galleryNames()
            NEARBY -> nearbyNames()
        }

    /**
     * Ob die Berechtigung steht.
     *
     * `any` und nicht `all`: beim Standort genuegt die grobe Angabe, um den Ort
     * eines Workouts zu benennen, und bei der Galerie genuegt die Freigabe
     * einzelner Bilder. Auf `all` zu bestehen hiesse, jemanden erneut zu fragen,
     * der bereits zugestimmt hat.
     */
    fun isGranted(context: Context): Boolean =
        // Ausnahme von der Regel: Suchen und Senden sind zwei getrennte
        // Rechte, und mit nur einem davon findet man entweder niemanden oder
        // wird selbst nicht gefunden. Hier muessen beide stehen.
        if (this == NEARBY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manifestNames.all { granted(context, it) }
        } else {
            manifestNames.any { granted(context, it) }
        }

    private fun granted(context: Context, name: String): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    private fun galleryNames(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )
        // Bis Android 12 gab es keinen eigenen Namen fuer Bilder.
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun nearbyNames(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Vor Android 12 verlangte das System fuer jeden Bluetooth-
            // Suchlauf den Standort. BLUETOOTH und BLUETOOTH_ADMIN stehen im
            // Manifest und werden ohne Nachfrage erteilt; erfragt werden muss
            // nur der Standort.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
