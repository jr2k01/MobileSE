package com.example.mobilese

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Die Challenges der Crew mit Fortschritt und Beitraegen der Mitglieder.
 *
 * Stand frueher oberhalb der Rangliste auf demselben Bildschirm. Dort war es
 * nur zu finden, wer die Rangliste oeffnete, und schob diese aus dem Blick.
 * Jetzt ein eigener Eintrag in der Navigationsleiste.
 */
class CrewChallengesActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var container: LinearLayout
    private lateinit var crewCode: String

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_crew_challenges)

        repository = AppRepository.get(this)
        crewCode = repository.getJoinedCrewCode() ?: run { finish(); return }

        container = findViewById(R.id.llChallengesContainer)

        setUpTopBar(R.string.crew_challenges_title)
        findViewById<Button>(R.id.btnLaunchChallenge).setOnClickListener { showAddChallengeDialog() }

        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val snapshot = repository.loadCrewSnapshot(crewCode)

            // Erst faellige Belohnungen schreiben, dann zeichnen - sonst zeigte
            // die Rangliste beim naechsten Oeffnen noch die alten Punktstaende.
            if (repository.awardCompletedChallenges(snapshot)) {
                showChallenges(repository.loadCrewSnapshot(crewCode))
            } else {
                showChallenges(snapshot)
            }
        }
    }

    private fun showChallenges(snapshot: CrewSnapshot) {
        container.removeAllViews()

        if (snapshot.challenges.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.no_challenges_yet)
                textSize = 16f
                setPadding(0, 100, 0, 0)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@CrewChallengesActivity, R.color.text_secondary))
            })
            return
        }

        val inflater = LayoutInflater.from(this)
        val accent = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent))

        for (challenge in snapshot.challenges) {
            val view = inflater.inflate(R.layout.item_challenge_entry, container, false)
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

            val progressBar = view.findViewById<LinearProgressIndicator>(R.id.pbChallenge)
            progressBar.max = challenge.goal.coerceAtLeast(1)
            progressBar.setProgressCompat(total.coerceAtMost(progressBar.max), true)

            if (total >= challenge.goal) {
                view.findViewById<TextView>(R.id.tvChallengeStatus).visibility = View.VISIBLE
                view.findViewById<MaterialCardView>(R.id.cvChallengeRoot).strokeColor = accent.defaultColor
                // LinearProgressIndicator faerbt sich ueber setIndicatorColor,
                // nicht ueber progressTintList wie die alte ProgressBar.
                progressBar.setIndicatorColor(accent.defaultColor)
            }

            container.addView(view)
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
                toast(R.string.challenge_deleted)
                load()
            } else {
                toast(R.string.challenge_delete_failed)
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
                val goal = InputRules.challengeGoalOrNull(etGoal.text.toString())

                if (goal == null) {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.error_challenge_goal_range,
                            InputRules.MIN_CHALLENGE_GOAL,
                            InputRules.MAX_CHALLENGE_GOAL
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                addChallenge(type, goal.toDouble())
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun addChallenge(type: ChallengeType, goal: Double) {
        lifecycleScope.launch {
            val reward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)
            if (repository.addCrewChallenge(crewCode, type.name, goal.toInt(), reward)) {
                toast(R.string.challenge_added)
                load()
            } else {
                toast(R.string.challenge_add_failed)
            }
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
