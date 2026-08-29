package com.example.mobilese

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Der Hinweis an einer Stelle, an der noch nichts steht.
 *
 * Wer die App zum ersten Mal oeffnet, sieht ueberall leere Flaechen: keine
 * Workouts, keine Challenges, keine Crew-Aktivitaeten. Eine leere Flaeche
 * erklaert sich nicht - sie sieht aus wie ein Fehler. Jede dieser Stellen sagt
 * jetzt, **was** dort erscheinen wird, und wo es sinnvoll ist auch, wie man
 * dorthin kommt.
 *
 * Wird in den Behaelter gelegt, den der Bildschirm ohnehin leert und neu
 * fuellt. Damit gibt es keinen zweiten Zustand, der sich verstellen kann: ist
 * die Liste leer, steht der Hinweis darin, und beim naechsten Fuellen ist er
 * mit einem `removeAllViews()` wieder weg.
 */
object EmptyState {

    /**
     * @param container die Liste selbst, vorher geleert.
     * @param textRes was hier erscheinen wird.
     * @param actionRes Beschriftung des Knopfes, oder null. Ohne Knopf ueberall
     *        dort, wo der Bildschirm schon einen traegt - zwei Wege zum selben
     *        Ziel nebeneinander helfen niemandem.
     */
    fun show(
        container: ViewGroup,
        textRes: Int,
        actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.view_empty_state, container, false)

        view.findViewById<TextView>(R.id.tvEmptyText).setText(textRes)

        if (actionRes != null && onAction != null) {
            view.findViewById<MaterialButton>(R.id.btnEmptyAction).apply {
                visibility = View.VISIBLE
                setText(actionRes)
                setOnClickListener { onAction() }
            }
        }

        container.addView(view)
    }
}
