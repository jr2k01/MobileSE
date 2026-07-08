package com.example.mobilese

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        backend = AppBackend(this)
        updateUI()

        // Navigation
        findViewById<ImageButton>(R.id.btnAddActivityIcon).setOnClickListener {
            startActivity(Intent(this, AddActivityActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnActivitiesListIcon).setOnClickListener {
            startActivity(Intent(this, ActivitiesActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnDashboardIcon).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnProfileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnCrewOverviewIcon).setOnClickListener {
            startActivity(Intent(this, CrewOverviewActivity::class.java))
        }
    }

    private fun updateUI() {
        val tvCrewName = findViewById<TextView>(R.id.tvHomeCrewName)
        val llMembersContainer = findViewById<LinearLayout>(R.id.llMembersContainer)
        val llRankingContainer = findViewById<LinearLayout>(R.id.llRankingContainer)
        val llLatestActivitiesContainer = findViewById<LinearLayout>(R.id.llLatestActivitiesContainer)

        val joinedCrewCode = backend.getJoinedCrewCode()
        if (joinedCrewCode != null) {
            val crewName = backend.getCrewName(joinedCrewCode)
            tvCrewName.text = "Deine Crew: $crewName"
            populateMembers(llMembersContainer, joinedCrewCode)
            populateTopRanking(llRankingContainer, joinedCrewCode)
            populateLatestActivities(llLatestActivitiesContainer, joinedCrewCode)
        } else {
            tvCrewName.text = "Keiner Crew beigetreten"
            llMembersContainer.removeAllViews()
            llRankingContainer.removeAllViews()
            llLatestActivitiesContainer.removeAllViews()
        }
    }

    private fun populateMembers(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val members = backend.getCrewMembers(crewCode)

        for (memberEmail in members) {
            val name = backend.getUserName(memberEmail)
            val imagePath = backend.getUserData(memberEmail, "profile_image_path")
            addUserToContainer(container, inflater, name, imagePath)
        }
    }

    private fun addUserToContainer(container: LinearLayout, inflater: LayoutInflater, name: String, imagePath: String?) {
        val view = inflater.inflate(R.layout.item_crew_member, container, false)
        view.findViewById<TextView>(R.id.tvMemberName).text = name
        val iv = view.findViewById<ImageView>(R.id.ivMemberPhoto)
        if (!imagePath.isNullOrEmpty()) {
            val file = File(imagePath)
            if (file.exists()) {
                iv.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            }
        }
        container.addView(view)
    }

    private fun populateTopRanking(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val memberEmails = backend.getCrewMembers(crewCode)

        val memberScores = memberEmails.map { email ->
            val points = backend.getUserActivities(email).size
            val name = backend.getUserName(email)
            val photoPath = backend.getUserData(email, "profile_image_path")
            HomeMemberScore(name, points, photoPath)
        }.sortedByDescending { it.points }.take(3)

        memberScores.forEachIndexed { index, score ->
            val view = inflater.inflate(R.layout.item_leaderboard, container, false)
            view.findViewById<TextView>(R.id.tvRank).text = (index + 1).toString()
            view.findViewById<TextView>(R.id.tvLeaderboardName).text = score.name
            view.findViewById<TextView>(R.id.tvPoints).text = "${score.points} Pkt"

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

    private fun populateLatestActivities(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val memberEmails = backend.getCrewMembers(crewCode)

        // Alle Aktivitäten aller Mitglieder sammeln
        val allActivities = mutableListOf<Triple<String, String, String>>() // Name, Sport, Zeit
        for (email in memberEmails) {
            val userName = backend.getUserName(email)
            val userActivities = backend.getUserActivities(email)
            for (activityData in userActivities) {
                val parts = activityData.split("|")
                if (parts.size >= 2) {
                    allActivities.add(Triple(userName, parts[0], parts[1]))
                }
            }
        }

        // Sortieren nach Zeit (neueste zuerst) und Top 3 nehmen
        val latestThree = allActivities.sortedByDescending { it.third }.take(3)

        for (act in latestThree) {
            val view = inflater.inflate(R.layout.item_latest_activity, container, false)
            view.findViewById<TextView>(R.id.tvLatestActivityUser).text = act.first
            view.findViewById<TextView>(R.id.tvLatestActivityInfo).text = act.second
            view.findViewById<TextView>(R.id.tvLatestActivityTime).text = act.third
            container.addView(view)
        }
    }

    private data class HomeMemberScore(val name: String, val points: Int, val photoPath: String)

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
