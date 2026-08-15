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
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Liste der Aktivitaeten, umschaltbar zwischen den eigenen und denen der
 * ganzen Crew.
 *
 * Erreichbar ueber den Knopf unter den letzten Aktivitaeten auf dem
 * Startbildschirm. In der Navigationsleiste gab es dafuer frueher ein eigenes
 * Symbol; im Zusammenhang mit der Liste, zu der er gehoert, ist der Weg
 * verstaendlicher.
 */
class WorkoutHistoryActivity : AppCompatActivity() {

    /** Welche Aktivitaeten gerade gezeigt werden. */
    private enum class Scope { MINE, CREW }

    private lateinit var repository: AppRepository
    private lateinit var container: LinearLayout

    private var scope = Scope.MINE
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_history)

        repository = AppRepository.get(this)
        container = findViewById(R.id.llActivitiesContainer)

        findViewById<ImageButton>(R.id.btnBackActivities).setOnClickListener { finish() }

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.tgActivityScope)
        toggle.check(R.id.btnScopeMine)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            scope = if (checkedId == R.id.btnScopeCrew) Scope.CREW else Scope.MINE
            load()
        }

        // Ohne Crew gibt es nichts umzuschalten.
        if (repository.getJoinedCrewCode() == null) {
            findViewById<View>(R.id.btnScopeCrew).isEnabled = false
        }

        load()
    }

    private fun load() {
        loadJob?.cancel()
        container.removeAllViews()

        loadJob = lifecycleScope.launch {
            when (scope) {
                Scope.MINE -> showActivities(
                    repository.getOwnActivities().map { it to null },
                    R.string.no_activities_yet
                )

                Scope.CREW -> {
                    val crewCode = repository.getJoinedCrewCode() ?: return@launch
                    val snapshot = repository.loadCrewSnapshot(crewCode)
                    val nameById = snapshot.members.associate { it.id to it.name.orEmpty() }

                    val entries = snapshot.activities
                        // Eintraege ehemaliger Mitglieder bleiben in der
                        // Datenbank stehen, gehoeren aber zu niemandem mehr.
                        .filter { it.userId in nameById }
                        .sortedByDescending { ActivityTime.sortKey(it.timestamp) }
                        .map { activity ->
                            val name = nameById[activity.userId]?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.unknown_member)
                            activity to name
                        }

                    showActivities(entries, R.string.no_crew_activities_yet)
                }
            }
        }
    }

    /**
     * @param entries Aktivitaet und, in der Crew-Ansicht, der Name der Person.
     */
    private fun showActivities(entries: List<Pair<Activity, String?>>, emptyTextRes: Int) {
        container.removeAllViews()

        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(emptyTextRes)
                textSize = 16f
                setPadding(0, 100, 0, 0)
                gravity = Gravity.CENTER
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        for ((activity, author) in entries) {
            val view = inflater.inflate(R.layout.item_workout_history_entry, container, false)

            val tvAuthor = view.findViewById<TextView>(R.id.tvActivityAuthor)
            if (author == null) {
                tvAuthor.visibility = View.GONE
            } else {
                tvAuthor.text = author
                tvAuthor.visibility = View.VISIBLE
            }

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
