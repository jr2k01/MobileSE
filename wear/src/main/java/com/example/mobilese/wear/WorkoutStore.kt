package com.example.mobilese.wear

import android.content.Context
import android.os.SystemClock

/**
 * Das laufende Workout, festgehalten fuer den Fall, dass der Prozess stirbt.
 *
 * Ein Vordergrunddienst haelt die App am Leben, aber nicht um jeden Preis: bei
 * knappem Speicher raeumt das System auch ihn ab. Ohne diese Ablage waere ein
 * Training dann weg - und zwar genau das lange, denn kurze ueberleben immer.
 *
 * In den SharedPreferences und in einzelnen Werten statt als JSON: das
 * wear-Modul bindet kotlinx-serialization nicht ein, und fuer neun Zahlen
 * lohnt sich keine Bibliothek.
 *
 * Alle Zeiten stammen aus [SystemClock.elapsedRealtime]. Das zaehlt seit dem
 * letzten Start der Uhr, weshalb hier auch abgelegt wird, *wann* gesichert
 * wurde: laeuft die Zeit beim Lesen hinter dem gesicherten Stand, war die Uhr
 * zwischendurch aus. Dann sind sowohl die Uhrzeit als auch der Schrittzaehler
 * wertlos, und das Workout wird verworfen statt falsch fortgesetzt.
 */
object WorkoutStore {

    /** Ein Workout, so wie es die Ablage ueberdauert. */
    data class Session(
        val sport: String,
        val accumulatedMillis: Long,
        /** Beginn der laufenden Runde, oder null wenn pausiert. */
        val runningSince: Long?,
        val heartRate: HeartRateReader.State,
        val steps: StepCounter.State
    )

    private const val PREFS = "running_workout"

    private const val KEY_SPORT = "sport"
    private const val KEY_ACCUMULATED = "accumulated"
    private const val KEY_RUNNING_SINCE = "running_since"
    private const val KEY_HR_SUM = "hr_sum"
    private const val KEY_HR_COUNT = "hr_count"
    private const val KEY_HR_MAX = "hr_max"
    private const val KEY_HR_LATEST = "hr_latest"
    private const val KEY_STEPS_DONE = "steps_done"
    private const val KEY_STEPS_START = "steps_start"
    private const val KEY_STEPS_LATEST = "steps_latest"
    private const val KEY_SAVED_AT = "saved_at"

    /** Pausiert - dieselbe Rolle wie null bei [Session.runningSince]. */
    private const val NOT_RUNNING = -1L

    fun save(context: Context, session: Session) {
        prefs(context).edit()
            .putString(KEY_SPORT, session.sport)
            .putLong(KEY_ACCUMULATED, session.accumulatedMillis)
            .putLong(KEY_RUNNING_SINCE, session.runningSince ?: NOT_RUNNING)
            .putLong(KEY_HR_SUM, session.heartRate.sum)
            .putInt(KEY_HR_COUNT, session.heartRate.count)
            .putInt(KEY_HR_MAX, session.heartRate.max)
            .putInt(KEY_HR_LATEST, session.heartRate.latest)
            .putInt(KEY_STEPS_DONE, session.steps.completed)
            .putLong(KEY_STEPS_START, session.steps.segmentStart)
            .putLong(KEY_STEPS_LATEST, session.steps.segmentLatest)
            .putLong(KEY_SAVED_AT, SystemClock.elapsedRealtime())
            // Sofort auf die Platte und nicht mit apply(): der naechste
            // Augenblick kann der sein, in dem der Prozess stirbt.
            .commit()
    }

    /**
     * Das gesicherte Workout, oder null wenn keines wartet.
     *
     * Ein Workout aus der Zeit vor einem Neustart der Uhr wird verworfen und
     * gleich mit aufgeraeumt: es taugt weder als Dauer noch als Schrittzahl,
     * und stehen zu lassen hiesse, es dem Nutzer bei jedem Blick erneut
     * anzubieten.
     */
    fun load(context: Context): Session? {
        val stored = prefs(context)
        val sport = stored.getString(KEY_SPORT, null) ?: return null

        if (stored.getLong(KEY_SAVED_AT, 0L) > SystemClock.elapsedRealtime()) {
            clear(context)
            return null
        }

        return Session(
            sport = sport,
            accumulatedMillis = stored.getLong(KEY_ACCUMULATED, 0L),
            runningSince = stored.getLong(KEY_RUNNING_SINCE, NOT_RUNNING)
                .takeIf { it != NOT_RUNNING },
            heartRate = HeartRateReader.State(
                sum = stored.getLong(KEY_HR_SUM, 0L),
                count = stored.getInt(KEY_HR_COUNT, 0),
                max = stored.getInt(KEY_HR_MAX, WatchProtocol.NO_BPM),
                latest = stored.getInt(KEY_HR_LATEST, WatchProtocol.NO_BPM)
            ),
            steps = StepCounter.State(
                completed = stored.getInt(KEY_STEPS_DONE, 0),
                segmentStart = stored.getLong(KEY_STEPS_START, StepCounter.NO_SEGMENT),
                segmentLatest = stored.getLong(KEY_STEPS_LATEST, 0L)
            )
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    /**
     * Die bisherige Dauer eines gesicherten Workouts in Sekunden.
     *
     * Fuer die Zeile auf dem Startbildschirm, die zum laufenden Training
     * zurueckfuehrt - dafuer eigens den Dienst anzusprechen waere Aufwand fuer
     * eine Zahl, die auch in der Ablage steht.
     */
    fun elapsedSeconds(session: Session): Long =
        Stopwatch(session.accumulatedMillis, session.runningSince)
            .elapsedSeconds(SystemClock.elapsedRealtime())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
