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
    @SerialName("creator_id") val creatorId: String,
    /**
     * Das Bild der Crew, das Gegenstueck zum Profilbild.
     *
     * Der Standardwert laesst aeltere Zeilen ohne diese Spalte weiterhin lesen.
     * Beim Anlegen einer Crew steht hier null, und weil kotlinx mit
     * `encodeDefaults = false` arbeitet, wird die Spalte dann gar nicht erst
     * mitgeschickt - ein Projekt ohne sie kann also weiterhin Crews anlegen.
     */
    @SerialName("image_url") val imageUrl: String? = null
)

/**
 * Ob ein Profil in der Suche auftaucht.
 *
 * Eigenes Modell und nicht ein Feld in [UserProfile]: das Profil wird per
 * Upsert als Ganzes geschrieben, und ein `false` waere von seinem Standardwert
 * verschieden und wuerde deshalb mitgeschickt. In einem Projekt ohne die Spalte
 * liesse sich das Profil dann gar nicht mehr speichern. So beruehrt die
 * Sichtbarkeit nur ihre eigene Spalte.
 */
@Serializable
data class ProfileVisibility(
    val id: String,
    @SerialName("is_public") val isPublic: Boolean = true
)

/**
 * Eine offene Bitte, in eine Crew aufgenommen zu werden.
 *
 * Eine Zeile je Crew und Person, deshalb kann dieselbe Anfrage nicht zweimal
 * offen sein. Angenommene und abgelehnte Anfragen werden geloescht statt als
 * erledigt markiert: die Tabelle enthaelt damit nur, was noch offen ist.
 */
@Serializable
data class CrewJoinRequest(
    @SerialName("crew_id") val crewId: String,
    @SerialName("user_id") val userId: String
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
    val longitude: Double? = null,
    // Puls aus Health Connect fuer den Zeitraum des Workouts, also das, was
    // eine Uhr aufgezeichnet hat. Null, wenn keine getragen wurde oder der
    // Zugriff nicht erlaubt ist.
    @SerialName("avg_heart_rate") val avgHeartRate: Int? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null
)

/**
 * Dieselbe Aktivitaet ohne die Pulswerte.
 *
 * Aus demselben Grund wie [ActivityWithoutCoordinates]: fehlen die Spalten in
 * der Datenbank, weist Postgrest einen Insert ab, der sie nennt. Ein Workout
 * darf daran nicht scheitern - es wird dann ohne Puls gespeichert.
 *
 * Sobald die Spalten ueberall angelegt sind, kann diese Klasse entfallen.
 */
@Serializable
data class ActivityWithoutHeartRate(
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

/**
 * Die Schrittzahl eines Nutzers an einem Tag.
 *
 * Eine Zeile je Nutzer und Tag, deshalb kann derselbe Tag nicht zweimal
 * zaehlen. Aus diesen Zeilen ergeben sich beide Anzeigen: der Ring in der
 * Rangliste (der Tag von heute) und die Bonuspunkte (alle erreichten Tage).
 */
@Serializable
data class StepDay(
    @SerialName("user_id") val userId: String,
    /** Der Tag als ISO-Datum, etwa "2026-08-17". */
    val day: String,
    val steps: Int = 0
)

/**
 * Das Bild, das die Nummer eins der Crew aufhaengt.
 *
 * Eine Zeile je Crew - der Schluessel ist die Crew, nicht der Nutzer. Ein neues
 * Bild ersetzt damit das alte, ohne dass irgendwo aufgeraeumt werden muss, und
 * es kann nie zwei gleichzeitig geben.
 *
 * Es bleibt haengen, bis der naechste Fuehrende eines hochlaedt. Beim
 * Rangwechsel zu verschwinden waere strenger, wuerde die Flaeche aber nach
 * jedem Ueberholen leeren.
 */
@Serializable
data class CrewMeme(
    @SerialName("crew_id") val crewId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("image_url") val imageUrl: String,
    val caption: String? = null
)

/**
 * Ein Bild aus der vorgegebenen Auswahl.
 *
 * Kein Datenbankmodell: die Auswahl steht nicht in einer Tabelle, sondern ist
 * schlicht der Inhalt eines Ordners im Bucket. [name] ist der Dateiname und
 * dient nur als Beschriftung fuer die Sprachausgabe.
 */
data class MemePreset(val name: String, val url: String)

@Serializable
data class Challenge(
    val id: String,
    @SerialName("crew_id") val crewId: String,
    val type: String,
    val goal: Int,
    val reward: Int = 0,
    /**
     * Letzter Tag, an dem noch etwas zaehlt, als ISO-Datum ("2026-08-31").
     * Null heisst: ohne Frist, die Challenge laeuft weiter.
     *
     * Der Standardwert laesst aeltere Zeilen ohne diese Spalte weiterhin lesen.
     */
    val deadline: String? = null
)

/**
 * Dieselbe Challenge ohne die Frist.
 *
 * Aus demselben Grund wie [ActivityWithoutCoordinates]: fehlt die Spalte
 * deadline in der Datenbank, weist Postgrest ein Insert ab, das sie nennt.
 * Ohne diese Fassung liesse sich dann gar keine Challenge mehr anlegen.
 */
@Serializable
data class ChallengeWithoutDeadline(
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

/**
 * Die Push-Kennung eines Geraets.
 *
 * Der Schluessel ist die Kennung, nicht der Nutzer: wer auf Telefon und Tablet
 * angemeldet ist, soll auf beiden benachrichtigt werden. Meldet sich auf einem
 * Geraet jemand anderes an, wandert die Zeile per Upsert zum neuen Konto.
 */
@Serializable
data class DeviceToken(
    val token: String,
    @SerialName("user_id") val userId: String
)

/**
 * Eine Reaktion auf ein Workout.
 *
 * Eine Zeile je Person und Aktivitaet, deshalb ersetzt ein zweites Zeichen das
 * erste. Wer dasselbe noch einmal antippt, nimmt es zurueck - dann verschwindet
 * die Zeile.
 */
@Serializable
data class ActivityReaction(
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String
)

/**
 * Ein Kommentar unter einem Workout.
 *
 * [id] und [createdAt] vergibt die Datenbank; beim Schreiben stehen sie
 * deshalb auf null und werden - weil kotlinx mit `encodeDefaults = false`
 * arbeitet - gar nicht erst mitgeschickt.
 */
@Serializable
data class ActivityComment(
    val id: String? = null,
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
    val text: String,
    @SerialName("created_at") val createdAt: String? = null
)

/**
 * Wer wem folgt.
 *
 * Bewusst gerichtet und ohne Bestaetigung: Folgen ist keine Freundschaft, die
 * beide Seiten eingehen, sondern ein Lesezeichen auf eine Person. Wer zurueck
 * folgen will, tut es seinerseits.
 */
@Serializable
data class Follow(
    @SerialName("follower_id") val followerId: String,
    @SerialName("followee_id") val followeeId: String
)
