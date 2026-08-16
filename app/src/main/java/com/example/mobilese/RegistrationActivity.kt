package com.example.mobilese

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    /** Zu jeder Regel die Zeile, die sie anzeigt, und ihr Text. */
    private lateinit var ruleRows: Map<PasswordPolicy.Rule, Pair<TextView, Int>>

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

        ruleRows = mapOf(
            PasswordPolicy.Rule.LENGTH to
                    (findViewById<TextView>(R.id.tvRuleLength) to R.string.pw_rule_length),
            PasswordPolicy.Rule.UPPERCASE to
                    (findViewById<TextView>(R.id.tvRuleUppercase) to R.string.pw_rule_uppercase),
            PasswordPolicy.Rule.LOWERCASE to
                    (findViewById<TextView>(R.id.tvRuleLowercase) to R.string.pw_rule_lowercase),
            PasswordPolicy.Rule.DIGIT to
                    (findViewById<TextView>(R.id.tvRuleDigit) to R.string.pw_rule_digit),
            PasswordPolicy.Rule.SPECIAL to
                    (findViewById<TextView>(R.id.tvRuleSpecial) to R.string.pw_rule_special)
        )
        showPasswordRules("")

        // Regeln bei jedem Tastendruck neu bewerten, damit der Nutzer sieht,
        // was noch fehlt, statt es nach dem Absenden zu erfahren.
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = showPasswordRules(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        // Geburtsdatum wird im Kalender gewaehlt statt getippt.
        val applyPickedDate: (String) -> Unit = { picked -> etBirthDate.setText(picked) }
        etBirthDate.setOnClickListener {
            BirthDatePicker.show(this, etBirthDate.text.toString(), applyPickedDate)
        }
        // Falls der Kalender offen war, als das Geraet gedreht wurde.
        BirthDatePicker.reattach(this, applyPickedDate)

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
            if (!InputRules.isValidName(name)) {
                toast(R.string.error_name_invalid)
                return@setOnClickListener
            }
            if (!BirthDate.isValid(birthDate)) {
                toast(R.string.select_birth_date)
                return@setOnClickListener
            }
            // Ein Geburtsdatum von vor zwei Jahren ist zwar ein gueltiges
            // Datum, aber kein plausibles Profil.
            val age = BirthDate.ageFrom(birthDate)
            if (age == null || age < InputRules.MIN_AGE_YEARS) {
                toastFormatted(R.string.error_min_age, InputRules.MIN_AGE_YEARS)
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast(R.string.error_invalid_email)
                return@setOnClickListener
            }
            if (!PasswordPolicy.isValid(password)) {
                toast(R.string.error_password_rules)
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
        MaterialAlertDialogBuilder(this)
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(error.messageRes())
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    /** Faerbt jede Regelzeile je nachdem, ob sie erfuellt ist. */
    private fun showPasswordRules(password: String) {
        ruleRows.forEach { (rule, row) ->
            val (view, labelRes) = row
            val met = PasswordPolicy.isMet(rule, password)

            view.text = getString(
                if (met) R.string.rule_met else R.string.rule_unmet,
                getString(labelRes)
            )
            view.setTextColor(
                ContextCompat.getColor(this, if (met) R.color.accent else R.color.text_secondary)
            )
        }
    }

    private fun setBusy(busy: Boolean, vararg buttons: View) {
        buttons.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

    private fun toastFormatted(resId: Int, vararg args: Any) =
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
}
