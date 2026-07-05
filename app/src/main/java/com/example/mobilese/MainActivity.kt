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

        // 1. Die UI-Elemente aus dem XML-Layout im Code verfügbar machen
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 2. Klick-Logik für den Login-Button
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 1. Sicherheitscheck: Sind die Felder leer?
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Bitte alle Felder ausfüllen!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Zugriff auf den lokalen Speicher (SharedPreferences) holen
            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)

            // Die gespeicherten Daten abrufen (falls nichts existiert, wird "null" zurückgegeben)
            val registeredEmail = sharedPref.getString("registered_email", null)
            val registeredPassword = sharedPref.getString("registered_password", null)

            // 3. Abgleich: Stimmen die eingegebenen Daten mit den registrierten übereinstimmen?
            if (email == registeredEmail && password == registeredPassword) {
                Toast.makeText(this, "Login erfolgreich!", Toast.LENGTH_SHORT).show()

                // 4. Der Seitenwechsel (Intent) zur ProfileActivity
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)

                // finish() sorgt dafür, dass die Login-Seite geschlossen wird.
                // Wenn man auf dem Profil-Screen "Zurück" drückt, fliegt man nicht wieder in den Login.
                finish()
            } else {
                // Fehler anzeigen, falls die Daten nicht übereinstimmen oder noch kein Konto existiert
                Toast.makeText(this, "Falsche E-Mail oder Passwort!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Klick-Logik für den Registrieren-Button
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 1. Sicherheitscheck: Sind die Felder leer?
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Bitte E-Mail und Passwort für die Registrierung eingeben!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Zugriff auf den lokalen Speicher (SharedPreferences) holen
            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()

            // 3. Daten speichern
            editor.putString("registered_email", email)
            editor.putString("registered_password", password)
            editor.apply() // .apply() speichert im Hintergrund

            Toast.makeText(this, "Registrierung erfolgreich!", Toast.LENGTH_SHORT).show()
        }
    }
}
