package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_login)

        repository = AppRepository.get(this)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                toast(R.string.fill_all_fields)
                return@setOnClickListener
            }

            // Waehrend der Anmeldung sperren, sonst startet ein zweiter Tipp
            // eine parallele Anfrage.
            setBusy(true, btnLogin, btnRegister)
            lifecycleScope.launch {
                val success = repository.loginUser(email, password)
                setBusy(false, btnLogin, btnRegister)
                if (success) {
                    toast(R.string.welcome_back)
                    openApp()
                } else {
                    toast(R.string.login_failed)
                }
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        /**
         * Frueher entschied allein ein Eintrag in den SharedPreferences, ob der
         * Nutzer angemeldet ist. Das Supabase-Token kann aber abgelaufen sein -
         * dann landete man im Hauptmenue, wo anschliessend jeder Schreibzugriff
         * still fehlschlug. hasValidSession() wartet die Wiederherstellung der
         * Sitzung ab und prueft sie wirklich.
         *
         * Die Pruefung steht in onResume, damit sie auch greift, wenn der
         * Nutzer von der Registrierung zurueckkommt und bereits angemeldet ist.
         */
        lifecycleScope.launch {
            if (repository.hasValidSession()) openApp()
        }
    }

    private fun openApp() {
        // Schuetzt davor, den naechsten Bildschirm zweimal zu starten, falls
        // zwei Pruefungen kurz hintereinander zurueckkommen.
        if (navigated) return
        navigated = true

        val target =
            if (repository.getJoinedCrewCode() != null) MainHubActivity::class.java
            else CrewLandingActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }

    private fun setBusy(busy: Boolean, vararg buttons: View) {
        buttons.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
