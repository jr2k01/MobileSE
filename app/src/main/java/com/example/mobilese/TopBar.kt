package com.example.mobilese

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Richtet die gemeinsame Kopfzeile [R.layout.part_top_bar] ein.
 *
 * Jeder Unterbildschirm hatte vorher seine eigene Kopfzeile mit eigener
 * Zurueck-Schaltflaeche und eigener Titel-ID. Das war fuenfmal derselbe
 * Aufbau in leicht unterschiedlichen Abstaenden - hier steht er einmal.
 *
 * @param actionIcon Zeichen fuer eine Aktion rechts, oder 0 fuer keine. Nur
 *        wer eine angibt, bekommt sie - sonst haetten alle Unterseiten einen
 *        Knopf, der nichts tut.
 */
fun AppCompatActivity.setUpTopBar(
    @StringRes titleRes: Int,
    @DrawableRes actionIcon: Int = 0,
    @StringRes actionDescription: Int = 0,
    onAction: (() -> Unit)? = null
) {
    findViewById<TextView>(R.id.tvTopBarTitle).setText(titleRes)
    findViewById<View>(R.id.btnTopBarBack).setOnClickListener { finish() }

    val action = findViewById<MaterialButton>(R.id.btnTopBarAction) ?: return
    if (actionIcon == 0 || onAction == null) {
        action.visibility = View.GONE
        return
    }

    action.visibility = View.VISIBLE
    action.setIconResource(actionIcon)
    if (actionDescription != 0) action.contentDescription = getString(actionDescription)
    action.setOnClickListener { onAction() }
}

/**
 * Setzt statt eines festen Titels das eigene Profil in die Kopfzeile.
 *
 * Der "Me"-Reiter ist der einzige Bildschirm, der von einer bestimmten Person
 * handelt - der eigenen. Dort "Me" zu schreiben sagt weniger als das eigene
 * Bild und der eigene Name, und es ist derselbe Kopf, den die anderen von
 * einem sehen.
 *
 * Fehlt das Bild, bleibt der Kreis mit dem Platzhalter stehen: er haelt die
 * Stelle, an der das Bild sein wird, sobald eines hochgeladen ist. Ist auch
 * kein Name gesetzt, bleibt der uebergebene Titel stehen - eine leere
 * Kopfzeile waere schlechter als ein allgemeines Wort.
 */
fun AppCompatActivity.showProfileInTopBar(profile: UserProfile?) {
    val holder = findViewById<View>(R.id.cvTopBarAvatar) ?: return
    if (profile == null) return

    holder.visibility = View.VISIBLE
    ImageLoader.into(
        findViewById(R.id.ivTopBarAvatar),
        profile.avatarUrl,
        circular = true,
        placeholder = R.drawable.ic_image
    )

    val name = DisplayName.of(profile)
    if (name.isNotBlank()) findViewById<TextView>(R.id.tvTopBarTitle).text = name
}
