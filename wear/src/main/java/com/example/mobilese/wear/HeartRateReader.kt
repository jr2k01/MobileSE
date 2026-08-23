package com.example.mobilese.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Liest den Pulssensor der Uhr, solange das Workout laeuft.
 *
 * Ueber den [SensorManager] aus dem SDK und nicht ueber Health Services: der
 * Sensor liefert genau eine Zahl, und dafuer eine weitere Bibliothek
 * einzubinden waere Aufwand ohne Gewinn.
 *
 * Gesammelt wird waehrenddessen nur Summe und Anzahl, nicht jede Messung. Fuer
 * Durchschnitt und Hoechstwert genuegt das, und eine Liste mit tausenden
 * Eintraegen muesste auf einer Uhr im Speicher gehalten werden.
 */
class HeartRateReader(context: Context) : SensorEventListener {

    /** Der Stand des Zaehlers, wie ihn [WorkoutStore] ablegt. */
    data class State(val sum: Long, val count: Int, val max: Int, val latest: Int)

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private var sum = 0L
    private var count = 0

    /** Hoechster gemessener Wert, oder [WatchProtocol.NO_BPM]. */
    var max = WatchProtocol.NO_BPM
        private set

    /** Zuletzt gemessener Wert - das, was auf dem Display steht. */
    var latest = WatchProtocol.NO_BPM
        private set

    /** Durchschnitt ueber alle Messungen, oder [WatchProtocol.NO_BPM]. */
    val average: Int
        get() = if (count == 0) WatchProtocol.NO_BPM else (sum / count).toInt()

    /** Ob die Uhr ueberhaupt einen Pulssensor hat. */
    fun isAvailable(): Boolean = sensor != null

    fun state(): State = State(sum, count, max, latest)

    /** Nimmt den Stand aus der Ablage an, nach einem Neustart des Prozesses. */
    fun restore(state: State) {
        sum = state.sum
        count = state.count
        max = state.max
        latest = state.latest
    }

    /**
     * @param onChange wird bei jeder Messung aufgerufen. Ohne Angabe wird nur
     *        gesammelt - der Dienst fragt den Stand im Sekundentakt ab, statt
     *        sich wecken zu lassen.
     */
    fun start(onChange: () -> Unit = {}) {
        listener = onChange
        val available = sensor ?: return
        // SENSOR_DELAY_NORMAL genuegt: ein Puls aendert sich nicht im
        // Millisekundentakt, und haeufiger zu messen kostet nur Akku.
        manager?.registerListener(this, available, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        manager?.unregisterListener(this)
        listener = null
    }

    private var listener: (() -> Unit)? = null

    override fun onSensorChanged(event: SensorEvent) {
        val bpm = event.values.firstOrNull()?.toInt() ?: return
        // Null kommt vor, solange die Uhr noch keinen Kontakt zur Haut hat.
        // Solche Messungen wuerden den Durchschnitt nach unten ziehen.
        if (bpm <= 0) return

        sum += bpm
        count++
        latest = bpm
        if (bpm > max) max = bpm
        listener?.invoke()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("CrewFitWear", "Heart rate accuracy is now $accuracy")
    }
}
