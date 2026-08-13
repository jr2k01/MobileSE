package com.example.mobilese

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainHubActivity : AppCompatActivity() {

    private lateinit var backend: AppRepository
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main_hub)

        backend = AppRepository(this)
        updateUI()
        setupClock()

        // Navigation
        findViewById<ImageButton>(R.id.btnAddActivityIcon).setOnClickListener {
            startActivity(Intent(this, WorkoutTrackingActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnActivitiesListIcon).setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnDashboardIcon).setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnProfileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnCrewOverviewIcon).setOnClickListener {
            startActivity(Intent(this, CrewDetailsActivity::class.java))
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
            lifecycleScope.launch {
                val crewName = backend.getCrewName(joinedCrewCode)
                tvCrewName.text = getString(R.string.your_crew_prefix, crewName)
                populateMembers(llMembersContainer, joinedCrewCode)
                populateTopRanking(llRankingContainer, joinedCrewCode)
                populateLatestActivities(llLatestActivitiesContainer, joinedCrewCode)
            }
        } else {
            tvCrewName.text = getString(R.string.no_crew_joined)
            llMembersContainer.removeAllViews()
            llRankingContainer.removeAllViews()
            llLatestActivitiesContainer.removeAllViews()
        }
    }

    private suspend fun populateMembers(container: LinearLayout, crewCode: String) {
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
        val view = inflater.inflate(R.layout.item_member_profile_mini, container, false)
        view.findViewById<TextView>(R.id.tvMemberName).text = name
        val iv = view.findViewById<ImageView>(R.id.ivMemberPhoto)
        if (!imagePath.isNullOrEmpty()) {
            if (imagePath.startsWith("http")) {
                iv.load(imagePath) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    transformations(CircleCropTransformation())
                }
            } else {
                val file = File(imagePath)
                if (file.exists()) {
                    iv.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                }
            }
        }
        container.addView(view)
    }

    private suspend fun populateTopRanking(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val memberEmails = backend.getCrewMembers(crewCode)

        val memberScores = memberEmails.map { email ->
            val points = backend.getPointsForCrew(email, crewCode)
            val name = backend.getUserName(email)
            val photoPath = backend.getUserData(email, "profile_image_path")
            HomeMemberScore(name, points, photoPath)
        }.sortedByDescending { it.points }.take(3)

        memberScores.forEachIndexed { index, score ->
            val view = inflater.inflate(R.layout.item_leaderboard_entry, container, false)
            view.findViewById<TextView>(R.id.tvRank).text = (index + 1).toString()
            view.findViewById<TextView>(R.id.tvLeaderboardName).text = score.name
            view.findViewById<TextView>(R.id.tvPoints).text = getString(R.string.points_unit, score.points)

            val iv = view.findViewById<ImageView>(R.id.ivLeaderboardPhoto)
            if (score.photoPath.isNotEmpty()) {
                if (score.photoPath.startsWith("http")) {
                    iv.load(score.photoPath) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    val file = File(score.photoPath)
                    if (file.exists()) {
                        iv.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }
            }
            container.addView(view)
        }
    }

    private suspend fun populateLatestActivities(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val memberEmails = backend.getCrewMembers(crewCode)

        val allActivities = mutableListOf<LatestActivityData>() 
        for (email in memberEmails) {
            val userName = backend.getUserName(email)
            val userActivities = backend.getUserActivitiesForCrew(email, crewCode)
            for (activity in userActivities) {
                allActivities.add(LatestActivityData(userName, activity.sport, activity.timestamp, activity.duration.toString(), activity.voiceUrl ?: ""))
            }
        }

        // Nach dem normalisierten ISO-Schluessel sortieren. Ein direkter
        // Vergleich der Anzeigetexte wuerde zuerst nach Tag und erst danach
        // nach Monat und Jahr sortieren.
        val latestThree = allActivities.sortedByDescending { ActivityTime.sortKey(it.timestamp) }.take(3)

        for (act in latestThree) {
            val view = inflater.inflate(R.layout.item_feed_entry, container, false)
            view.findViewById<TextView>(R.id.tvLatestActivityUser).text = act.userName
            view.findViewById<TextView>(R.id.tvLatestActivityInfo).text = act.sport
            view.findViewById<TextView>(R.id.tvLatestActivityTime).text = ActivityTime.toDisplay(act.timestamp)
            view.findViewById<TextView>(R.id.tvLatestActivityDuration).text = getString(R.string.duration_unit, act.duration)
            
            val btnPlay = view.findViewById<ImageButton>(R.id.btnPlayVoice)
            if (act.voicePath.isNotEmpty()) {
                btnPlay.visibility = android.view.View.VISIBLE
                btnPlay.setOnClickListener {
                    playVoiceNote(act.voicePath)
                }
            } else {
                btnPlay.visibility = android.view.View.GONE
            }

            container.addView(view)
        }
    }

    /**
     * Sprachnotizen liegen seit der Supabase-Migration als URL vor. prepare()
     * wuerde dafuer synchron im Main-Thread auf das Netz warten und einen ANR
     * riskieren, deshalb prepareAsync() mit Callback.
     */
    private fun playVoiceNote(path: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, what, extra ->
                android.util.Log.e("Audio", "Playback error $what/$extra")
                android.widget.Toast.makeText(
                    this@MainHubActivity,
                    "Playback failed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                true
            }
            try {
                setDataSource(path)
                prepareAsync()
            } catch (e: Exception) {
                android.util.Log.e("Audio", "Could not set data source: ${e.message}")
                android.widget.Toast.makeText(
                    this@MainHubActivity,
                    "Playback failed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private data class LatestActivityData(val userName: String, val sport: String, val timestamp: String, val duration: String, val voicePath: String)

    private data class HomeMemberScore(val name: String, val points: Int, val photoPath: String)

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
