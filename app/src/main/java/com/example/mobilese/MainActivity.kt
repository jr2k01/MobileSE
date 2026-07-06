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

        // Login-Logik
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Bitte alle Felder ausfüllen!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            val registeredEmail = sharedPref.getString("registered_email", null)
            val registeredPassword = sharedPref.getString("registered_password", null)

            if (email == registeredEmail && password == registeredPassword) {
                Toast.makeText(this, "Login erfolgreich!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Falsche E-Mail oder Passwort!", Toast.LENGTH_SHORT).show()
            }
        }

        // Navigation zum Registrierungs-Screen
        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
