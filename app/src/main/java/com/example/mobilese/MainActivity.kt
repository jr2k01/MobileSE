package com.example.mobilese

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

        val backend = AppBackend(this)

        // Falls bereits eingeloggt -> Direkt weiter
        val currentUser = backend.getCurrentUser()
        if (currentUser != null) {
            val target = if (backend.getJoinedCrew() != null) HomeActivity::class.java else StartActivity::class.java
            startActivity(Intent(this, target))
            finish()
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (backend.loginUser(email, password)) {
                backend.setCurrentUser(email)
                Toast.makeText(this, "Willkommen zurück!", Toast.LENGTH_SHORT).show()
                
                val target = if (backend.getJoinedCrew() != null) HomeActivity::class.java else StartActivity::class.java
                startActivity(Intent(this, target))
                finish()
            } else {
                Toast.makeText(this, "E-Mail oder Passwort falsch!", Toast.LENGTH_SHORT).show()
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
