package com.example.mobilese

/**
 * Ergebnisse der Anmeldung und Registrierung.
 *
 * Das Repository liefert diese Typen statt eines blossen Boolean zurueck. Ein
 * Boolean konnte nur "hat geklappt" oder "hat nicht geklappt" ausdruecken -
 * deshalb erschien bei einer Registrierung mit Bestaetigungsmail dieselbe
 * Meldung wie bei einem echten Fehler.
 *
 * Die Texte stehen bewusst nicht hier, sondern werden erst in der Activity aus
 * den String-Ressourcen geholt. So bleibt das Repository frei von
 * Oberflaechen-Belangen.
 */

/** Warum eine Anmeldung oder Registrierung nicht geklappt hat. */
enum class AuthError {
    /** Die Adresse hat bereits ein Konto. */
    EMAIL_ALREADY_REGISTERED,

    /** Supabase verlangt standardmaessig mindestens sechs Zeichen. */
    WEAK_PASSWORD,

    /** Die Adresse ist syntaktisch keine E-Mail-Adresse. */
    INVALID_EMAIL,

    /**
     * Zu viele Versuche in kurzer Zeit. Betrifft besonders den eingebauten
     * Mailversand von Supabase, der nur wenige Nachrichten pro Stunde
     * zulaesst - beim wiederholten Ausprobieren der Registrierung ist das
     * schnell erreicht.
     */
    RATE_LIMITED,

    /** Registrierung ist im Supabase-Projekt abgeschaltet. */
    SIGNUP_DISABLED,

    /** E-Mail oder Passwort stimmen nicht. */
    INVALID_CREDENTIALS,

    /** Der Code aus der Mail ist falsch oder abgelaufen. */
    CODE_INVALID,

    /** Das Konto existiert, die Adresse wurde aber noch nicht bestaetigt. */
    EMAIL_NOT_CONFIRMED,

    /** Kein Netz oder Server nicht erreichbar. */
    NETWORK,

    UNKNOWN
}

sealed interface RegistrationResult {

    /**
     * Konto angelegt, Bestaetigungsmail unterwegs. Eine Sitzung gibt es noch
     * nicht - der Nutzer muss erst den Link in der Mail oeffnen.
     */
    data object ConfirmationRequired : RegistrationResult

    /**
     * Konto angelegt und direkt angemeldet. Das passiert, wenn im
     * Supabase-Projekt die Bestaetigung per Mail abgeschaltet ist
     * (mailer_autoconfirm).
     */
    data object SignedIn : RegistrationResult

    data class Failed(val error: AuthError) : RegistrationResult
}

sealed interface LoginResult {

    data object Success : LoginResult

    data class Failed(val error: AuthError) : LoginResult
}
