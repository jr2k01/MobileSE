package com.example.mobilese

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val etName = findViewById<EditText>(R.id.etName)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)

        // Bestehende Daten laden
        etName.setText(sharedPref.getString("user_name", ""))
        etAge.setText(sharedPref.getString("user_age", ""))
        etHeight.setText(sharedPref.getString("user_height", ""))
        etWeight.setText(sharedPref.getString("user_weight", ""))

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val age = etAge.text.toString()
            val height = etHeight.text.toString()
            val weight = etWeight.text.toString()

            val editor = sharedPref.edit()
            editor.putString("user_name", name)
            editor.putString("user_age", age)
            editor.putString("user_height", height)
            editor.putString("user_weight", weight)
            editor.apply()

            Toast.makeText(this, "Profil gespeichert!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            finish()
        }
    }
}
