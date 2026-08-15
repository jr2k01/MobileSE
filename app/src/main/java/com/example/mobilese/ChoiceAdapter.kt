package com.example.mobilese

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes

/**
 * Eine Auswahl mit Symbol und Beschriftung, wie sie [android.app.AlertDialog]
 * ueber setAdapter annimmt.
 *
 * Die eingebaute Liste eines Dialogs zeigt nur Text. Sechs Sportarten
 * untereinander sind so schwer zu unterscheiden; mit einem Symbol davor findet
 * man die gesuchte auf einen Blick.
 */
class ChoiceAdapter(
    context: Context,
    private val entries: List<Entry>
) : ArrayAdapter<ChoiceAdapter.Entry>(context, 0, entries) {

    data class Entry(val label: String, @DrawableRes val iconRes: Int)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dialog_choice, parent, false)

        val entry = entries[position]
        view.findViewById<TextView>(R.id.tvChoiceLabel).text = entry.label
        view.findViewById<ImageView>(R.id.ivChoiceIcon).setImageResource(entry.iconRes)

        return view
    }
}
