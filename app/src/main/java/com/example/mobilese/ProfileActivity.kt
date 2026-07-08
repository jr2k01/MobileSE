package com.example.mobilese

import android.content.Context
import android.content.Intent
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
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val ivProfilePicture = findViewById<android.widget.ImageView>(R.id.ivProfilePicture)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)

        ivProfilePicture.setOnClickListener {
            Toast.makeText(this, "Profilbild-Auswahl wird in Kürze implementiert!", Toast.LENGTH_SHORT).show()
        }

        // Bestehende Daten laden
        // Name, Geburtsdatum und Email können entweder aus der Registrierung 
        // oder aus vorherigen Profil-Updates stammen.
        etName.setText(sharedPref.getString("user_name", ""))
        etBirthDate.setText(sharedPref.getString("user_birthdate", ""))
        etEmail.setText(sharedPref.getString("registered_email", ""))
        
        etAge.setText(sharedPref.getString("user_age", ""))
        etHeight.setText(sharedPref.getString("user_height", ""))
        etWeight.setText(sharedPref.getString("user_weight", ""))

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val birthDate = etBirthDate.text.toString()
            val email = etEmail.text.toString()
            val age = etAge.text.toString()
            val height = etHeight.text.toString()
            val weight = etWeight.text.toString()

            val editor = sharedPref.edit()
            editor.putString("user_name", name)
            editor.putString("user_birthdate", birthDate)
            editor.putString("registered_email", email) // Email hier auch aktualisierbar machen
            editor.putString("user_age", age)
            editor.putString("user_height", height)
            editor.putString("user_weight", weight)
            editor.apply()

            Toast.makeText(this, "Profil gespeichert!", Toast.LENGTH_SHORT).show()

            // Überprüfen, ob bereits eine Crew existiert
            val targetActivity = if (sharedPref.contains("joined_crew")) {
                HomeActivity::class.java
            } else {
                StartActivity::class.java
            }

            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish()
        }

        btnLogout.setOnClickListener {
            finish()
        }
    }
}
