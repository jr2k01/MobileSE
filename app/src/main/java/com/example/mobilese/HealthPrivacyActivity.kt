package com.example.mobilese

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Erklaert, wofuer CrewFit die Schrittzahl aus Health Connect liest.
 *
 * Health Connect verlangt, dass jede App diese Auskunft geben kann, und
 * verlinkt aus seiner eigenen Oberflaeche hierher - beim Erteilen der Erlaubnis
 * und spaeter in der Uebersicht der zugreifenden Apps. Ohne diesen Bildschirm
 * liefe der Link ins Leere.
 *
 * Deshalb ist die Activity als einzige neben dem Anmeldebildschirm von aussen
 * aufrufbar. Sie zeigt nur Text und nimmt nichts entgegen.
 */
class HealthPrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_health_privacy)
        setUpTopBar(R.string.health_privacy_title)
    }
}
