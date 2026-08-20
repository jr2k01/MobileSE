package com.example.mobilese

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Ein Bild aus der Galerie waehlen - mit der Erlaubnis davor.
 *
 * Beides gehoert zusammen und stand vorher an zwei Stellen getrennt: das
 * Profilbild im Profil, das Meme in der Rangliste, beide mit eigenem Aufrufer
 * und keiner von beiden mit einer Abfrage. Hier ist es einmal, und wer ein Bild
 * braucht, legt sich ein [GalleryPicker] an.
 *
 * Wichtig: Das Feld muss beim Anlegen der Activity gesetzt werden, nicht erst
 * in `onCreate` nach `setContentView`. Android verlangt, dass die Vertraege fuer
 * Ergebnisse feststehen, bevor die Activity sichtbar wird - sonst stuerzt sie
 * beim Wiederherstellen ab.
 *
 * @param onPicked Bekommt das gewaehlte Bild. Wird nicht gerufen, wenn die
 *        Auswahl abgebrochen oder die Erlaubnis verweigert wurde.
 */
class GalleryPicker(
    private val activity: AppCompatActivity,
    private val onPicked: (Uri) -> Unit
) {

    private val pick =
        activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let(onPicked)
        }

    /**
     * Nach der Antwort wird nicht auf das Ergebnis der Abfrage geschaut, sondern
     * erneut geprueft, ob die Erlaubnis steht: ab Android 14 kann jemand im
     * Dialog einzelne Bilder freigeben, und dann ist die angefragte Berechtigung
     * abgelehnt, die auf einzelne Bilder beschraenkte aber erteilt. Wer nur auf
     * das Ergebnis sieht, haelt diesen Fall faelschlich fuer eine Absage.
     */
    private val request =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AppPermission.GALLERY.isGranted(activity)) {
                pick.launch(MIME_IMAGES)
            } else {
                Toast.makeText(activity, R.string.gallery_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    /**
     * Oeffnet die Galerie, fragt beim ersten Mal vorher nach der Erlaubnis.
     *
     * Beim zweiten Mal wird nicht erneut gefragt: steht die Erlaubnis, geht es
     * direkt zur Auswahl.
     */
    fun open() {
        if (AppPermission.GALLERY.isGranted(activity)) {
            pick.launch(MIME_IMAGES)
        } else {
            request.launch(AppPermission.GALLERY.manifestNames)
        }
    }

    private companion object {
        const val MIME_IMAGES = "image/*"
    }
}
