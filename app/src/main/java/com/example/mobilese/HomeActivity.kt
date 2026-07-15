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
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        backend = AppBackend(this)
        updateUI()
        setupClock()

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

    private fun setupClock() {
        val tvTime = findViewById<TextView>(R.id.tvGermanTime)
        val sdf = SimpleDateFormat("HH:mm:ss 'DE'", Locale.GERMANY)
        sdf.timeZone = TimeZone.getTimeZone("Europe/Berlin")

        timeRunnable = object : Runnable {
            override fun run() {
                tvTime.text = sdf.format(Date())
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }

    private fun updateUI() {
        val tvCrewName = findViewById<TextView>(R.id.tvHomeCrewName)
        val llMembersContainer = findViewById<LinearLayout>(R.id.llMembersContainer)
        val llRankingContainer = findViewById<LinearLayout>(R.id.llRankingContainer)
        val llLatestActivitiesContainer = findViewById<LinearLayout>(R.id.llLatestActivitiesContainer)

        val joinedCrewCode = backend.getJoinedCrewCode()
        if (joinedCrewCode != null) {
            val crewName = backend.getCrewName(joinedCrewCode)
            tvCrewName.text = getString(R.string.your_crew_prefix, crewName)
            populateMembers(llMembersContainer, joinedCrewCode)
            populateTopRanking(llRankingContainer, joinedCrewCode)
            populateLatestActivities(llLatestActivitiesContainer, joinedCrewCode)
        } else {
            tvCrewName.text = getString(R.string.no_crew_joined)
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
            // NUR Aktivitäten für die AKTUELLE Crew zählen
            val points = backend.getPointsForCrew(email, crewCode)
            val name = backend.getUserName(email)
            val photoPath = backend.getUserData(email, "profile_image_path")
            HomeMemberScore(name, points, photoPath)
        }.sortedByDescending { it.points }.take(3)

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

    private fun populateLatestActivities(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val memberEmails = backend.getCrewMembers(crewCode)

        // Alle Aktivitäten aller Mitglieder sammeln (Gefiltert nach Crew)
        val allActivities = mutableListOf<LatestActivityData>() 
        for (email in memberEmails) {
            val userName = backend.getUserName(email)
            val userActivities = backend.getUserActivitiesForCrew(email, crewCode)
            for (activityData in userActivities) {
                val parts = activityData.split("|")
                if (parts.size >= 2) {
                    val sport = parts[0]
                    val time = parts[1]
                    val duration = if (parts.size >= 6) parts[5] else "0"
                    allActivities.add(LatestActivityData(userName, sport, time, duration))
                }
            }
        }

        // Sortieren nach Zeit (neueste zuerst) und Top 3 nehmen
        val latestThree = allActivities.sortedByDescending { it.timestamp }.take(3)

        for (act in latestThree) {
            val view = inflater.inflate(R.layout.item_latest_activity, container, false)
            view.findViewById<TextView>(R.id.tvLatestActivityUser).text = act.userName
            view.findViewById<TextView>(R.id.tvLatestActivityInfo).text = act.sport
            view.findViewById<TextView>(R.id.tvLatestActivityTime).text = act.timestamp
            view.findViewById<TextView>(R.id.tvLatestActivityDuration).text = getString(R.string.duration_unit, act.duration)
            container.addView(view)
        }
    }

    private data class LatestActivityData(val userName: String, val sport: String, val timestamp: String, val duration: String)

    private data class HomeMemberScore(val name: String, val points: Int, val photoPath: String)

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
