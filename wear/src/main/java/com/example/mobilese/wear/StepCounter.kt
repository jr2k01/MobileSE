package com.example.mobilese.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Zaehlt die Schritte, die waehrend des Workouts gemacht werden.
 *
 * Der Schrittzaehler der Uhr ([Sensor.TYPE_STEP_COUNTER]) liefert nicht die
 * Schritte seit dem Anmelden, sondern die seit dem letzten Neustart des
 * Geraets - eine Zahl, die auch waehrend des Fruehstuecks weiterlaeuft.
 * Gebraucht wird die Differenz, also merkt sich diese Klasse den Stand beim
 * Start und zieht ihn ab.
 *
 * Wird pausiert, wird der bisherige Abschnitt weggeschrieben und beim
 * Fortsetzen ein neuer begonnen. Ohne das zaehlte der Weg zum Getraenkeautomat
 * mit, denn der Sensor laeuft weiter, ob die Uhr ihn abfragt oder nicht.
 *
 * Genau deshalb ueberlebt der Stand auch einen Neustart des Prozesses: der
 * Anfangswert des Abschnitts bleibt gueltig, weil der Sensor waehrenddessen
 * einfach weitergezaehlt hat. Nur ein Neustart der *Uhr* setzt ihn zurueck -
 * und den erkennt [WorkoutStore].
 *
 * Der Sensor braucht ab Android 10 die Erlaubnis ACTIVITY_RECOGNITION. Fehlt
 * sie, liefert er einfach nichts - deshalb wird sie vorher erfragt.
 */
class StepCounter(context: Context) : SensorEventListener {

    /**
     * Der Stand des Zaehlers, wie ihn [WorkoutStore] ablegt.
     *
     * [segmentStart] ist [NO_SEGMENT], solange kein Abschnitt laeuft.
     */
    data class State(val completed: Int, val segmentStart: Long, val segmentLatest: Long)

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** Was in den abgeschlossenen Abschnitten zusammengekommen ist. */
    private var completed = 0

    /** Der Stand des Zaehlers zu Beginn des laufenden Abschnitts. */
    private var segmentStart: Long? = null

    /** Der zuletzt gemeldete Stand des Zaehlers. */
    private var segmentLatest = 0L

    /** Ob die Uhr ueberhaupt einen Schrittzaehler hat. */
    fun isAvailable(): Boolean = sensor != null

    /**
     * Die Schritte seit dem Start des Workouts, ohne die Pausen.
     *
     * [WatchProtocol.NO_STEPS], solange nichts gemessen werden konnte - eine
     * Uhr ohne Sensor meldet nicht null Schritte, sondern gar keine.
     */
    val steps: Int
        get() {
            if (!isAvailable()) return WatchProtocol.NO_STEPS
            val start = segmentStart ?: return completed
            return completed + (segmentLatest - start).coerceAtLeast(0L).toInt()
        }

    fun state(): State = State(completed, segmentStart ?: NO_SEGMENT, segmentLatest)

    /** Nimmt den Stand aus der Ablage an, nach einem Neustart des Prozesses. */
    fun restore(state: State) {
        completed = state.completed
        segmentStart = state.segmentStart.takeIf { it != NO_SEGMENT }
        segmentLatest = state.segmentLatest
    }

    /**
     * @param onChange wird bei jedem Schritt aufgerufen. Ohne Angabe wird nur
     *        gezaehlt - der Dienst fragt den Stand im Sekundentakt ab, statt
     *        sich wecken zu lassen.
     */
    fun start(onChange: () -> Unit = {}) {
        listener = onChange
        val available = sensor ?: return
        // Der Schrittzaehler meldet sich beim Anmelden sofort mit seinem
        // aktuellen Stand - der wird unten zum Anfang dieses Abschnitts.
        manager?.registerListener(this, available, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** Haelt an und schreibt den laufenden Abschnitt fest. */
    fun stop() {
        val start = segmentStart
        if (start != null) {
            completed += (segmentLatest - start).coerceAtLeast(0L).toInt()
            segmentStart = null
        }
        manager?.unregisterListener(this)
        listener = null
    }

    private var listener: (() -> Unit)? = null

    override fun onSensorChanged(event: SensorEvent) {
        val counter = event.values.firstOrNull()?.toLong() ?: return
        segmentLatest = counter
        if (segmentStart == null) {
            segmentStart = counter
            return
        }
        listener?.invoke()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        /** Kein laufender Abschnitt. Der Sensor selbst zaehlt nie negativ. */
        const val NO_SEGMENT = -1L
    }
}
