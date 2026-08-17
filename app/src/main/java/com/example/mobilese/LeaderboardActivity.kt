package com.example.mobilese

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Die Rangliste der Crew.
 *
 * Die Challenges standen frueher auf demselben Bildschirm oberhalb der
 * Rangliste und schoben sie nach unten; sie haben jetzt einen eigenen, siehe
 * [CrewChallengesActivity]. Faellige Belohnungen werden hier trotzdem noch
 * ausgeschuettet - die Punkte sollen ankommen, egal welchen der beiden
 * Bildschirme jemand oeffnet.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var llLeaderboard: LinearLayout
    private lateinit var crewCode: String

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_leaderboard)

        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        llLeaderboard = findViewById(R.id.llLeaderboardContainer)
        setUpTopBar(R.string.crew_ranking)

        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val snapshot = repository.loadCrewSnapshot(crewCode)

            // Wurde etwas gutgeschrieben, ist der Snapshot veraltet und die
            // Rangliste wuerde ohne Neuladen die alten Punktstaende zeigen.
            val current =
                if (repository.awardCompletedChallenges(snapshot)) repository.loadCrewSnapshot(crewCode)
                else snapshot

            showLeaderboard(current)
        }
    }

    private fun showLeaderboard(snapshot: CrewSnapshot) {
        llLeaderboard.removeAllViews()
        val inflater = LayoutInflater.from(this)

        Scoreboard.build(snapshot).forEachIndexed { index, entry ->
            val view = inflater.inflate(R.layout.item_leaderboard_entry, llLeaderboard, false)
            view.findViewById<TextView>(R.id.tvRank).text = (index + 1).toString()
            view.findViewById<TextView>(R.id.tvLeaderboardName).text = entry.name
            view.findViewById<TextView>(R.id.tvPoints).text =
                getString(R.string.points_unit, entry.points)
            ImageLoader.into(
                view.findViewById<ImageView>(R.id.ivLeaderboardPhoto),
                entry.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
            showStepRing(view, entry.todaySteps)
            llLeaderboard.addView(view)
        }
    }

    /**
     * Der Ring zum heutigen Schrittziel in einer Ranglistenzeile.
     *
     * Der Ring steht auch bei null Schritten da, nur leer. Ihn wegzulassen
     * waere zweideutig: dann saehe ein Mitglied ohne Health Connect genauso aus
     * wie eines, das den Bildschirm gerade nicht geoeffnet hat - und die Zeilen
     * waeren unterschiedlich breit.
     */
    private fun showStepRing(row: View, steps: Int) {
        row.findViewById<CircularProgressIndicator>(R.id.piStepsGoal)
            .setProgressCompat(StepGoal.progressPercent(steps.toLong()), false)

        row.findViewById<ImageView>(R.id.ivStepsGoalReached).visibility =
            if (StepGoal.isReached(steps.toLong())) View.VISIBLE else View.GONE

        row.findViewById<View>(R.id.flStepsGoal).contentDescription =
            if (StepGoal.isReached(steps.toLong())) getString(R.string.steps_hint_goal_reached)
            else getString(R.string.steps_ring_desc, steps, StepGoal.DAILY_STEPS)
    }
}
