package com.example.mobilese

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class WorkoutHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_history)

        val repository = AppRepository.get(this)
        val container = findViewById<LinearLayout>(R.id.llActivitiesContainer)

        findViewById<ImageButton>(R.id.btnBackActivities).setOnClickListener { finish() }

        lifecycleScope.launch {
            // Bereits nach Zeitstempel sortiert, neueste zuerst.
            showActivities(container, repository.getOwnActivities())
        }
    }

    private fun showActivities(container: LinearLayout, activities: List<Activity>) {
        container.removeAllViews()

        if (activities.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.no_activities_yet)
                textSize = 16f
                setPadding(0, 100, 0, 0)
                gravity = Gravity.CENTER
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        for (activity in activities) {
            val view = inflater.inflate(R.layout.item_workout_history_entry, container, false)

            view.findViewById<TextView>(R.id.tvActivitySport).text = activity.sport
            view.findViewById<TextView>(R.id.tvActivityDate).text =
                ActivityTime.toDisplay(activity.timestamp)

            view.findViewById<TextView>(R.id.tvActivityDuration).apply {
                text = getString(R.string.duration_unit, activity.duration.toString())
                visibility = View.VISIBLE
            }

            // Fotos liegen seit der Supabase-Migration als oeffentliche URL vor.
            // Aeltere Eintraege koennen noch einen lokalen Dateipfad enthalten;
            // der ImageLoader behandelt beide Faelle.
            val ivPhoto = view.findViewById<ImageView>(R.id.ivActivityPhoto)
            val photoUrl = activity.photoUrl
            if (photoUrl.isNullOrEmpty()) {
                ivPhoto.visibility = View.GONE
            } else {
                ivPhoto.visibility = View.VISIBLE
                ImageLoader.into(ivPhoto, photoUrl, placeholder = android.R.drawable.ic_menu_gallery)
            }

            val tvLocation = view.findViewById<TextView>(R.id.tvActivityLocation)
            if (activity.location.isNullOrEmpty()) {
                tvLocation.visibility = View.GONE
            } else {
                tvLocation.text = activity.location
                tvLocation.visibility = View.VISIBLE
            }

            showMap(view, activity)

            container.addView(view)
        }
    }

    /**
     * Kartenausschnitt zu einer Aktivitaet, sofern Koordinaten gespeichert
     * wurden. Aeltere Eintraege haben keine und zeigen weiterhin nur den
     * Ortsnamen.
     *
     * Die Views werden hier je Eintrag frisch erzeugt und nicht wie in einer
     * RecyclerView wiederverwendet - eine Verwechslung durch spaet
     * eintreffende Bilder ist deshalb ausgeschlossen.
     */
    private fun showMap(view: View, activity: Activity) {
        val ivMap = view.findViewById<ImageView>(R.id.ivActivityMap)
        val tvAttribution = view.findViewById<TextView>(R.id.tvActivityMapAttribution)

        val latitude = activity.latitude
        val longitude = activity.longitude
        if (latitude == null || longitude == null) {
            ivMap.visibility = View.GONE
            tvAttribution.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val map = StaticMap.preview(
                this@WorkoutHistoryActivity,
                latitude,
                longitude,
                ContextCompat.getColor(this@WorkoutHistoryActivity, R.color.primary)
            ) ?: return@launch

            ivMap.setImageBitmap(map)
            ivMap.visibility = View.VISIBLE
            tvAttribution.text = StaticMap.ATTRIBUTION
            tvAttribution.visibility = View.VISIBLE
        }
    }
}
