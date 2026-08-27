package com.example.mobilese

import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

/**
 * Der Dialog, in dem eine Challenge oder ein Battle eingerichtet wird.
 *
 * Steht fuer sich, weil ihn zwei Bildschirme brauchen: die Challenges der
 * eigenen Crew und die Battles gegen andere. Als zweite Abschrift waeren die
 * beiden irgendwann auseinandergelaufen - und ein Battle, der eine andere
 * Obergrenze fuer das Ziel erlaubt als eine Challenge, waere schwer zu
 * erklaeren.
 */
object ChallengeSetup {

    /**
     * Zeigt den Dialog.
     *
     * @param opponent Die herausgeforderte Crew, oder null fuer eine Challenge
     *        innerhalb der eigenen. Bestimmt nur den Titel - was danach
     *        geschieht, entscheidet [onReady].
     * @param onReady Art, Ziel und Frist, sobald alles eingetragen ist.
     */
    fun show(
        activity: AppCompatActivity,
        opponent: Crew?,
        onReady: (ChallengeType, Double, String?) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_challenge_setup, null)
        val etType = view.findViewById<EditText>(R.id.etChallengeType)
        val tilGoal = view.findViewById<TextInputLayout>(R.id.tilChallengeGoal)
        val etGoal = view.findViewById<EditText>(R.id.etChallengeGoal)
        val etDeadline = view.findViewById<EditText>(R.id.etChallengeDeadline)

        var type = ChallengeType.DISTANCE
        // Die Beschriftung des Zielfeldes nennt die Einheit und wechselt mit
        // der Art - sonst stuende bei einer Schritt-Challenge "Ziel in
        // Kilometern".
        val applyType: (ChallengeType) -> Unit = { chosen ->
            type = chosen
            etType.setText(activity.getString(chosen.labelRes))
            tilGoal.hint = activity.getString(chosen.goalHintRes)
        }
        applyType(type)
        etType.setOnClickListener { askForType(activity, applyType) }

        // Das Feld haelt die Frist in Anzeigeform; gespeichert wird ISO.
        // Deshalb steht der gewaehlte Wert daneben und nicht im Text.
        var deadline: String? = null
        etDeadline.setOnClickListener {
            DeadlinePicker.show(activity, deadline) { picked ->
                deadline = picked
                etDeadline.setText(ChallengeDeadline.toDisplay(picked))
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(
                if (opponent == null) activity.getString(R.string.create_challenge_title)
                else activity.getString(R.string.battle_label, opponent.name)
            )
            .setView(view)
            .setPositiveButton(R.string.add_btn) { _, _ ->
                val goal = InputRules.challengeGoalOrNull(etGoal.text.toString(), type.maxGoal)
                if (goal == null) {
                    Toast.makeText(
                        activity,
                        activity.getString(
                            R.string.error_challenge_goal_range,
                            InputRules.MIN_CHALLENGE_GOAL,
                            type.maxGoal
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                onReady(type, goal.toDouble(), deadline)
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    /** Die Auswahl der Art, als Liste mit Symbolen wie bei der Sportart. */
    private fun askForType(activity: AppCompatActivity, onChosen: (ChallengeType) -> Unit) {
        val types = ChallengeType.entries
        val choices = types.map { ChoiceAdapter.Entry(activity.getString(it.labelRes), it.iconRes) }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.challenge_type_choose)
            .setAdapter(ChoiceAdapter(activity, choices)) { _, index -> onChosen(types[index]) }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }
}
