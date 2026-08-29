package com.example.mobilese

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Die Frage nach der ersten Anmeldung: Fuehrung durch die App, oder gleich los?
 *
 * Steht genau einmal je Konto und Geraet zwischen der Anmeldung und dem ersten
 * Bildschirm der App. Zusammen mit der Zeile in den Einstellungen ist das der
 * einzige Weg in den Tutorialmodus - er faengt nicht mehr von allein an, nur
 * weil jemand einen Bildschirm zum ersten Mal oeffnet.
 *
 * Warum ueberhaupt fragen: eine Fuehrung, die ungefragt losgeht, ist fuer den
 * einen die Rettung und fuer den anderen etwas, das er neunmal wegtippt. Die
 * Frage kostet einen Tipp und beantwortet beides.
 *
 * Der Merker steht bei der Kennung des Kontos und nicht bloss als Ja/Nein: auf
 * einem geteilten Geraet - im Kurs der Normalfall - soll der zweite Anmelder
 * die Frage auch bekommen.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_welcome)

        userId = intent.getStringExtra(EXTRA_USER).orEmpty()

        findViewById<View>(R.id.btnWelcomeStart).setOnClickListener { leave(tour = true) }
        findViewById<View>(R.id.btnWelcomeSkip).setOnClickListener { leave(tour = false) }

        // Zurueck heisst hier dasselbe wie "Not now": ohne das landet man
        // wieder auf der Anmeldung, die einen sofort hierher zurueckschickt.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave(tour = false)
        })
    }

    /**
     * Die Frage ist beantwortet: merken, gegebenenfalls den Tutorialmodus
     * einschalten und weiter in die App.
     */
    private fun leave(tour: Boolean) {
        if (userId.isNotEmpty()) markSeen(this, userId)
        if (tour) CoachTour.begin(this)

        val next = intent.getStringExtra(EXTRA_NEXT)
        if (next != null) startActivity(Intent(this, Class.forName(next)))
        finish()
    }

    companion object {

        private const val PREFS = "welcome"

        /** Die Konten, die die Frage auf diesem Geraet schon hatten. */
        private const val SEEN = "seen"

        private const val EXTRA_USER = "user"
        private const val EXTRA_NEXT = "next"

        /**
         * @param next der Bildschirm, auf dem es danach weitergeht - der
         *        Willkommensbildschirm schiebt sich nur davor, er ersetzt die
         *        Wegwahl der Anmeldung nicht.
         */
        fun intent(context: Context, userId: String, next: Class<out Activity>): Intent =
            Intent(context, WelcomeActivity::class.java)
                .putExtra(EXTRA_USER, userId)
                .putExtra(EXTRA_NEXT, next.name)

        /** Ob dieses Konto die Frage auf diesem Geraet noch vor sich hat. */
        fun pending(context: Context, userId: String): Boolean =
            userId !in seen(context)

        private fun markSeen(context: Context, userId: String) {
            // Die Menge aus den Einstellungen darf nicht veraendert werden -
            // sie ist dieselbe Instanz, die dort weiterliegt.
            val updated = seen(context) + userId
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(SEEN, updated).apply()
        }

        private fun seen(context: Context): Set<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(SEEN, emptySet()) ?: emptySet()
    }
}
