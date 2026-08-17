package com.example.mobilese

import android.media.MediaPlayer
import android.util.Log

/**
 * Spielt die Sprachnotiz zu einem Workout ab.
 *
 * Steht hier einmal, weil zwei Bildschirme sie brauchen - die letzten
 * Aktivitaeten auf dem Startbildschirm und die Einzelansicht eines Workouts.
 *
 * Sprachnotizen liegen als URL vor. prepare() wuerde dafuer synchron im
 * Main-Thread auf das Netz warten und einen ANR riskieren, deshalb
 * prepareAsync() mit Rueckruf.
 *
 * Der Aufrufer muss [release] beim Verlassen des Bildschirms aufrufen: ein
 * laufender MediaPlayer haelt sonst Ressourcen und spielt weiter, obwohl nichts
 * mehr zu sehen ist.
 */
class VoicePlayer(private val onError: () -> Unit) {

    private var player: MediaPlayer? = null

    fun play(url: String) {
        // Ein zweiter Antipper soll nicht zwei Aufnahmen uebereinander legen.
        release()

        player = MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, what, extra ->
                Log.e("Audio", "Playback error $what/$extra")
                onError()
                true
            }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (e: Exception) {
                Log.e("Audio", "Could not set data source: ${e.message}")
                onError()
            }
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
