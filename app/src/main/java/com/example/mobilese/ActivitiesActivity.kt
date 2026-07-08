package com.example.mobilese

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

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
            emptyText.setPadding(0, 100, 0, 0)
            emptyText.gravity = android.view.Gravity.CENTER
            container.addView(emptyText)
            return
        }

        for (activityData in activities) {
            val parts = activityData.split("|")
            // Format: sport|timestamp|photoPath|location
            if (parts.size >= 2) {
                val view = inflater.inflate(R.layout.item_activity, container, false)
                
                val tvSport = view.findViewById<TextView>(R.id.tvActivitySport)
                val tvDate = view.findViewById<TextView>(R.id.tvActivityDate)
                val tvLocation = view.findViewById<TextView>(R.id.tvActivityLocation)
                val ivPhoto = view.findViewById<ImageView>(R.id.ivActivityPhoto)

                tvSport.text = parts[0]
                tvDate.text = parts[1]
                
                // Foto laden, falls vorhanden
                if (parts.size > 2 && parts[2].isNotEmpty()) {
                    val imgFile = File(parts[2])
                    if (imgFile.exists()) {
                        ivPhoto.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
                        ivPhoto.visibility = View.VISIBLE
                    }
                }

                // Standort anzeigen, falls vorhanden
                if (parts.size > 3 && parts[3].isNotEmpty()) {
                    tvLocation.text = parts[3]
                    tvLocation.visibility = View.VISIBLE
                } else {
                    tvLocation.visibility = View.GONE
                }

                container.addView(view)
            }
        }
    }
}
