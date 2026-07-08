package com.example.mobilese

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etName = findViewById<EditText>(R.id.etRegName)
        val etBirthDate = findViewById<EditText>(R.id.etRegBirthDate)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnDoRegister)
        val btnBack = findViewById<Button>(R.id.btnBackToLogin)

        val backend = AppBackend(this)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (name.isEmpty() || birthDate.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Bitte alle Felder ausfüllen!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (backend.registerUser(email, password, name, birthDate)) {
                Toast.makeText(this, "Registrierung erfolgreich!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "E-Mail bereits vergeben!", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
