package com.example.mobilese

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker

/**
 * Oeffnet den Kalender zur Auswahl des Geburtsdatums.
 *
 * Verwendet wird [MaterialDatePicker] aus der Material-Bibliothek, die im
 * Projekt ohnehin fuer die uebrige Oberflaeche eingebunden ist - es kommt also
 * keine Abhaengigkeit hinzu. Er holt sich seine Farben aus dem App-Theme und
 * passt sich von selbst an Handy und Tablet an.
 *
 * Davor lief hier der [android.app.DatePickerDialog] aus dem SDK, dem ueber
 * android:datePickerDialogTheme ein ThemeOverlay fuer MaterialAlertDialog
 * untergeschoben wurde. Das ist fuer den System-Dialog das falsche Theme; der
 * darin gesetzte durchsichtige Fensterhintergrund liess den Kalender auf echten
 * Geraeten unsichtbar und unbedienbar werden. Der Material-Kalender bringt seine
 * eigene Darstellung mit und kann so nicht mehr leer erscheinen.
 *
 * Die Grenzen sind gesetzt, damit gar nicht erst Unsinn entstehen kann: kein
 * Datum in der Zukunft und keines vor 120 Jahren.
 */
object BirthDatePicker {

    private const val TAG = "crewfit_birth_date"

    fun show(activity: AppCompatActivity, currentValue: String?, onPicked: (String) -> Unit) {
        val manager = activity.supportFragmentManager

        // Zwei schnelle Antipper sollen nicht zwei Kalender uebereinanderlegen.
        if (manager.findFragmentByTag(TAG) != null) return

        val selection = BirthDate.toUtcMillis(currentValue)
        val constraints = CalendarConstraints.Builder()
            .setStart(BirthDate.earliestSelectableUtcMillis())
            .setEnd(BirthDate.latestSelectableUtcMillis())
            .setOpenAt(selection)
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.birth_date_title)
            .setSelection(selection)
            .setCalendarConstraints(constraints)
            .build()

        picker.listenForResult(onPicked)
        picker.show(manager, TAG)
    }

    /**
     * Haengt den Rueckruf nach einer Bildschirmdrehung wieder an.
     *
     * Der Kalender ist ein Fragment und uebersteht das Drehen, seine Zuhoerer
     * aber nicht - die gehoeren zur alten Activity. Ohne diesen Aufruf bliebe
     * ein gedrehter Kalender bedienbar, das gewaehlte Datum landete aber in
     * keinem Feld mehr.
     */
    fun reattach(activity: AppCompatActivity, onPicked: (String) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val restored = activity.supportFragmentManager.findFragmentByTag(TAG)
                as? MaterialDatePicker<Long> ?: return
        restored.listenForResult(onPicked)
    }

    private fun MaterialDatePicker<Long>.listenForResult(onPicked: (String) -> Unit) {
        addOnPositiveButtonClickListener { millis -> onPicked(BirthDate.fromUtcMillis(millis)) }
    }
}
