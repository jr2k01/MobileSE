package com.example.mobilese

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Rangliste der Crew und die laufenden Challenges.
 *
 * Beide Bereiche werden aus demselben [CrewSnapshot] gerechnet. Vorher wurde
 * pro Challenge und Mitglied nachgefragt, und die Rangliste wurde innerhalb der
 * Challenge-Schleife jedes Mal komplett neu geladen - bei drei abgeschlossenen
 * Challenges also dreimal. Jetzt wird sie einmal gezeichnet und nur dann neu
 * geladen, wenn tatsaechlich Punkte gutgeschrieben wurden.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var llLeaderboard: LinearLayout
    private lateinit var llChallenges: LinearLayout
    private lateinit var crewCode: String

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_leaderboard)

        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        llLeaderboard = findViewById(R.id.llLeaderboardContainer)
        llChallenges = findViewById(R.id.llChallengesContainer)

        findViewById<ImageButton>(R.id.btnBackDashboard).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnLaunchChallenge).setOnClickListener { showAddChallengeDialog() }

        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val snapshot = repository.loadCrewSnapshot(crewCode)

            // Erst alle faelligen Belohnungen schreiben, dann zeichnen. Wurde
            // etwas gutgeschrieben, ist der Snapshot veraltet und die Rangliste
            // wuerde ohne Neuladen die alten Punktstaende zeigen.
            val current =
                if (awardCompletedChallenges(snapshot)) repository.loadCrewSnapshot(crewCode)
                else snapshot

            showLeaderboard(current)
            showChallenges(current)
        }
    }

    /** @return true, wenn Punkte gutgeschrieben wurden. */
    private suspend fun awardCompletedChallenges(snapshot: CrewSnapshot): Boolean {
        val memberIds = snapshot.members.map { it.id }
        var awarded = false

        for (challenge in snapshot.challenges) {
            val total = ChallengeManager.progressByMember(challenge, snapshot).sumOf { it.second }
            val award = ChallengeManager.pendingAward(challenge, total, memberIds, snapshot)
            if (award != null && repository.awardChallenge(award)) {
                awarded = true
            }
        }
        return awarded
    }

    // --- Rangliste ---

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
            llLeaderboard.addView(view)
        }
    }

    // --- Challenges ---

    private fun showChallenges(snapshot: CrewSnapshot) {
        llChallenges.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val accent = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))

        for (challenge in snapshot.challenges) {
            val view = inflater.inflate(R.layout.item_challenge_entry, llChallenges, false)
            val isDistance = ChallengeCalculator.isDistanceChallenge(challenge.type)
            val contributions = ChallengeManager.progressByMember(challenge, snapshot)
            val total = contributions.sumOf { it.second }

            view.findViewById<TextView>(R.id.tvChallengeTitle).text = getString(
                if (isDistance) R.string.challenge_type_running else R.string.challenge_type_gym
            )
            view.findViewById<TextView>(R.id.tvChallengeProgress).text = getString(
                if (isDistance) R.string.progress_km else R.string.progress_sessions,
                total,
                challenge.goal
            )

            view.findViewById<ImageButton>(R.id.btnDeleteChallenge).setOnClickListener {
                deleteChallenge(challenge.id)
            }

            showContributions(
                view.findViewById(R.id.llContributionsContainer),
                inflater,
                contributions,
                isDistance
            )

            val progressBar = view.findViewById<ProgressBar>(R.id.pbChallenge)
            progressBar.max = challenge.goal.coerceAtLeast(1)
            progressBar.progress = total.coerceAtMost(progressBar.max)

            if (total >= challenge.goal) {
                view.findViewById<TextView>(R.id.tvChallengeStatus).visibility = View.VISIBLE
                view.findViewById<MaterialCardView>(R.id.cvChallengeRoot).strokeColor = accent.defaultColor
                progressBar.progressTintList = accent
            }

            llChallenges.addView(view)
        }
    }

    private fun showContributions(
        container: LinearLayout,
        inflater: LayoutInflater,
        contributions: List<Pair<UserProfile, Int>>,
        isDistance: Boolean
    ) {
        container.removeAllViews()
        for ((member, value) in contributions) {
            if (value <= 0) continue
            val row = inflater.inflate(R.layout.item_challenge_contributor_row, container, false)
            row.findViewById<TextView>(R.id.tvContributorName).text =
                member.name?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_member)
            row.findViewById<TextView>(R.id.tvContributorValue).text = getString(
                if (isDistance) R.string.contribution_km else R.string.contribution_sessions,
                value
            )
            container.addView(row)
        }
    }

    private fun deleteChallenge(challengeId: String) {
        lifecycleScope.launch {
            if (repository.deleteCrewChallenge(challengeId)) {
                Toast.makeText(this@LeaderboardActivity, R.string.challenge_deleted, Toast.LENGTH_SHORT).show()
                load()
            } else {
                Toast.makeText(this@LeaderboardActivity, R.string.challenge_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddChallengeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_challenge_setup, null)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgChallengeType)
        val etGoal = dialogView.findViewById<EditText>(R.id.etChallengeGoal)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_challenge_title)
            .setView(dialogView)
            .setPositiveButton(R.string.add_btn) { _, _ ->
                val type =
                    if (rgType.checkedRadioButtonId == R.id.rbRunning) ChallengeType.DISTANCE
                    else ChallengeType.WORKOUT_COUNT
                val goal = etGoal.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0

                if (goal <= 0) {
                    Toast.makeText(this, R.string.challenge_goal_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                addChallenge(type, goal)
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun addChallenge(type: ChallengeType, goal: Double) {
        lifecycleScope.launch {
            val reward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)
            if (repository.addCrewChallenge(crewCode, type.name, goal.toInt(), reward)) {
                Toast.makeText(this@LeaderboardActivity, R.string.challenge_added, Toast.LENGTH_SHORT).show()
                load()
            } else {
                Toast.makeText(this@LeaderboardActivity, R.string.challenge_add_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
