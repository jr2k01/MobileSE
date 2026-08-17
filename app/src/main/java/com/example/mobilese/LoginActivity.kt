package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                val result = repository.loginUser(email, password)
                setBusy(false, btnLogin, btnRegister)

                when (result) {
                    LoginResult.Success -> {
                        toast(R.string.welcome_back)
                        openApp()
                    }
                    // Eigener Weg, weil der Nutzer hier etwas tun kann:
                    // die Mail oeffnen oder sie sich erneut schicken lassen.
                    is LoginResult.Failed ->
                        if (result.error == AuthError.EMAIL_NOT_CONFIRMED) {
                            showNotConfirmedDialog(email)
                        } else {
                            showError(result.error)
                        }
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

    /**
     * Das Konto besteht, die Adresse ist aber noch nicht bestaetigt. Statt
     * einer Sackgasse bekommt der Nutzer hier den Weg nach vorne: nachsehen
     * oder die Mail erneut anfordern.
     */
    private fun showNotConfirmedDialog(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.not_confirmed_title)
            .setMessage(R.string.not_confirmed_message)
            .setPositiveButton(R.string.got_it, null)
            .setNeutralButton(R.string.resend_email) { _, _ ->
                lifecycleScope.launch {
                    val error = repository.resendConfirmationEmail(email)
                    // Den Grund nennen, nicht nur den Fehlschlag - siehe
                    // AppRepository.resendConfirmationEmail.
                    toast(error?.messageRes() ?: R.string.resend_email_sent)
                }
            }
            .show()
    }

    private fun showError(error: AuthError) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(error.messageRes())
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private fun setBusy(busy: Boolean, vararg buttons: View) {
        buttons.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
