package com.example.mobilese.wear

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
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
 * Die Anzeige des laufenden Workouts.
 *
 * Sie fuehrt das Training nicht mehr selbst - das tut [WorkoutService], und
 * zwar auch dann noch, wenn diese Activity laengst abgeraeumt ist. Hier wird
 * im Sekundentakt abgefragt und gezeichnet, und es werden die drei Knoepfe
 * bedient: Pause, Weiter, Stopp.
 *
 * Abgefragt statt benachrichtigt: gezeichnet wird ohnehin nur einmal je
 * Sekunde, und ein Rueckruf je Pulsschlag waere ein Weckruf mehr, der nichts
 * aendert, was man sehen koennte.
 *
 * Der Bildschirm bleibt an, solange trainiert wird - eine Uhr, die nach zehn
 * Sekunden abdunkelt, ist beim Laufen nutzlos. Anders als frueher ist das aber
 * nur noch Bequemlichkeit und keine Bedingung: geht er aus, laeuft das Workout
 * weiter.
 */
class WorkoutActivity : AppCompatActivity() {

    private var workout: WorkoutService? = null
    private var ticker: Job? = null

    /**
     * Beide Sensorrechte und das Melderecht in einem Zug.
     *
     * Drei Abfragen nacheinander waeren drei Dialoge auf einem Display in der
     * Groesse einer Muenze, bevor das Training ueberhaupt begonnen hat. Das
     * Melderecht ist dabei, weil ohne es die Meldung des Vordergrunddienstes
     * unsichtbar bliebe - und mit ihr der Weg zurueck ins laufende Training.
     */
    private val askForSensors =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            // Ohne Sensor laeuft das Workout weiter, nur eben ohne Puls.
            // Abzubrechen waere die schlechtere Antwort: Sportart und Dauer
            // sind auch ohne ihn etwas wert.
            if (granted[Manifest.permission.BODY_SENSORS] == false) {
                Toast.makeText(this, R.string.sensor_permission_needed, Toast.LENGTH_SHORT).show()
            }
            // Was jetzt erlaubt ist, soll ab jetzt gemessen werden - und nicht
            // erst beim naechsten Training.
            workout?.attachSensors()
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val bound = (service as? WorkoutService.LocalBinder)?.service
            workout = bound

            // Kein Training in Sicht: das passiert, wenn diese Activity ueber
            // eine alte Meldung geoeffnet wird, deren Workout laengst
            // eingetragen ist.
            if (bound?.sport == null) {
                finish()
                return
            }

            findViewById<TextView>(R.id.tvSport).text = bound.sport
            requestSensorsIfNeeded(bound)
            draw()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            workout = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sport = intent.getStringExtra(EXTRA_SPORT)
        // Mit Sportart beginnt ein neues Training, ohne wird das laufende
        // wieder aufgenommen - aus der Meldung oder vom Startbildschirm.
        if (sport != null) WorkoutService.start(this, sport) else WorkoutService.resume(this)

        findViewById<TextView>(R.id.btnStop).setOnClickListener { finishWorkout() }
        findViewById<TextView>(R.id.btnPause).setOnClickListener { togglePause() }

        bindService(
            Intent(this, WorkoutService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStart() {
        super.onStart()
        // Die Anzeige laeuft im Sekundentakt mit, nicht schneller als noetig -
        // und nur, solange jemand hinsieht.
        ticker = lifecycleScope.launch {
            while (isActive) {
                draw()
                delay(1000)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        ticker?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    /**
     * Nur die Rechte erfragen, fuer die diese Uhr auch einen Sensor hat.
     *
     * Nach dem Schrittzaehler zu fragen, wo keiner verbaut ist, waere ein
     * Dialog, auf den nichts folgt - und die naechste Frage wird dann schneller
     * weggetippt als gelesen.
     */
    private fun requestSensorsIfNeeded(running: WorkoutService) {
        val needed = buildList {
            if (running.hasHeartRateSensor()) add(Manifest.permission.BODY_SENSORS)
            if (running.hasStepSensor()) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filterNot { isGranted(it) }

        if (missing.isNotEmpty()) askForSensors.launch(missing.toTypedArray())
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun togglePause() {
        val running = workout ?: return
        if (running.isRunning) running.pause() else running.resume()
        draw()
    }

    private fun draw() {
        val running = workout ?: return
        if (running.sport == null) return

        showElapsed(running)
        showPauseButton(running)
        showHeartRate(running)
        showSteps(running)
    }

    private fun showElapsed(running: WorkoutService) {
        val seconds = running.elapsedSeconds()
        val view = findViewById<TextView>(R.id.tvElapsed)
        view.text = String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        // Eine stehende Uhr saehe sonst aus wie eine laufende, die haengt.
        view.setTextColor(if (running.isRunning) COLOR_RUNNING else COLOR_PAUSED)
    }

    private fun showPauseButton(running: WorkoutService) {
        findViewById<TextView>(R.id.btnPause)
            .setText(if (running.isRunning) R.string.pause else R.string.resume)
    }

    private fun showHeartRate(running: WorkoutService) {
        val view = findViewById<TextView>(R.id.tvHeartRate)
        view.text = when {
            // Waehrend der Pause ist der Sensor abgemeldet. Den letzten Wert
            // stehen zu lassen behauptete eine Messung, die nicht stattfindet.
            !running.isRunning -> getString(R.string.paused)
            !running.hasHeartRateSensor() -> getString(R.string.heart_rate_none)
            running.latestHeartRate() == WatchProtocol.NO_BPM ->
                getString(R.string.heart_rate_waiting)
            else -> getString(R.string.heart_rate, running.latestHeartRate())
        }
    }

    /**
     * Die Schritte stehen erst da, wenn welche gemacht wurden.
     *
     * Eine Zeile "0 steps" waere auf einem runden Display eine Zeile weniger
     * fuer alles andere - und beim Yoga bliebe sie den ganzen Abend stehen.
     */
    private fun showSteps(running: WorkoutService) {
        val view = findViewById<TextView>(R.id.tvSteps)
        val counted = running.countedSteps()
        if (counted <= 0) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = getString(R.string.steps, counted)
    }

    /**
     * Beendet das Workout.
     *
     * Verschickt wird im Dienst: wer die Uhr nach dem Stopp sinken laesst,
     * nimmt dieser Activity den Boden unter den Fuessen weg. Was sie noch tun
     * kann, ist die Antwort zu zeigen, falls sie so lange lebt.
     */
    private fun finishWorkout() {
        val running = workout ?: return

        findViewById<TextView>(R.id.btnStop).isEnabled = false
        findViewById<TextView>(R.id.btnPause).isEnabled = false

        running.finish { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        private const val EXTRA_SPORT = "sport"

        private const val COLOR_RUNNING = 0xFFE3E2E9.toInt()
        private const val COLOR_PAUSED = 0xFF8B8D94.toInt()

        /** Beginnt ein neues Workout. */
        fun intent(context: Context, sport: String): Intent =
            Intent(context, WorkoutActivity::class.java).putExtra(EXTRA_SPORT, sport)

        /**
         * Zeigt das laufende Workout wieder an.
         *
         * `CLEAR_TOP`, damit aus der Meldung heraus nicht eine zweite Anzeige
         * ueber der ersten steht: es gibt nur ein Training.
         */
        fun resumeIntent(context: Context): Intent =
            Intent(context, WorkoutActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
