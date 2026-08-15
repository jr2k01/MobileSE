package com.example.mobilese

import android.app.DatePickerDialog
import android.content.Context
import java.util.Calendar

/**
 * Oeffnet den Kalender zur Auswahl des Geburtsdatums.
 *
 * [DatePickerDialog] ist Teil des Android-SDK und bringt genau das mit, was
 * gebraucht wird: eine Monatsansicht zum Antippen des Tages und eine
 * Jahresliste, die sich ueber die Jahreszahl in der Kopfzeile oeffnet. Eine
 * eigene Auswahl zu bauen waere zusaetzlicher Code ohne Gewinn.
 *
 * Die Grenzen sind gesetzt, damit gar nicht erst Unsinn entstehen kann: kein
 * Datum in der Zukunft und keines vor 120 Jahren. Das ersetzt die frueheren
 * Tippfehler im freien Textfeld.
 */
object BirthDatePicker {

    fun show(context: Context, currentValue: String?, onPicked: (String) -> Unit) {
        val start = BirthDate.calendarFor(currentValue)

        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth -> onPicked(BirthDate.format(year, month, dayOfMonth)) },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.datePicker.minDate = BirthDate.earliestSelectableMillis()
        dialog.show()
    }
}
