package com.example.crewfit // ACHTUNG: Hier muss euer eigener Package-Name stehen bleiben!

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Die UI-Elemente aus dem XML-Layout im Code verfügbar machen
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 2. Klick-Logik für den Login-Button
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            // Für heute zeigen wir nur eine Testnachricht an
            Toast.makeText(this, "Login versucht mit: $email", Toast.LENGTH_SHORT).show()
        }

        // 3. Klick-Logik für den Registrieren-Button
        btnRegister.setOnClickListener {
            Toast.makeText(this, "Registrieren gedrückt!", Toast.LENGTH_SHORT).show()
        }
    }
}