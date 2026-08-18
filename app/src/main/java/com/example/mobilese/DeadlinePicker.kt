package com.example.mobilese

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.LocalDate

/**
 * Oeffnet den Kalender zur Wahl der Frist einer Challenge.
 *
 * Wie [BirthDatePicker] aufgebaut, nur mit umgekehrter Grenze: eine Frist liegt
 * in der Zukunft. Eine bereits abgelaufene liesse sich zwar speichern, waere
 * aber im selben Moment vorbei - das kann kein gewolltes Ergebnis sein.
 *
 * Ohne reattach: dieser Kalender steht ueber einem Dialog, und der uebersteht
 * das Drehen ohnehin nicht. Beim Geburtsdatum gehoert das Feld zur Activity,
 * deshalb braucht es dort den Rueckweg.
 */
object DeadlinePicker {

    private const val TAG = "crewfit_deadline"

    /** Wie weit im Voraus sich eine Frist setzen laesst. */
    private const val MAX_MONTHS_AHEAD = 12L

    fun show(activity: AppCompatActivity, currentValue: String?, onPicked: (String) -> Unit) {
        val manager = activity.supportFragmentManager

        // Zwei schnelle Antipper sollen nicht zwei Kalender uebereinanderlegen.
        if (manager.findFragmentByTag(TAG) != null) return

        val today = LocalDate.now()
        val selection = ChallengeDeadline.toUtcMillis(currentValue, today.plusWeeks(4))

        val constraints = CalendarConstraints.Builder()
            .setStart(ChallengeDeadline.toUtcMillis(null, today))
            .setEnd(ChallengeDeadline.toUtcMillis(null, today.plusMonths(MAX_MONTHS_AHEAD)))
            .setOpenAt(selection)
            .setValidator(DateValidatorPointForward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.challenge_deadline_title)
            .setSelection(selection)
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            onPicked(ChallengeDeadline.fromUtcMillis(millis))
        }
        picker.show(manager, TAG)
    }
}
