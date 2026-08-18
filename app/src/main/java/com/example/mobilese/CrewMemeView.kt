package com.example.mobilese

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Zeigt das Bild, das die Nummer eins der Crew aufgehaengt hat.
 *
 * Steht getrennt von [LeaderboardActivity], die schon Rangliste, Auswertung und
 * Belohnungen traegt. Hier liegt nur die Anzeige; hochgeladen und geloescht wird
 * ueber die beiden Rueckrufe, weil dafuer Bildauswahl und Dialoge der Activity
 * gebraucht werden.
 *
 * Wer aendern darf, entscheidet allein [canEdit] - also der Bildschirm anhand
 * des ersten Platzes. In der Datenbank laesst sich das nicht durchsetzen, siehe
 * AppRepository.saveCrewMeme.
 */
class CrewMemeView(
    private val root: View,
    private val onChange: () -> Unit,
    private val onRemove: () -> Unit
) {

    private val title: TextView = root.findViewById(R.id.tvMemeTitle)
    private val image: ImageView = root.findViewById(R.id.ivCrewMeme)
    private val caption: TextView = root.findViewById(R.id.tvMemeCaption)
    private val empty: TextView = root.findViewById(R.id.tvMemeEmpty)
    private val change: MaterialButton = root.findViewById(R.id.btnMemeChange)
    private val remove: MaterialButton = root.findViewById(R.id.btnMemeRemove)

    init {
        change.setOnClickListener { onChange() }
        remove.setOnClickListener { onRemove() }
    }

    /**
     * @param meme das aufgehaengte Bild, oder null
     * @param ownerName Anzeigename dessen, der es aufgehaengt hat
     * @param canEdit ob der angemeldete Nutzer gerade fuehrt
     */
    fun show(meme: CrewMeme?, ownerName: String?, canEdit: Boolean) {
        change.visibility = if (canEdit) View.VISIBLE else View.GONE
        // Abnehmen kann nur, wer auch aufhaengen darf - und nur, wenn etwas
        // haengt.
        remove.visibility = if (canEdit && meme != null) View.VISIBLE else View.GONE

        if (meme == null) {
            image.visibility = View.GONE
            caption.visibility = View.GONE
            title.setText(R.string.meme_title_empty)
            empty.visibility = View.VISIBLE
            empty.setText(
                if (canEdit) R.string.meme_empty_leader else R.string.meme_empty_other
            )
            return
        }

        empty.visibility = View.GONE
        title.text = root.context.getString(
            R.string.meme_title,
            ownerName ?: root.context.getString(R.string.unknown_member)
        )

        image.visibility = View.VISIBLE
        ImageLoader.into(image, meme.imageUrl, placeholder = android.R.drawable.ic_menu_gallery)

        val text = meme.caption.orEmpty().trim()
        caption.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        caption.text = text
    }
}
