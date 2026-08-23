package com.example.mobilese.wear

/**
 * Die Uhr eines Workouts auf der Smartwatch.
 *
 * Sie laeuft, bis pausiert wird, und macht danach dort weiter, wo aufgehoert
 * wurde - die Pause zaehlt nicht mit. Wer an der Ampel steht oder zwischen
 * zwei Saetzen verschnauft, hat in dieser Zeit nicht trainiert, und die Dauer
 * ist die Zahl, aus der spaeter Punkte werden.
 *
 * Bekommt die Zeit von aussen gereicht statt sie selbst abzulesen. Nur so
 * laesst sich das Anhalten und Fortsetzen pruefen, ohne wirklich zu warten -
 * ein Test, der eine Minute Training abbildet, darf keine Minute dauern.
 *
 * Alle Zeiten in Millisekunden, wie sie [android.os.SystemClock.elapsedRealtime]
 * liefert. Bewusst nicht die Uhrzeit: eine Zeitumstellung oder ein Abgleich mit
 * dem Netz wuerde sonst mitten im Training die Dauer verschieben.
 *
 * Der Zustand steckt in den beiden Werten des Konstruktors, damit ein Workout
 * einen Neustart des Prozesses ueberlebt: [WorkoutStore] schreibt sie weg und
 * baut die Uhr daraus wieder auf.
 *
 * **Diese Klasse gibt es zweimal** - hier und als Stopwatch.kt im app-Modul,
 * wo sie das gemeinsame Training zaehlt. Uhr und Telefon sind getrennte APKs
 * und teilen keinen Code; wie bei [WatchProtocol] ist der Verweis hier
 * billiger als ein drittes Gradle-Modul fuer eine Handvoll Zeilen.
 */
class Stopwatch(
    /** Was vor der laufenden Runde schon zusammengekommen ist. */
    private var accumulatedMillis: Long = 0L,
    /** Beginn der laufenden Runde, oder null wenn die Uhr steht. */
    private var runningSince: Long? = null
) {

    val isRunning: Boolean get() = runningSince != null

    /** Der Stand, wie ihn [WorkoutStore] ablegt. */
    val accumulated: Long get() = accumulatedMillis

    /** Der Beginn der laufenden Runde, wie ihn [WorkoutStore] ablegt. */
    val startedAt: Long? get() = runningSince

    /** Startet oder setzt fort. Ein zweiter Aufruf im Lauf aendert nichts. */
    fun start(now: Long) {
        if (isRunning) return
        runningSince = now
    }

    /** Haelt an und rechnet die gelaufene Runde dazu. */
    fun pause(now: Long) {
        val since = runningSince ?: return
        // Eine Zeit, die rueckwaerts laeuft, darf nichts abziehen.
        accumulatedMillis += (now - since).coerceAtLeast(0L)
        runningSince = null
    }

    /** Die bisherige Dauer in Sekunden, abgerundet. */
    fun elapsedSeconds(now: Long): Long = elapsedMillis(now) / 1000L

    /**
     * Die Dauer in vollen Minuten, so wie sie zum Telefon geht.
     *
     * Abgerundet: wer 59 Sekunden trainiert hat, hat keine Minute trainiert.
     */
    fun elapsedMinutes(now: Long): Int = (elapsedSeconds(now) / 60L).toInt()

    private fun elapsedMillis(now: Long): Long {
        val since = runningSince ?: return accumulatedMillis
        return accumulatedMillis + (now - since).coerceAtLeast(0L)
    }
}
