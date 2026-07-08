package com.example.mobilese

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ActivitiesActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activities)

        backend = AppBackend(this)
        val currentUser = backend.getCurrentUser() ?: run { finish(); return }
        
        val llContainer = findViewById<LinearLayout>(R.id.llActivitiesContainer)
        val btnBack = findViewById<ImageButton>(R.id.btnBackActivities)

        btnBack.setOnClickListener { finish() }

        displayActivities(llContainer, currentUser)
    }

    private fun displayActivities(container: LinearLayout, email: String) {
        container.removeAllViews()
        val activities = backend.getUserActivities(email)
        val inflater = LayoutInflater.from(this)

        if (activities.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Noch keine Aktivitäten aufgezeichnet."
            emptyText.textSize = 16f
            emptyText.setPadding(0, 50, 0, 0)
            emptyText.gravity = android.view.Gravity.CENTER
            container.addView(emptyText)
            return
        }

        for (activityData in activities) {
            val parts = activityData.split("|")
            if (parts.size == 2) {
                val view = inflater.inflate(R.layout.item_activity, container, false)
                view.findViewById<TextView>(R.id.tvActivitySport).text = parts[0]
                view.findViewById<TextView>(R.id.tvActivityDate).text = parts[1]
                container.addView(view)
            }
        }
    }
}
