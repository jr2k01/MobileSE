package com.example.mobilese

import android.app.Application

/**
 * Setzt beim Start das gewaehlte Erscheinungsbild.
 *
 * Muss geschehen, bevor die erste Activity ihr Fenster aufbaut - danach waere
 * der Bildschirm schon in der falschen Fassung gezeichnet und wuerde sichtbar
 * umspringen. Die einzige Stelle, die frueh genug laeuft, ist diese.
 *
 * Die App hatte bisher keine eigene Application-Klasse; sie tut auch nur das.
 */
class CrewFitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeMode.apply(AppRepository.get(this).getThemeMode())

        // Die Kanaele muessen stehen, bevor die erste Benachrichtigung kommt.
        // Sie hier anzulegen kostet nichts und erspart die Frage, ob der
        // empfangende Dienst schon einmal lief.
        Notifications.createChannels(this)
    }
}
