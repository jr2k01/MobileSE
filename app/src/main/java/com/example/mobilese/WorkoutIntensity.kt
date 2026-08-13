package com.example.mobilese

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutIntensity(val multiplier: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    companion object {
        /**
         * Liest eine Intensitaet aus der Datenbank. Unbekannte oder leere Werte
         * ergeben MEDIUM statt einer Exception - in der Datenbank koennen
         * Altbestaende oder von Hand eingetragene Zeilen stehen, und eine
         * einzelne unlesbare Aktivitaet darf nicht die ganze Rangliste kippen.
         */
        fun fromName(value: String?): WorkoutIntensity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}
