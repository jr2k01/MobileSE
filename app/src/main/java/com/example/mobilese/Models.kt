package com.example.mobilese

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val age: String? = null,
    val height: String? = null,
    val weight: String? = null,
    val birthdate: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    // Das selbst gewaehlte Kuerzel fuer Rangliste und Crew-Uebersicht. Der
    // Standardwert laesst aeltere Zeilen ohne diese Spalte weiterhin lesen.
    @SerialName("display_name") val displayName: String? = null
)

/**
 * Dasselbe Profil ohne das Kuerzel.
 *
 * Aus demselben Grund wie [ActivityWithoutCoordinates]: fehlt die Spalte
 * display_name in der Datenbank, weist Postgrest ein Upsert ab, das sie nennt -
 * auch mit null als Wert. Ohne diesen Rueckfall liesse sich in einem solchen
 * Projekt das Profil ueberhaupt nicht mehr speichern, also auch kein Name und
 * kein Geburtsdatum.
 *
 * Sobald die Spalte ueberall angelegt ist, kann diese Klasse entfallen.
 */
@Serializable
data class UserProfileWithoutDisplayName(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val age: String? = null,
    val height: String? = null,
    val weight: String? = null,
    val birthdate: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class Crew(
    val id: String,
    val name: String,
    @SerialName("creator_id") val creatorId: String
)

@Serializable
data class CrewMember(
    @SerialName("crew_id") val crewId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
data class Activity(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("crew_id") val crewId: String,
    val sport: String,
    val duration: Int,
    val distance: Double = 0.0,
    val location: String? = null,
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val intensity: String,
    val timestamp: String,
    // Koordinaten des Orts, damit die Historie eine Karte zeigen kann. Die
    // Standardwerte sorgen dafuer, dass aeltere Zeilen ohne diese Spalten
    // weiterhin gelesen werden koennen.
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Dieselbe Aktivitaet ohne Koordinatenfelder.
 *
 * Wird nur gebraucht, wenn in der Datenbank die Spalten latitude und longitude
 * noch fehlen: Postgrest weist einen Insert ab, der unbekannte Spalten nennt,
 * und ein null-Wert wuerde trotzdem als Spaltenname mitgeschickt. Ohne diesen
 * Rueckfall koennte in einem solchen Projekt gar kein Workout mehr gespeichert
 * werden.
 *
 * Sobald die Spalten ueberall angelegt sind, kann diese Klasse entfallen.
 */
@Serializable
data class ActivityWithoutCoordinates(
    @SerialName("user_id") val userId: String,
    @SerialName("crew_id") val crewId: String,
    val sport: String,
    val duration: Int,
    val distance: Double = 0.0,
    val location: String? = null,
    @SerialName("voice_url") val voiceUrl: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val intensity: String,
    val timestamp: String
)

@Serializable
data class Challenge(
    val id: String,
    @SerialName("crew_id") val crewId: String,
    val type: String,
    val goal: Int,
    val reward: Int = 0
)

@Serializable
data class ChallengeReward(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("user_id") val userId: String,
    // Die tatsaechlich ausgeschuettete Punktzahl wird mitgespeichert, damit sie
    // spaeter nicht aus der aktuellen Crew-Groesse rekonstruiert werden muss.
    // Diese haette sich zwischen Ausschuettung und Abfrage aendern koennen.
    val points: Int = 0
)
