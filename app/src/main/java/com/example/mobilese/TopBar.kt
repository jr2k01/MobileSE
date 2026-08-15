package com.example.mobilese

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity

/**
 * Richtet die gemeinsame Kopfzeile [R.layout.part_top_bar] ein.
 *
 * Jeder Unterbildschirm hatte vorher seine eigene Kopfzeile mit eigener
 * Zurueck-Schaltflaeche und eigener Titel-ID. Das war fuenfmal derselbe
 * Aufbau in leicht unterschiedlichen Abstaenden - hier steht er einmal.
 */
fun AppCompatActivity.setUpTopBar(@StringRes titleRes: Int) {
    findViewById<TextView>(R.id.tvTopBarTitle).setText(titleRes)
    findViewById<View>(R.id.btnTopBarBack).setOnClickListener { finish() }
}
