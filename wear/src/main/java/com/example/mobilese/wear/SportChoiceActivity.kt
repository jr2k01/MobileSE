package com.example.mobilese.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Der Startbildschirm der Uhr: Sportart waehlen, dann laeuft es los.
 *
 * Kein Anmelden, keine Crew, keine Rangliste - die Uhr macht genau eine Sache.
 * Alles, was ein Konto braucht, passiert auf dem Telefon; die Uhr schickt nur
 * hin, was sie gemessen hat.
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
}
