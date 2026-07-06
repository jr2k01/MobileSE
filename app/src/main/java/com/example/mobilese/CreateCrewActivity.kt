package com.example.mobilese

import android.content.Context
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
        val tvCode = findViewById<TextView>(R.id.tvCode)

        btnSave.setOnClickListener {
            val name = et.text.toString().trim()
            if (name.isNotEmpty()) {
                val code = name.uppercase() + "-" + (100..999).random()
                tvCode.text = "CODE: $code"
                tvCode.visibility = View.VISIBLE
                val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().putString("joined_crew", name).apply()
                Toast.makeText(this, "Crew erstellt!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
