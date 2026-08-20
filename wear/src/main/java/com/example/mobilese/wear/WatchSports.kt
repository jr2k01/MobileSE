package com.example.mobilese.wear

/**
 * Die Sportarten, die sich auf der Uhr starten lassen.
 *
 * Dieselben Namen wie in der App (Sports.kt) - sie wandern als Text zum
 * Telefon und landen dort unveraendert in der Aktivitaet. Ein abweichender
 * Name ergaebe eine eigene Sportart in der Auswertung.
 *
 * Kuerzer als die Liste im Telefon: auf einem runden Display in der Groesse
 * einer Muenze ist jede Zeile mehr eine Zeile, die niemand liest. Wer etwas
 * anderes trainiert, traegt es weiterhin am Telefon ein.
 */
object WatchSports {
    val ALL = listOf("Running", "Cycling", "Gym", "Swimming", "Yoga")
}
