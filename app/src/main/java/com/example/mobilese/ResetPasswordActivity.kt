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

        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        val tilCode = findViewById<View>(R.id.tilResetCode)
        val etCode = findViewById<EditText>(R.id.etResetCode)

        // Ueber den Link liegt die Sitzung schon vor - dann ist nichts
        // abzutippen. Sonst fuehrt der Code aus derselben Mail zum Ziel.
        tilCode.visibility = if (linkAccepted) View.GONE else View.VISIBLE

        btnSave.setOnClickListener {
            val password = etNewPassword.text.toString()
            val code = etCode.text.toString().trim()

            if (!PasswordPolicy.isValid(password)) {
                toast(R.string.error_password_rules)
                return@setOnClickListener
            }
            if (!linkAccepted && (code.isEmpty() || email.isEmpty())) {
                // Weder Link noch Code: etwa wenn der Bildschirm ohne beides
                // erreicht wurde. Ohne diesen Hinweis liefe der Nutzer in eine
                // Fehlermeldung des Servers, die ihm nichts sagt.
                if (email.isEmpty()) showExpired() else toast(R.string.reset_code_required)
                return@setOnClickListener
            }

            setBusy(true, btnSave, btnBack)
            lifecycleScope.launch {
                // Erst den Code einloesen, falls es keinen Link gab - ohne
                // Sitzung laesst sich das Passwort nicht aendern.
                val codeError =
                    if (linkAccepted) null else repository.verifyRecoveryCode(email, code)

                val error = codeError ?: repository.updatePassword(password)
                setBusy(false, btnSave, btnBack)

                if (error != null) {
                    toast(error.messageRes())
                    return@launch
                }
                // Abmelden, damit sich der Nutzer mit dem neuen Passwort
                // anmeldet. Die Sitzung aus Link oder Code ist danach erledigt.
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

    companion object {
        private const val EXTRA_EMAIL = "email"

        /**
         * Weg vom Anmeldebildschirm aus, direkt nach dem Verschicken der Mail.
         * Die Adresse wird mitgegeben, weil der Code nur zusammen mit ihr
         * eingeloest werden kann.
         */
        fun intent(context: android.content.Context, email: String): Intent =
            Intent(context, ResetPasswordActivity::class.java).putExtra(EXTRA_EMAIL, email)
    }
}
