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
    }
}
