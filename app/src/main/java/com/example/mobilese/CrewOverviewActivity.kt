package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrewOverviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_overview)

        val tvCrewName = findViewById<TextView>(R.id.tvCrewNameDisplay)
        val btnLeave = findViewById<Button>(R.id.btnLeaveCrew)

        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        val crewName = sharedPref.getString("joined_crew", "Keine Crew")
        tvCrewName.text = crewName

        btnLeave.setOnClickListener {
            val editor = sharedPref.edit()
            editor.remove("joined_crew")
            editor.apply()
            
            Toast.makeText(this, "Crew verlassen", Toast.LENGTH_SHORT).show()
            
            // Zurück zum Startscreen
            val intent = Intent(this, StartActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
