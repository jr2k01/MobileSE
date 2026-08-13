package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val backend = AppRepository(this)

        // Falls bereits eingeloggt -> Direkt weiter
        val currentUser = backend.getCurrentUser()
        if (currentUser != null) {
            val target = if (backend.getJoinedCrew() != null) MainHubActivity::class.java else CrewLandingActivity::class.java
            startActivity(Intent(this, target))
            finish()
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            lifecycleScope.launch {
                if (backend.loginUser(email, password)) {
                    Toast.makeText(this@LoginActivity, getString(R.string.welcome_back) + "!", Toast.LENGTH_SHORT).show()
                    
                    val target = if (backend.getJoinedCrew() != null) MainHubActivity::class.java else CrewLandingActivity::class.java
                    startActivity(Intent(this@LoginActivity, target))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Email or password incorrect!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
    }
}
