package com.example.mobilese

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class DashboardActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend
    private lateinit var llLeaderboard: LinearLayout
    private lateinit var llChallenges: LinearLayout
    private lateinit var crewCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        backend = AppBackend(this)
        crewCode = backend.getJoinedCrewCode() ?: run { finish(); return }

        val btnBack = findViewById<ImageButton>(R.id.btnBackDashboard)
        llLeaderboard = findViewById<LinearLayout>(R.id.llLeaderboardContainer)
        llChallenges = findViewById<LinearLayout>(R.id.llChallengesContainer)
        val btnAddChallenge = findViewById<Button>(R.id.btnLaunchChallenge)

        btnBack.setOnClickListener { finish() }
        btnAddChallenge.setOnClickListener { showAddChallengeDialog() }

        loadData()
    }

    private fun loadData() {
        loadLeaderboard(llLeaderboard, crewCode)
        loadChallenges()
    }

    private fun showAddChallengeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_challenge, null)
        val rgType = dialogView.findViewById<RadioGroup>(R.id.rgChallengeType)
        val etGoal = dialogView.findViewById<EditText>(R.id.etChallengeGoal)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_challenge_title)
            .setView(dialogView)
            .setPositiveButton(R.string.add_btn) { _, _ ->
                val selectedId = rgType.checkedRadioButtonId
                val type = if (selectedId == R.id.rbRunning) ChallengeType.DISTANCE else ChallengeType.WORKOUT_COUNT
                val goal = etGoal.text.toString().toDoubleOrNull() ?: 0.0
                
                if (goal > 0) {
                    val totalReward = ChallengeCalculator.calculateTotalChallengePoints(type, goal)
                    backend.addCrewChallenge(crewCode, type.name, goal.toInt(), totalReward)
                    loadChallenges()
                    Toast.makeText(this, "Challenge added!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun loadChallenges() {
        llChallenges.removeAllViews()
        val challenges = backend.getCrewChallenges(crewCode)
        val inflater = LayoutInflater.from(this)
        val members = backend.getCrewMembers(crewCode).toList()

        for (challengeData in challenges) {
            val parts = challengeData.split("|")
            if (parts.size >= 2) {
                val typeStr = parts[0]
                val goal = parts[1].toIntOrNull() ?: 0
                val challengeId = if (parts.size >= 3) parts[2] else ""
                val reward = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
                
                val view = inflater.inflate(R.layout.item_challenge, llChallenges, false)
                val tvTitle = view.findViewById<TextView>(R.id.tvChallengeTitle)
                val tvProgress = view.findViewById<TextView>(R.id.tvChallengeProgress)
                val pb = view.findViewById<ProgressBar>(R.id.pbChallenge)
                val tvStatus = view.findViewById<TextView>(R.id.tvChallengeStatus)
                val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteChallenge)
                val card = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cvChallengeRoot)
                val llContributions = view.findViewById<LinearLayout>(R.id.llContributionsContainer)

                btnDelete.setOnClickListener {
                    backend.deleteCrewChallenge(crewCode, challengeId)
                    loadChallenges()
                    Toast.makeText(this, "Challenge deleted", Toast.LENGTH_SHORT).show()
                }

                var totalProgress = 0
                val contributions = mutableListOf<Pair<String, Int>>()

                for (m in members) {
                    val name = backend.getUserName(m)
                    var memberContribution = 0
                    val acts = backend.getUserActivitiesForCrew(m, crewCode)
                    
                    if (typeStr == ChallengeType.DISTANCE.name || typeStr == "Running") {
                        for (a in acts) {
                            val aParts = a.split("|")
                            if (aParts[0] == "Running" && aParts.size >= 8) {
                                memberContribution += aParts[7].replace(",", ".").toDoubleOrNull()?.toInt() ?: 0
                            }
                        }
                    } else {
                        memberContribution = acts.count { it.startsWith("Gym|") }
                    }
                    
                    contributions.add(name to memberContribution)
                    totalProgress += memberContribution
                }

                if (typeStr == ChallengeType.DISTANCE.name || typeStr == "Running") {
                    tvTitle.text = getString(R.string.challenge_type_running)
                    tvProgress.text = "$totalProgress / $goal km"
                } else {
                    tvTitle.text = getString(R.string.challenge_type_gym)
                    tvProgress.text = "$totalProgress / $goal sessions"
                }

                // Display individual contributions
                llContributions.removeAllViews()
                contributions.sortedByDescending { it.second }.forEach { (name, value) ->
                    if (value > 0) {
                        val contribView = inflater.inflate(R.layout.item_challenge_contribution, llContributions, false)
                        contribView.findViewById<TextView>(R.id.tvContributorName).text = name
                        contribView.findViewById<TextView>(R.id.tvContributorValue).text = if (typeStr == ChallengeType.DISTANCE.name || typeStr == "Running") "$value km" else "$value sessions"
                        llContributions.addView(contribView)
                    }
                }

                pb.max = goal
                pb.progress = totalProgress

                if (totalProgress >= goal) {
                    tvStatus.visibility = android.view.View.VISIBLE
                    card.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent)))
                    pb.setProgressTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent)))
                    
                    // NEW: Distribute points using the TeamChallenge model
                    val challengeModel = TeamChallenge(challengeId, tvTitle.text.toString(), true, reward, members)
                    ChallengeManager.distributeChallengePoints(challengeModel, crewCode, backend)
                    // Reload leaderboard in case points were distributed
                    loadLeaderboard(llLeaderboard, crewCode)
                }

                llChallenges.addView(view)
            }
        }
    }

    private fun loadLeaderboard(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val memberEmails = backend.getCrewMembers(crewCode)
        val inflater = LayoutInflater.from(this)

        // Daten sammeln: Email -> Punkte (NUR Aktivitäten für diese Crew)
        val memberScores = memberEmails.map { email ->
            val points = backend.getPointsForCrew(email, crewCode)
            val name = backend.getUserName(email)
            val photoPath = backend.getUserData(email, "profile_image_path")
            MemberScore(email, name, points, photoPath)
        }.sortedByDescending { it.points }

        // Liste befüllen
        memberScores.forEachIndexed { index, score ->
            val view = inflater.inflate(R.layout.item_leaderboard, container, false)
            
            view.findViewById<TextView>(R.id.tvRank).text = (index + 1).toString()
            view.findViewById<TextView>(R.id.tvLeaderboardName).text = score.name
            view.findViewById<TextView>(R.id.tvPoints).text = getString(R.string.points_unit, score.points)

            val iv = view.findViewById<ImageView>(R.id.ivLeaderboardPhoto)
            if (score.photoPath.isNotEmpty()) {
                val file = File(score.photoPath)
                if (file.exists()) {
                    iv.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                }
            }

            container.addView(view)
        }
    }

    private data class MemberScore(
        val email: String,
        val name: String,
        val points: Int,
        val photoPath: String
    )
}

