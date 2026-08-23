package com.example.mobilese.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_sport_choice)

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
