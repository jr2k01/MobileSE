package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Neues Passwort vergeben, nachdem der Link aus der Mail die App geoeffnet hat.
 *
 * Der Weg: auf dem Anmeldebildschirm "Passwort vergessen" antippen, Mail
 * anfordern, den Link darin oeffnen. Der Link zeigt auf crewfit://reset-password
 * und landet ueber den intent-filter hier. Supabase legt daraus eine Sitzung an,
 * die genau fuer diesen einen Zweck reicht - erst damit darf das Passwort
 * ueberhaupt geaendert werden.
 *
 * Ohne gueltigen Link gibt es hier nichts zu tun; dann fuehrt der Bildschirm
 * zurueck zur Anmeldung, statt ein Formular zu zeigen, dessen Absenden
 * scheitern muesste.
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var passwordRules: PasswordRulesView

    /** Erst wenn der Link verarbeitet ist, gibt es eine Sitzung zum Aendern. */
    private var linkAccepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_reset_password)

        repository = AppRepository.get(this)

        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val btnSave = findViewById<Button>(R.id.btnSavePassword)
        val btnBack = findViewById<Button>(R.id.btnBackToLoginFromReset)

        passwordRules = PasswordRulesView(findViewById(android.R.id.content))
        passwordRules.show("")
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = passwordRules.show(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        repository.handleResetLink(intent) { linkAccepted = true }

        btnSave.setOnClickListener {
            val password = etNewPassword.text.toString()

            if (!PasswordPolicy.isValid(password)) {
                toast(R.string.error_password_rules)
                return@setOnClickListener
            }
            if (!linkAccepted) {
                // Etwa wenn der Bildschirm ohne Link erreicht wurde oder der
                // Link zu alt war. Ohne diesen Hinweis liefe der Nutzer in eine
                // Fehlermeldung des Servers, die ihm nichts sagt.
                showExpired()
                return@setOnClickListener
            }

            setBusy(true, btnSave, btnBack)
            lifecycleScope.launch {
                val error = repository.updatePassword(password)
                setBusy(false, btnSave, btnBack)

                if (error != null) {
                    toast(error.messageRes())
                    return@launch
                }
                // Abmelden, damit sich der Nutzer mit dem neuen Passwort
                // anmeldet. Die Sitzung aus dem Link ist danach erledigt.
                repository.logout()
                toast(R.string.reset_done)
                openLogin()
            }
        }

        btnBack.setOnClickListener { openLogin() }
    }

    private fun showExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_expired_title)
            .setMessage(R.string.reset_expired_message)
            .setPositiveButton(R.string.got_it) { _, _ -> openLogin() }
            .show()
    }

    private fun openLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setBusy(busy: Boolean, vararg views: View) {
        views.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
