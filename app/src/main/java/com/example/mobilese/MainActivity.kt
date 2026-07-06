package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Bitte alles ausfüllen!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            val regEmail = sharedPref.getString("registered_email", null)
            val regPass = sharedPref.getString("registered_password", null)

            if (email == regEmail && password == regPass) {
                // Falls bereits in einer Crew -> Home, sonst -> Start
                val targetActivity = if (sharedPref.contains("joined_crew")) {
                    HomeActivity::class.java
                } else {
                    StartActivity::class.java
                }
                
                startActivity(Intent(this, targetActivity))
                finish()
            } else {
                Toast.makeText(this, "Fehler beim Login!", Toast.LENGTH_SHORT).show()
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
