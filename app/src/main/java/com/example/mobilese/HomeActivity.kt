package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)
        val btnCrew = findViewById<ImageButton>(R.id.btnCrewOverviewIcon)

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
