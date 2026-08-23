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

    /**
     * Der Rueckweg: das Telefon bestaetigt, dass das Workout in der Crew steht.
     *
     * Dahinter haengt derselbe Endzeitpunkt wie beim Hinweg - so weiss die Uhr,
     * welches ihrer Workouts gemeint ist, und zwei Bestaetigungen belegen nicht
     * dasselbe Element der Datenschicht.
     */
    const val PATH_LOGGED = "/crewfit/logged"

    const val KEY_SPORT = "sport"
    const val KEY_MINUTES = "minutes"
    const val KEY_AVG_BPM = "avg_bpm"
    const val KEY_MAX_BPM = "max_bpm"
    const val KEY_STEPS = "steps"
    const val KEY_ENDED_AT = "ended_at"

    /** Nur auf dem Rueckweg: was das Workout in der Crew wert war. */
    const val KEY_POINTS = "points"

    /** Kein Puls gemessen - die Uhr lag auf dem Tisch oder hat keinen Sensor. */
    const val NO_BPM = 0

    /**
     * Keine Schritte gezaehlt - kein Schrittzaehler oder keine Erlaubnis.
     *
     * Anders als beim Puls nicht die Null: null Schritte sind beim Yoga eine
     * richtige Messung und kein fehlender Wert. Ein Herz, das null Mal schlaegt,
     * gibt es dagegen nicht.
     */
    const val NO_STEPS = -1
}
