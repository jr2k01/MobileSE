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
    GALLERY(R.string.permission_gallery, R.string.permission_gallery_purpose);

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
        manifestNames.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

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
}
