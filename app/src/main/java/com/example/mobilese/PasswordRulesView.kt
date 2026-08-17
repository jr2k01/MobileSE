package com.example.mobilese

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Haelt die Liste der Passwortregeln aktuell, waehrend getippt wird.
 *
 * Erfuellte Regeln werden abgehakt und eingefaerbt, offene bleiben zurueckhaltend
 * stehen. So sieht der Nutzer, was noch fehlt, statt es erst nach dem Absenden
 * zu erfahren.
 *
 * Steht hier einmal, weil zwei Bildschirme dieselbe Liste zeigen: die
 * Registrierung und das Neusetzen des Passworts nach dem Link aus der Mail.
 * Erwartet die Zeilen aus [R.layout.part_password_rules] irgendwo unterhalb von
 * [root].
 */
class PasswordRulesView(root: View) {

    private val rows: Map<PasswordPolicy.Rule, Pair<TextView, Int>> = mapOf(
        PasswordPolicy.Rule.LENGTH to
                (root.findViewById<TextView>(R.id.tvRuleLength) to R.string.pw_rule_length),
        PasswordPolicy.Rule.UPPERCASE to
                (root.findViewById<TextView>(R.id.tvRuleUppercase) to R.string.pw_rule_uppercase),
        PasswordPolicy.Rule.LOWERCASE to
                (root.findViewById<TextView>(R.id.tvRuleLowercase) to R.string.pw_rule_lowercase),
        PasswordPolicy.Rule.DIGIT to
                (root.findViewById<TextView>(R.id.tvRuleDigit) to R.string.pw_rule_digit),
        PasswordPolicy.Rule.SPECIAL to
                (root.findViewById<TextView>(R.id.tvRuleSpecial) to R.string.pw_rule_special)
    )

    fun show(password: String) {
        rows.forEach { (rule, row) ->
            val (view, labelRes) = row
            val met = PasswordPolicy.isMet(rule, password)
            val context = view.context

            view.text = context.getString(
                if (met) R.string.rule_met else R.string.rule_unmet,
                context.getString(labelRes)
            )
            view.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (met) R.color.accent else R.color.text_secondary
                )
            )
        }
    }
}
