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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class WorkoutHistoryActivity : AppCompatActivity() {

    private lateinit var backend: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_history)

        backend = AppRepository(this)
        val currentUser = backend.getCurrentUser() ?: run { finish(); return }
        
        val llContainer = findViewById<LinearLayout>(R.id.llActivitiesContainer)
        val btnBack = findViewById<ImageButton>(R.id.btnBackActivities)

        btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            displayActivities(llContainer, currentUser)
        }
    }

    private suspend fun displayActivities(container: LinearLayout, email: String) {
        container.removeAllViews()
        val activities = backend.getUserActivities(email)
        val inflater = LayoutInflater.from(this)

        if (activities.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "No activities recorded yet."
            emptyText.textSize = 16f
            emptyText.setPadding(0, 100, 0, 0)
            emptyText.gravity = android.view.Gravity.CENTER
            container.addView(emptyText)
            return
        }

        for (activity in activities) {
            val view = inflater.inflate(R.layout.item_workout_history_entry, container, false)
                
            val tvSport = view.findViewById<TextView>(R.id.tvActivitySport)
            val tvDate = view.findViewById<TextView>(R.id.tvActivityDate)
            val tvDuration = view.findViewById<TextView>(R.id.tvActivityDuration)
            val tvLocation = view.findViewById<TextView>(R.id.tvActivityLocation)
            val ivPhoto = view.findViewById<ImageView>(R.id.ivActivityPhoto)

            tvSport.text = activity.sport
            tvDate.text = activity.timestamp
                
            tvDuration.text = getString(R.string.duration_unit, activity.duration.toString())
            tvDuration.visibility = View.VISIBLE
                
            if (!activity.photoUrl.isNullOrEmpty()) {
                if (!activity.photoUrl.startsWith("http")) {
                    val imgFile = File(activity.photoUrl)
                    if (imgFile.exists()) {
                        ivPhoto.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
                        ivPhoto.visibility = View.VISIBLE
                    }
                }
            }

            if (!activity.location.isNullOrEmpty()) {
                tvLocation.text = activity.location
                tvLocation.visibility = View.VISIBLE
            } else {
                tvLocation.visibility = View.GONE
            }

            container.addView(view)
        }
    }
}
