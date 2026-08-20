package com.example.mobilese

import android.view.View
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.NumberFormat

/**
 * Fuellt die Levelkarte [R.layout.part_level_card].
 *
 * Wie [CrewMemeView] eine Huelle um ein bereits eingebundenes Teillayout und
 * keine eigene View-Klasse: sie haelt nur die Kindelemente zusammen und setzt
 * Texte. Gebraucht wird sie im eigenen Profil und im Kurzprofil eines
 * Crew-Mitglieds - ohne sie stuende dieselbe Fuellerei zweimal da.
 */
class LevelCardView(private val root: View) {

    private val tvLevel = root.findViewById<TextView>(R.id.tvLevelValue)
    private val tvPrestige = root.findViewById<TextView>(R.id.tvPrestigeValue)
    private val tvPoints = root.findViewById<TextView>(R.id.tvTotalPointsValue)
    private val tvProgress = root.findViewById<TextView>(R.id.tvLevelProgress)
    private val bar = root.findViewById<LinearProgressIndicator>(R.id.piLevel)

    /**
     * Zeigt Level, Prestige und Gesamtpunktzahl.
     *
     * @param totalPoints Punkte ueber alle Crews. Aus ihnen ergibt sich alles
     *        Weitere - die Karte rechnet selbst, damit Aufrufer nicht zweimal
     *        dasselbe herleiten.
     */
    fun show(totalPoints: Int) {
        val progress = Levels.of(totalPoints)
        val context = root.context

        tvLevel.text = context.getString(R.string.level_value, progress.level)
        tvPoints.text = NumberFormat.getIntegerInstance().format(totalPoints)

        // Prestige 0 ist der erste Durchlauf und keine Auszeichnung - eine
        // Null neben dem Level sagte nur, dass noch nichts passiert ist.
        if (progress.prestige > 0) {
            tvPrestige.text = context.getString(R.string.level_prestige, progress.prestige)
            tvPrestige.visibility = View.VISIBLE
        } else {
            tvPrestige.visibility = View.GONE
        }

        bar.max = progress.pointsForNextLevel
        bar.progress = progress.pointsIntoLevel

        // Auf Level 100 fuehrt der naechste Aufstieg nicht zu Level 101,
        // sondern ins naechste Prestige. Der Text muss das sagen, sonst
        // verspricht er ein Level, das es nicht gibt.
        tvProgress.text = if (progress.level >= Levels.MAX_LEVEL) {
            context.getString(
                R.string.level_progress_max,
                progress.pointsIntoLevel,
                progress.pointsForNextLevel,
                progress.prestige + 1
            )
        } else {
            context.getString(
                R.string.level_progress,
                progress.pointsIntoLevel,
                progress.pointsForNextLevel,
                progress.level + 1
            )
        }

        // Der Balken traegt keine Beschriftung, die eine Sprachausgabe lesen
        // koennte - deshalb hier von Hand.
        bar.contentDescription =
            context.getString(R.string.level_desc, progress.level, progress.percent)
    }
}
