package com.example.mobilese.wear

/**
 * Was Uhr und Telefon sich zu sagen haben.
 *
 * **Diese Datei gibt es zweimal** - hier und als WatchProtocol.kt im
 * app-Modul. Uhr und Telefon sind getrennte APKs und teilen keinen Code; die
 * Datenverbindung kennt nur einen Pfad und einen Sack aus Werten. Aendert sich
 * hier etwas, muss es dort nachgezogen werden, sonst kommt die Nachricht an
 * und niemand versteht sie.
 *
 * Bewusst wenige Felder: die Uhr schickt, was nur sie weiss - Sportart, Dauer
 * und Puls. Foto und Ort traegt das Telefon nach, wo eine Kamera und eine
 * Tastatur sind.
 */
object WatchProtocol {

    /**
     * Anfang des Pfads. Dahinter haengt der Endzeitpunkt des Workouts, damit
     * zwei Workouts nicht dasselbe Element der Datenschicht belegen.
     *
     * Frei gewaehlt, muss auf beiden Seiten gleich sein.
     */
    const val PATH_WORKOUT = "/crewfit/workout"

    const val KEY_SPORT = "sport"
    const val KEY_MINUTES = "minutes"
    const val KEY_AVG_BPM = "avg_bpm"
    const val KEY_MAX_BPM = "max_bpm"
    const val KEY_ENDED_AT = "ended_at"

    /** Kein Puls gemessen - die Uhr lag auf dem Tisch oder hat keinen Sensor. */
    const val NO_BPM = 0
}
