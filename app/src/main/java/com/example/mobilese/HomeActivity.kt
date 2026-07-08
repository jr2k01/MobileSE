package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tvCrewName = findViewById<TextView>(R.id.tvHomeCrewName)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)
        val btnCrew = findViewById<ImageButton>(R.id.btnCrewOverviewIcon)

        // Crew-Name aus SharedPreferences laden
        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        val joinedCrew = sharedPref.getString("joined_crew", "Keine Crew")
        tvCrewName.text = "Deine Crew: $joinedCrew"

        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        btnCrew.setOnClickListener {
            val intent = Intent(this, CrewOverviewActivity::class.java)
            startActivity(intent)
        }
    }
}
