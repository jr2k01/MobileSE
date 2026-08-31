package com.example.mobilese.wear

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Der Startbildschirm der Uhr: Sportart waehlen, dann laeuft es los.
 *
 * Kein Anmelden, keine Crew, keine Rangliste - die Uhr macht genau eine Sache.
 * Alles, was ein Konto braucht, passiert auf dem Telefon; die Uhr schickt nur
 * hin, was sie gemessen hat.
 *
 * Ueber der Liste stehen drei Zeilen, die es nur gibt, wenn es sie zu geben
 * hat: das Training, das noch laeuft; die Rueckmeldung des Telefons zum
 * letzten; und der Hinweis, dass das Telefon gerade fehlt.
 */
class SportChoiceActivity : AppCompatActivity() {

    /**
     * Die Antwort auf die Rechtefrage. Sie wird nicht ausgewertet: wer
     * ablehnt, trainiert eben ohne Puls, und das steht spaeter im Bildschirm.
     */
    private val askForSensors =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_sport_choice)

        askBeforeTheClockRuns()

        val container = findViewById<LinearLayout>(R.id.llSports)
        val inflater = LayoutInflater.from(this)

        for (sport in WatchSports.ALL) {
            val row = inflater.inflate(R.layout.item_sport, container, false) as TextView
            row.text = sport
            row.setOnClickListener {
                startActivity(WorkoutActivity.intent(this, sport))
            }
            container.addView(row)
        }
    }

    /**
     * Die Rechte erfragen, **bevor** die Stoppuhr laeuft.
     *
     * Gefragt wurde frueher erst im Trainingsbildschirm - also nachdem der
     * Dienst gestartet und die Uhr angelaufen war. Wer zehn Sekunden ueberlegt,
     * ob er seine Vitalwerte hergeben will, hatte danach zehn Sekunden
     * Training, die er nicht trainiert hat. Hier steht die Frage vor der
     * Sportauswahl, wo noch nichts zaehlt.
     *
     * Gefragt wird nur nach dem, wofuer diese Uhr auch einen Sensor hat: eine
     * Frage nach dem Schrittzaehler, wo keiner verbaut ist, waere ein Dialog,
     * auf den nichts folgt - und der naechste wird dann schneller weggetippt
     * als gelesen.
     */
    private fun askBeforeTheClockRuns() {
        val sensors = getSystemService(SensorManager::class.java)
        val needed = buildList {
            if (sensors?.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null) {
                add(Manifest.permission.BODY_SENSORS)
            }
            if (sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filterNot {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) askForSensors.launch(missing.toTypedArray())
    }

    /**
     * Bei jedem Anzeigen neu und nicht nur beim Start: zwischen zwei Blicken
     * kann ein Training beendet, eine Bestaetigung angekommen oder das Telefon
     * in die Tasche gewandert sein.
     */
    override fun onResume() {
        super.onResume()
        val running = WorkoutStore.load(this)
        showRunning(running)
        showLastLogged()
        showPhoneMissing(running == null)
    }

    /**
     * Der Weg zurueck in ein Training, das noch laeuft.
     *
     * Solange es das gibt, verschwindet die Auswahl darunter. Ein zweites
     * Workout zu starten wuerde das erste stillschweigend wegwerfen - und die
     * Zeit, die dabei verloren geht, ist die einzige, die niemand
     * nachtragen kann.
     *
     * Die Dauer kommt aus der Ablage und nicht vom Dienst: dafuer eigens eine
     * Verbindung aufzubauen waere Aufwand fuer eine Zahl, die ohnehin
     * dortsteht.
     */
    private fun showRunning(session: WorkoutStore.Session?) {
        val button = findViewById<TextView>(R.id.btnResume)

        if (session == null) {
            button.visibility = View.GONE
            setChoiceVisible(true)
            return
        }

        val seconds = WorkoutStore.elapsedSeconds(session)
        button.visibility = View.VISIBLE
        button.text = getString(
            R.string.resume_workout,
            session.sport,
            String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        )
        button.setOnClickListener { startActivity(WorkoutActivity.resumeIntent(this)) }
        setChoiceVisible(false)
    }

    private fun setChoiceVisible(visible: Boolean) {
        val state = if (visible) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvChoose).visibility = state
        findViewById<LinearLayout>(R.id.llSports).visibility = state
    }

    /** Was das Telefon zum letzten Workout gemeldet hat, falls es das tat. */
    private fun showLastLogged() {
        val view = findViewById<TextView>(R.id.tvLogged)
        val entry = LastLogged.get(this)

        if (entry == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = PhoneAckService.summary(this, entry)
    }

    private fun showPhoneMissing(check: Boolean) {
        val notice = findViewById<TextView>(R.id.tvPhoneMissing)

        if (!check) {
            notice.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val nearby = PhoneReach.isPhoneNearby(this@SportChoiceActivity)
            notice.visibility = if (nearby) View.GONE else View.VISIBLE
        }
    }
}
