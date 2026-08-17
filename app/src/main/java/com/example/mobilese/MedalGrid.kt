package com.example.mobilese

import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Fuellt ein Medaillenraster - im eigenen Profil wie im Profil eines
 * Crewmitglieds.
 *
 * Beide Bildschirme zeigen dieselben Medaillen in derselben Form, deshalb steht
 * das Fuellen hier einmal statt zweimal fast gleich.
 *
 * Verwendet [GridLayout] aus dem SDK: es bricht selbsttaetig um und braucht
 * weder RecyclerView noch eine zusaetzliche Bibliothek. Bei sieben festen
 * Eintraegen waere ein Recycler ohnehin Aufwand ohne Gewinn.
 */
object MedalGrid {

    fun fill(grid: GridLayout, status: Map<Medal, Boolean>) {
        grid.removeAllViews()
        val inflater = LayoutInflater.from(grid.context)

        status.forEach { (medal, earned) ->
            val view = inflater.inflate(R.layout.item_medal, grid, false)

            view.findViewById<ImageView>(R.id.ivMedal).apply {
                setImageResource(medal.iconRes)
                contentDescription = context.getString(medal.titleRes)
            }
            view.findViewById<TextView>(R.id.tvMedalName).setText(medal.titleRes)

            // Nicht verdiente Medaillen bleiben sichtbar, aber deutlich
            // zurueckgenommen - sie sind ein Ziel, keine Auszeichnung.
            view.alpha = if (earned) 1f else UNEARNED_ALPHA

            // Der Name unter dem Bild muss kurz sein; die Bedingung steht
            // deshalb im Antippen, nicht in der Kachel.
            view.setOnClickListener { showDetails(it, medal, earned) }

            grid.addView(view)
        }
    }

    private fun showDetails(view: View, medal: Medal, earned: Boolean) {
        MaterialAlertDialogBuilder(view.context)
            .setTitle(medal.titleRes)
            .setIcon(medal.iconRes)
            .setMessage(medal.descriptionRes)
            .setPositiveButton(
                if (earned) R.string.medal_earned else R.string.got_it,
                null
            )
            .show()
    }

    private const val UNEARNED_ALPHA = 0.28f
}
