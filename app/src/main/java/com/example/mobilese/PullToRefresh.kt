package com.example.mobilese

import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Herunterziehen laedt den Bildschirm neu.
 *
 * Jeder Bildschirm holte seine Daten bisher nur beim Oeffnen und beim
 * Zurueckkommen. Wer die App offen liegen laesst, waehrend jemand anderes ein
 * Workout eintraegt, sieht davon nichts - und es gibt keine Live-Verbindung
 * zum Backend, die es ihm sagen wuerde: Supabase Realtime wurde bewusst
 * entfernt, weil dafuer dauernd eine Verbindung offen stehen muesste.
 *
 * Die Geste ist die Antwort darauf: sie kostet nichts, solange niemand zieht,
 * und sie ist das, wonach man auf einem Telefon ohnehin greift.
 *
 * Der Bildschirm muss dafuer eine [SwipeRefreshLayout] mit der Kennung
 * `swipeRefresh` um seinen scrollenden Bereich haben.
 */
fun AppCompatActivity.setUpPullToRefresh(onRefresh: () -> Unit) {
    val layout = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh) ?: return

    // Farben aus dem Thema, nicht fest eingetragen: der Kreis liegt sonst im
    // dunklen Erscheinungsbild als heller Fleck auf dunklem Grund.
    layout.setColorSchemeColors(themeColor(androidx.appcompat.R.attr.colorPrimary))
    layout.setProgressBackgroundColorSchemeColor(
        themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    )

    layout.setOnRefreshListener { onRefresh() }
}

/**
 * Beendet den sich drehenden Kreis.
 *
 * Muss auch dann gerufen werden, wenn das Laden schiefgegangen ist - sonst
 * dreht sich der Kreis weiter und behauptet, es passiere noch etwas.
 */
fun AppCompatActivity.finishRefreshing() {
    findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)?.isRefreshing = false
}

private fun AppCompatActivity.themeColor(attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
