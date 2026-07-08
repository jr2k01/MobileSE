package com.example.mobilese

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class DashboardActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        backend = AppBackend(this)
        val crewCode = backend.getJoinedCrewCode() ?: run { finish(); return }

        val btnBack = findViewById<ImageButton>(R.id.btnBackDashboard)
        val llContainer = findViewById<LinearLayout>(R.id.llLeaderboardContainer)

        btnBack.setOnClickListener { finish() }

        loadLeaderboard(llContainer, crewCode)
    }

    private fun loadLeaderboard(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val memberEmails = backend.getCrewMembers(crewCode)
        val inflater = LayoutInflater.from(this)

        // Daten sammeln: Email -> Punkte (Anzahl Aktivitäten)
        val memberScores = memberEmails.map { email ->
            val points = backend.getUserActivities(email).size
            val name = backend.getUserName(email)
            val photoPath = backend.getUserData(email, "profile_image_path")
            MemberScore(email, name, points, photoPath)
        }.sortedByDescending { it.points }

        // Liste befüllen
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

    private data class MemberScore(
        val email: String,
        val name: String,
        val points: Int,
        val photoPath: String
    )
}
