package com.example.mobilese

import androidx.annotation.StringRes

/**
 * Uebersetzt einen [AuthError] in den Text, der dem Nutzer angezeigt wird.
 *
 * Bewusst getrennt vom Repository: dort wird nur festgestellt, *was* schief
 * ging, hier wird entschieden, *wie* man es formuliert. So bleibt die
 * Datenschicht ohne Verweis auf Oberflaechen-Ressourcen.
 */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.EMAIL_ALREADY_REGISTERED -> R.string.error_email_already_registered
    AuthError.WEAK_PASSWORD -> R.string.error_weak_password
    AuthError.INVALID_EMAIL -> R.string.error_invalid_email
    AuthError.RATE_LIMITED -> R.string.error_rate_limited
    AuthError.SIGNUP_DISABLED -> R.string.error_signup_disabled
    AuthError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    AuthError.CODE_INVALID -> R.string.error_code_invalid
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.not_confirmed_message
    AuthError.NETWORK -> R.string.error_network
    AuthError.UNKNOWN -> R.string.error_unknown
}
