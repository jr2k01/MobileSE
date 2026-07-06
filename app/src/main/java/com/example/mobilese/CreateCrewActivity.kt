package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CreateCrewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_crew)

        val et = findViewById<EditText>(R.id.etCrewName)
        val btnSave = findViewById<Button>(R.id.btnSaveCrew)

        btnSave.setOnClickListener {
            val name = et.text.toString().trim()
            if (name.isNotEmpty()) {
                val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().putString("joined_crew", name).apply()
                
                Toast.makeText(this, "Crew '$name' erstellt und beigetreten!", Toast.LENGTH_SHORT).show()
                
                // Zum Home Screen
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }
}
