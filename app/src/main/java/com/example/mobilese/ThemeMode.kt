package com.example.mobilese

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * Hell, dunkel oder wie das Geraet.
 *
 * Reine Zuordnung zwischen dem, was gespeichert wird, und dem, was AppCompat
 * versteht - ohne Android-Aufrufe und deshalb ohne Emulator testbar. Gesetzt
 * wird der Modus an genau zwei Stellen: beim Start der App ([CrewFitApp]) und
 * wenn ihn jemand in den Einstellungen aendert.
 *
 * Gespeichert wird der Name, nicht die Zahl von AppCompat: die Zahlen sind
 * Implementierungsdetail der Bibliothek, und ein gespeichertes "LIGHT" bleibt
 * lesbar, auch wenn sich dahinter etwas verschiebt.
 */
enum class ThemeMode(
    val storedName: String,
    val delegateValue: Int,
    @StringRes val labelRes: Int
) {

    /**
     * Was das Geraet vorgibt. Voreinstellung, weil die App damit dem folgt, was
     * jemand fuer alle seine Apps eingestellt hat - eine Entscheidung, die man
     * nicht noch einmal treffen moechte.
     */
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.settings_theme_system),

    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO, R.string.settings_theme_light),

    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES, R.string.settings_theme_dark);

    companion object {

        val DEFAULT = SYSTEM

        /**
         * Der zu einem gespeicherten Namen gehoerende Modus.
         *
         * Faellt auf [DEFAULT] zurueck, wenn nichts oder etwas Unbekanntes
         * gespeichert ist - ein unlesbarer Wert darf die App nicht daran
         * hindern, ueberhaupt zu starten.
         */
        fun fromStored(stored: String?): ThemeMode =
            entries.firstOrNull { it.storedName == stored } ?: DEFAULT

        /** Setzt den Modus fuer die ganze App. */
        fun apply(mode: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(mode.delegateValue)
        }
    }
}
