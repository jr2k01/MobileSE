package com.example.mobilese.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Das laufende Workout auf der Uhr.
 *
 * Die Zeit laeuft ueber [SystemClock.elapsedRealtime] und nicht ueber die
 * Uhrzeit: eine Zeitumstellung oder ein Abgleich mit dem Netz wuerde sonst
 * mitten im Training die Dauer verschieben.
 *
 * Der Bildschirm bleibt an, solange trainiert wird - eine Uhr, die nach zehn
 * Sekunden abdunkelt, ist beim Laufen nutzlos.
 */
class WorkoutActivity : AppCompatActivity() {

    private lateinit var sport: String
    private lateinit var heartRate: HeartRateReader

    private var startedAt = 0L
    private var ticker: Job? = null

    private val askForSensor =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startHeartRate()
            } else {
                // Ohne Sensor laeuft das Workout weiter, nur eben ohne Puls.
                // Abzubrechen waere die schlechtere Antwort: Sportart und Dauer
                // sind auch ohne ihn etwas wert.
                Toast.makeText(this, R.string.sensor_permission_needed, Toast.LENGTH_SHORT).show()
                showHeartRate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sport = intent.getStringExtra(EXTRA_SPORT) ?: WatchSports.ALL.first()
        findViewById<TextView>(R.id.tvSport).text = sport

        heartRate = HeartRateReader(this)
        startedAt = SystemClock.elapsedRealtime()

        findViewById<TextView>(R.id.btnStop).setOnClickListener { finishWorkout() }

        requestSensorThenStart()
        startTicking()
    }

    private fun requestSensorThenStart() {
        if (!heartRate.isAvailable()) {
            showHeartRate()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) startHeartRate() else askForSensor.launch(Manifest.permission.BODY_SENSORS)
    }

    private fun startHeartRate() {
        heartRate.start { showHeartRate() }
        showHeartRate()
    }

    /** Die Anzeige laeuft im Sekundentakt mit, nicht schneller als noetig. */
    private fun startTicking() {
        ticker = lifecycleScope.launch {
            while (isActive) {
                showElapsed()
                delay(1000)
            }
        }
    }

    private fun showElapsed() {
        val seconds = elapsedSeconds()
        findViewById<TextView>(R.id.tvElapsed).text =
            String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun showHeartRate() {
        val view = findViewById<TextView>(R.id.tvHeartRate)
        view.text = when {
            !heartRate.isAvailable() -> getString(R.string.heart_rate_none)
            heartRate.latest == WatchProtocol.NO_BPM -> getString(R.string.heart_rate_waiting)
            else -> getString(R.string.heart_rate, heartRate.latest)
        }
    }

    private fun elapsedSeconds(): Long = (SystemClock.elapsedRealtime() - startedAt) / 1000

    /**
     * Beendet das Workout und schickt es ans Telefon.
     *
     * Unter einer Minute wird nichts geschickt: die App zaehlt in Minuten, und
     * ein Workout von null Minuten waere in der Rangliste ohnehin nichts wert.
     */
    private fun finishWorkout() {
        val minutes = (elapsedSeconds() / 60).toInt()
        heartRate.stop()
        ticker?.cancel()

        if (minutes < 1) {
            Toast.makeText(this, R.string.too_short, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.btnStop).isEnabled = false

        lifecycleScope.launch {
            val sent = PhoneLink.send(
                this@WorkoutActivity,
                sport,
                minutes,
                heartRate.average,
                heartRate.max
            )
            Toast.makeText(
                this@WorkoutActivity,
                if (sent) R.string.sent else R.string.not_sent,
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartRate.stop()
        ticker?.cancel()
    }

    companion object {
        private const val EXTRA_SPORT = "sport"

        fun intent(context: Context, sport: String): Intent =
            Intent(context, WorkoutActivity::class.java).putExtra(EXTRA_SPORT, sport)
    }
}
