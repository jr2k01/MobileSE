package com.example.mobilese

/**
 * Die Uhr eines gemeinsamen Trainings.
 *
 * Sie laeuft, solange die Verbindung steht, und haelt an, sobald sie abreisst.
 * Wer sich wieder verbindet, macht dort weiter, wo aufgehoert wurde - die
 * Pause zaehlt nicht mit. Sonst waere ein Training, bei dem die Geraete
 * kurzzeitig ausser Reichweite geraten, laenger als es war.
 *
 * Bekommt die Zeit von aussen gereicht statt sie selbst abzulesen. Nur so
 * laesst sich das Anhalten und Fortsetzen pruefen, ohne wirklich zu warten -
 * ein Test, der eine Minute Training abbildet, darf keine Minute dauern.
 *
 * Alle Zeiten in Millisekunden, wie sie [android.os.SystemClock.elapsedRealtime]
 * liefert. Bewusst nicht die Uhrzeit: die kann sich waehrend des Trainings
 * verstellen, etwa beim Wechsel in eine andere Zeitzone.
 */
class Stopwatch {

    /** Was vor der laufenden Runde schon zusammengekommen ist. */
    private var accumulatedMillis = 0L

    /** Beginn der laufenden Runde, oder null wenn die Uhr steht. */
    private var runningSince: Long? = null

    val isRunning: Boolean get() = runningSince != null

    /** Ob ueberhaupt schon einmal gelaufen wurde. */
    val hasStarted: Boolean get() = isRunning || accumulatedMillis > 0L

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
    fun elapsedSeconds(now: Long): Int = (elapsedMillis(now) / 1000L).toInt()

    /**
     * Die Dauer in vollen Minuten, so wie sie gespeichert wird.
     *
     * Abgerundet: wer 59 Sekunden trainiert hat, hat keine Minute trainiert.
     * Die App verlangt ohnehin mindestens zehn.
     */
    fun elapsedMinutes(now: Long): Int = elapsedSeconds(now) / 60

    private fun elapsedMillis(now: Long): Long {
        val since = runningSince ?: return accumulatedMillis
        return accumulatedMillis + (now - since).coerceAtLeast(0L)
    }

    /** Setzt zurueck, fuer ein neues Training. */
    fun reset() {
        accumulatedMillis = 0L
        runningSince = null
    }
}
