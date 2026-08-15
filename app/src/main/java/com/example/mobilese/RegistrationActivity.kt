package com.example.mobilese

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private companion object {
        /** Supabase weist kuerzere Passwoerter ohnehin ab. */
        const val MIN_PASSWORD_LENGTH = 6
    }

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_registration)

        repository = AppRepository.get(this)

        val etName = findViewById<EditText>(R.id.etRegName)
        val etBirthDate = findViewById<EditText>(R.id.etRegBirthDate)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnDoRegister)
        val btnBack = findViewById<Button>(R.id.btnBackToLogin)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Was sich ohne Netz pruefen laesst, wird hier geprueft. Das spart
            // einen Serveraufruf und schont vor allem das Stundenlimit fuer
            // Bestaetigungsmails.
            if (name.isEmpty() || birthDate.isEmpty() || email.isEmpty() || password.isEmpty()) {
                toast(R.string.fill_all_fields)
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast(R.string.error_invalid_email)
                return@setOnClickListener
            }
            if (password.length < MIN_PASSWORD_LENGTH) {
                toast(R.string.error_weak_password)
                return@setOnClickListener
            }

            setBusy(true, btnRegister, btnBack)
            lifecycleScope.launch {
                val result = repository.registerUser(email, password, name, birthDate)
                setBusy(false, btnRegister, btnBack)

                when (result) {
                    // Der Regelfall in diesem Projekt: die Bestaetigung per
                    // Mail ist eingeschaltet.
                    RegistrationResult.ConfirmationRequired -> showConfirmationDialog(email)

                    // Nur wenn die Bestaetigung im Projekt abgeschaltet waere.
                    RegistrationResult.SignedIn -> {
                        toast(R.string.registration_success)
                        finish()
                    }

                    is RegistrationResult.Failed -> showError(result.error)
                }
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    /**
     * Bestaetigt dem Nutzer, dass das Konto angelegt wurde und die Mail
     * unterwegs ist. Frueher erschien an dieser Stelle "Registration failed",
     * obwohl die Registrierung erfolgreich war - der Rueckgabewert von Supabase
     * war falsch ausgewertet worden.
     */
    private fun showConfirmationDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_email_title)
            .setMessage(getString(R.string.confirm_email_message, email))
            .setCancelable(false)
            .setPositiveButton(R.string.got_it) { _, _ -> finish() }
            .setNeutralButton(R.string.resend_email) { _, _ -> resend(email) }
            .show()
    }

    private fun resend(email: String) {
        lifecycleScope.launch {
            val sent = repository.resendConfirmationEmail(email)
            toast(if (sent) R.string.resend_email_sent else R.string.resend_email_failed)
            if (sent) finish()
        }
    }

    private fun showError(error: AuthError) {
        AlertDialog.Builder(this)
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
