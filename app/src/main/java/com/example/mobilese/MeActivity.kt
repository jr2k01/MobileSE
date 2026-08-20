package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Der persoenliche Bildschirm - alles, was zur Person gehoert und nicht zur
 * Crew.
 *
 * Er hat den Challenges-Reiter in der unteren Leiste abgeloest. Die laufenden
 * Challenges stehen jetzt auf dem Startbildschirm, wo man sie ohnehin
 * ansieht; angelegt werden sie im Crew-Bildschirm, wo sie hingehoeren. Damit
 * war ein Reiter frei fuer das, was vorher nirgends zusammenstand: Punkte,
 * Level, Serie, Trainings und Medaillen ueber alle Crews hinweg.
 *
 * Die Crew-Bildschirme zeigen immer nur einen Ausschnitt. Wer in zwei Crews
 * trainiert, sah seine Zahlen deshalb nie vollstaendig.
 */
class MeActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    private var summary: PersonalSummary? = null

    /** Die Crew, auf die die Liste gerade eingeschraenkt ist; null = alle. */
    private var filterCrew: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_me)
        setUpTopBar(R.string.nav_me)
        setUpPullToRefresh { load() }

        repository = AppRepository.get(this)

        val openFilter = View.OnClickListener { askForCrew() }
        findViewById<EditText>(R.id.etCrewFilter).setOnClickListener(openFilter)
        findViewById<TextInputLayout>(R.id.tilCrewFilter).setEndIconOnClickListener(openFilter)

        load()
    }

    /**
     * Beim Zurueckkommen neu laden: wer von hier aus ein Workout geoeffnet und
     * darunter etwas geaendert hat, soll die Zahlen nicht veraltet vorfinden.
     */
    override fun onResume() {
        super.onResume()
        if (summary != null) load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val loaded = repository.loadPersonalSummary() ?: return@launch
                summary = loaded

                // Eine Crew, auf die gefiltert wurde, kann man inzwischen
                // verlassen haben - dann zurueck auf alle, statt eine leere
                // Liste ohne Erklaerung zu zeigen.
                if (filterCrew != null && loaded.crews.none { it.id == filterCrew }) {
                    filterCrew = null
                }

                show(loaded)
            } finally {
                finishRefreshing()
            }
        }
    }

    private fun show(summary: PersonalSummary) {
        LevelCardView(findViewById(R.id.levelCard)).show(summary.totalPoints)
        showStreak(summary.streakDays)
        MedalGrid.fill(findViewById<GridLayout>(R.id.glMeMedals), Medals.statusOf(summary.medals))
        showActivities(summary)
    }

    private fun showStreak(days: Int) {
        findViewById<TextView>(R.id.tvMeStreakTitle).text =
            if (days <= 0) getString(R.string.streak_title_none)
            else resources.getQuantityString(R.plurals.streak_title, days, days)

        val toNext = Streak.daysToNextTier(days)
        findViewById<TextView>(R.id.tvMeStreakHint).text = when {
            days <= 0 -> getString(R.string.streak_hint_start)
            toNext == null -> getString(R.string.streak_hint_top)
            else -> resources.getQuantityString(
                R.plurals.streak_hint_next,
                toNext,
                toNext,
                decimal(Streak.multiplierFor(days + toNext))
            )
        }

        findViewById<TextView>(R.id.tvMeStreakMultiplier).text =
            getString(R.string.streak_multiplier, decimal(Streak.multiplierFor(days)))
    }

    /**
     * Die Trainings des gewaehlten Ausschnitts: drei Kennzahlen und die Liste.
     *
     * Die Liste ist auf [MAX_ROWS] begrenzt. Wer vierhundert Trainings hat,
     * baut sich sonst vierhundert Ansichten in eine ScrollView - der
     * Bildschirm ruckelt, und gelesen wird ohnehin nur der Anfang.
     */
    private fun showActivities(summary: PersonalSummary) {
        val shown = summary.activitiesIn(filterCrew)

        findViewById<EditText>(R.id.etCrewFilter).setText(
            filterCrew?.let { code -> summary.crews.firstOrNull { it.id == code }?.name }
                ?: getString(R.string.me_filter_all)
        )

        setStat(R.id.statMeWorkouts, shown.size.toString(), R.string.member_stat_workouts)
        setStat(
            R.id.statMeMinutes,
            NumberFormat.getIntegerInstance().format(shown.sumOf { it.duration }),
            R.string.me_stat_minutes
        )
        setStat(
            R.id.statMeDistance,
            // Ohne Distanz steht ein Strich statt "0", das sonst nach einer
            // gemessenen Null aussaehe.
            shown.sumOf { it.distance }.let {
                if (it <= 0.0) getString(R.string.value_none) else formatKm(it)
            },
            R.string.me_stat_distance
        )

        val container = findViewById<LinearLayout>(R.id.llMeActivities)
        container.removeAllViews()
        findViewById<View>(R.id.tvMeActivitiesEmpty).visibility =
            if (shown.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        shown.take(MAX_ROWS).forEach { activity ->
            container.addView(summaryRow(inflater, container, activity))
        }
    }

    /** Dieselbe Zeile wie in der Aktivitaetenliste, damit sie sich gleich liest. */
    private fun summaryRow(inflater: LayoutInflater, parent: LinearLayout, activity: Activity): View {
        val row = inflater.inflate(R.layout.item_workout_summary, parent, false)

        row.findViewById<ImageView>(R.id.ivSummarySport)
            .setImageResource(Sports.iconFor(activity.sport))
        row.findViewById<TextView>(R.id.tvSummarySport).text = activity.sport
        row.findViewById<TextView>(R.id.tvSummaryMeta).text =
            ActivityTime.toDisplay(activity.timestamp)
        row.findViewById<TextView>(R.id.tvSummaryDuration).text =
            getString(R.string.duration_unit, activity.duration.toString())

        showBadge(row, R.id.ivSummaryHasPhoto, !activity.photoUrl.isNullOrEmpty())
        showBadge(row, R.id.ivSummaryHasMap, activity.latitude != null && activity.longitude != null)
        showBadge(row, R.id.ivSummaryHasVoice, !activity.voiceUrl.isNullOrEmpty())

        row.setOnClickListener {
            startActivity(WorkoutDetailActivity.intent(this, activity, null))
        }
        return row
    }

    private fun showBadge(row: View, id: Int, present: Boolean) {
        row.findViewById<View>(id).visibility = if (present) View.VISIBLE else View.GONE
    }

    /**
     * Die Auswahl des Ausschnitts: alle Crews, oder eine bestimmte.
     *
     * Ein Auswahldialog und kein aufklappendes Menue - dasselbe Muster wie
     * beim Sport-Waehler, dessen Popup sich zwar oeffnete, aber nichts zeigte.
     */
    private fun askForCrew() {
        val current = summary ?: return

        val labels = (listOf(getString(R.string.me_filter_all)) + current.crews.map { it.name })
            .toTypedArray()
        val codes = listOf<String?>(null) + current.crews.map { it.id }
        val checked = codes.indexOf(filterCrew).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.me_filter_hint)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                filterCrew = codes[which]
                showActivities(current)
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun setStat(containerId: Int, value: String, labelRes: Int) {
        val card = findViewById<View>(containerId)
        card.findViewById<TextView>(R.id.tvStatValue).text = value
        card.findViewById<TextView>(R.id.tvStatLabel).setText(labelRes)
    }

    /**
     * Wie auf den anderen Bildschirmen: ueber NumberFormat und nicht ueber
     * String.format. Letzteres nimmt stillschweigend die Sprache des Geraets
     * und schreibt je nachdem "1.5" oder "1,5" - NumberFormat tut dasselbe,
     * sagt es aber, und beide Bildschirme zeigen dann dieselbe Schreibweise.
     */
    private fun decimal(value: Double): String =
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)

    private fun formatKm(km: Double): String = decimal(km)

    companion object {

        /** So viele Trainings stehen in der Liste; alles Weitere waere Ballast. */
        private const val MAX_ROWS = 20

        fun intent(context: Context): Intent = Intent(context, MeActivity::class.java)
    }
}
