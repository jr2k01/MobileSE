package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.NumberFormat

/**
 * Ein einzelnes Workout in voller Laenge.
 *
 * Die Liste der Aktivitaeten zeigt nur noch eine Kurzfassung je Eintrag; alles
 * Uebrige steht hier: Foto, Kartenausschnitt, Sprachnotiz, Dauer, Distanz, Ort,
 * Zeitpunkt und - in der Crew-Ansicht - von wem.
 *
 * Das Workout wird als Ganzes mitgegeben statt nur seine Kennung. Die Liste hat
 * die Daten bereits geladen; sie hier erneut zu holen waere eine zweite Abfrage
 * fuer etwas, das schon vorliegt - und aeltere Zeilen haben nicht einmal eine
 * Kennung, mit der sich nachladen liesse.
 */
class WorkoutDetailActivity : AppCompatActivity() {

    private val voicePlayer = VoicePlayer { toast(R.string.playback_failed) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_detail)
        setUpTopBar(R.string.workout_detail_title)

        val activity = readActivity()
        if (activity == null) {
            toast(R.string.workout_not_found)
            finish()
            return
        }

        show(activity, intent.getStringExtra(EXTRA_AUTHOR))
    }

    override fun onStop() {
        super.onStop()
        voicePlayer.release()
    }

    private fun readActivity(): Activity? {
        val raw = intent.getStringExtra(EXTRA_ACTIVITY) ?: return null
        return try {
            Json.decodeFromString(Activity.serializer(), raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun show(activity: Activity, author: String?) {
        findViewById<ImageView>(R.id.ivDetailSport)
            .setImageResource(Sports.iconFor(activity.sport))
        findViewById<TextView>(R.id.tvDetailSport).text = activity.sport
        findViewById<TextView>(R.id.tvDetailDate).text =
            ActivityTime.toDisplay(activity.timestamp)

        // In der eigenen Liste waere "von mir" bei jedem Eintrag ueberfluessig.
        findViewById<TextView>(R.id.tvDetailAuthor).apply {
            if (author == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.workout_by, author)
                visibility = View.VISIBLE
            }
        }

        showStats(activity)
        showPhoto(activity.photoUrl)
        showLocation(activity)
        showVoice(activity.voiceUrl)
    }

    private fun showStats(activity: Activity) {
        setStat(
            R.id.statDuration,
            getString(R.string.duration_unit, activity.duration.toString()),
            R.string.detail_stat_duration
        )
        // Ohne Distanz - Yoga, Kraftraum - steht dort ein Strich statt "0 km",
        // was nach einer gemessenen Null aussaehe.
        setStat(
            R.id.statDistance,
            if (activity.distance > 0) {
                getString(R.string.detail_distance_km, formatKm(activity.distance))
            } else {
                getString(R.string.value_none)
            },
            R.string.detail_stat_distance
        )
        setStat(
            R.id.statPoints,
            PointsCalculator.calculateWorkoutPoints(
                activity.duration,
                WorkoutIntensity.fromName(activity.intensity)
            ).toString(),
            R.string.detail_stat_points
        )
    }

    private fun setStat(containerId: Int, value: String, labelRes: Int) {
        val card = findViewById<View>(containerId)
        card.findViewById<TextView>(R.id.tvStatValue).text = value
        card.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
    }

    private fun showPhoto(photoUrl: String?) {
        val card = findViewById<View>(R.id.cvDetailPhoto)
        if (photoUrl.isNullOrEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        ImageLoader.into(
            findViewById(R.id.ivDetailPhoto),
            photoUrl,
            placeholder = android.R.drawable.ic_menu_gallery
        )
    }

    private fun showLocation(activity: Activity) {
        val row = findViewById<View>(R.id.llDetailLocation)
        if (activity.location.isNullOrEmpty()) {
            row.visibility = View.GONE
        } else {
            row.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailLocation).text = activity.location
        }

        val latitude = activity.latitude
        val longitude = activity.longitude
        if (latitude == null || longitude == null) return

        lifecycleScope.launch {
            val map = StaticMap.preview(
                this@WorkoutDetailActivity,
                latitude,
                longitude,
                ContextCompat.getColor(this@WorkoutDetailActivity, R.color.primary)
            ) ?: return@launch

            findViewById<ImageView>(R.id.ivDetailMap).setImageBitmap(map)
            findViewById<View>(R.id.cvDetailMap).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvDetailMapAttribution).apply {
                text = StaticMap.ATTRIBUTION
                visibility = View.VISIBLE
            }
        }
    }

    private fun showVoice(voiceUrl: String?) {
        val button = findViewById<MaterialButton>(R.id.btnDetailVoice)
        if (voiceUrl.isNullOrEmpty()) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.setOnClickListener { voicePlayer.play(voiceUrl) }
    }

    /** Nachkommastellen nur, wenn es welche gibt: "5 km" statt "5,0 km". */
    private fun formatKm(distance: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(distance)

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_AUTHOR = "author"

        /** Einziger Weg hierher, damit die Schluessel der Extras nur hier stehen. */
        fun intent(context: Context, activity: Activity, author: String?): Intent =
            Intent(context, WorkoutDetailActivity::class.java)
                // Serializer ausdruecklich benannt: mit der abgekuerzten Form
                // greift die Ueberladung mit Strategie-Parameter.
                .putExtra(EXTRA_ACTIVITY, Json.encodeToString(Activity.serializer(), activity))
                .putExtra(EXTRA_AUTHOR, author)
    }
}
