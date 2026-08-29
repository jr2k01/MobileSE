package com.example.mobilese

import android.content.Intent
import android.os.Bundle
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

        setUpTopBar(R.string.latest_activities_label)
        setUpPullToRefresh { load() }

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
            // finally: auch der Weg ueber return@launch - keine Crew - und ein
            // Abbruch beim erneuten Laden muessen den Kreis anhalten.
            try {
                when (scope) {
                    // Nur diese Crew: nebenan steht die Liste der ganzen Crew,
                    // und beide muessen denselben Ausschnitt meinen. Sonst
                    // faende man unter "meine" Trainings, die in der Liste
                    // daneben fehlen, und haelt das fuer einen Fehler.
                    Scope.MINE -> {
                        val crewCode = repository.getJoinedCrewCode() ?: return@launch
                        showActivities(
                            repository.getOwnActivities(crewCode).map { it to null },
                            R.string.no_activities_yet
                        )
                    }

                    Scope.CREW -> {
                        val crewCode = repository.getJoinedCrewCode() ?: return@launch
                        val snapshot = repository.loadCrewSnapshot(crewCode)
                        val nameById = snapshot.members.associate { it.id to DisplayName.of(it) }

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
            } finally {
                finishRefreshing()
            }
        }
    }

    /**
     * @param entries Aktivitaet und, in der Crew-Ansicht, der Name der Person.
     */
    private fun showActivities(entries: List<Pair<Activity, String?>>, emptyTextRes: Int) {
        container.removeAllViews()

        if (entries.isEmpty()) {
            // Der Knopf fuehrt in beiden Ausschnitten ins Formular: auch die
            // leere Crew-Liste fuellt man, indem man selbst etwas eintraegt.
            EmptyState.show(container, emptyTextRes, R.string.empty_action_log_workout) {
                startActivity(Intent(this, WorkoutTrackingActivity::class.java))
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        for ((activity, author) in entries) {
            container.addView(summaryRow(inflater, activity, author))
        }
    }

    /**
     * Ein Workout als Zeile.
     *
     * Bewusst knapp: Sportart, wer und wann, Dauer - und drei kleine Zeichen
     * dafuer, ob es Foto, Ort und Sprachnotiz gibt. Vorher stand hier je
     * Eintrag eine Karte mit grossem Foto und Landkarte; auf dem Bildschirm
     * waren damit knapp zwei Workouts zu sehen, und beim Suchen nach einem
     * bestimmten Training scrollte man endlos. Alles Weitere steht jetzt in
     * [WorkoutDetailActivity].
     */
    private fun summaryRow(inflater: LayoutInflater, activity: Activity, author: String?): View {
        val view = inflater.inflate(R.layout.item_workout_summary, container, false)

        view.findViewById<ImageView>(R.id.ivSummarySport)
            .setImageResource(Sports.iconFor(activity.sport))
        view.findViewById<TextView>(R.id.tvSummarySport).text = activity.sport
        view.findViewById<TextView>(R.id.tvSummaryDuration).text =
            getString(R.string.duration_unit, activity.duration.toString())

        val date = ActivityTime.toDisplay(activity.timestamp)
        view.findViewById<TextView>(R.id.tvSummaryMeta).text =
            if (author == null) date else getString(R.string.summary_meta, author, date)

        // Die Zeichen sagen, was in der Einzelansicht zu erwarten ist - so
        // muss niemand ein Workout oeffnen, um zu sehen, dass es kein Foto hat.
        showBadge(view, R.id.ivSummaryHasPhoto, !activity.photoUrl.isNullOrEmpty())
        showBadge(view, R.id.ivSummaryHasMap, activity.latitude != null && activity.longitude != null)
        showBadge(view, R.id.ivSummaryHasVoice, !activity.voiceUrl.isNullOrEmpty())
        // Nur das Zeichen: in dieser schmalen Zeile waere fuer eine Gruppe von
        // fuenf Namen kein Platz. Wer wissen will, mit wem, tippt die Zeile an.
        showBadge(view, R.id.ivSummaryTogether, JointWorkout.isJoint(activity))

        view.setOnClickListener {
            startActivity(WorkoutDetailActivity.intent(this, activity, author))
        }
        return view
    }

    private fun showBadge(row: View, id: Int, present: Boolean) {
        row.findViewById<View>(id).visibility = if (present) View.VISIBLE else View.GONE
    }

}
