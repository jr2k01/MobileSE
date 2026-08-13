package com.example.mobilese

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Zugriff auf das Supabase-Backend.
 *
 * Zwei Entwurfsentscheidungen praegen diese Klasse:
 *
 * 1. **Singleton.** Vorher legte jede Activity mit `AppRepository(this)` einen
 *    eigenen Supabase-Client an, und jeder Client bringt einen eigenen
 *    HTTP-Engine-Pool mit. Beim Durchklicken der App entstanden so laufend
 *    neue Verbindungspools, die nie wieder freigegeben wurden. Der Client wird
 *    jetzt einmal pro Prozess erzeugt.
 *
 * 2. **Sammelabfragen statt Schleifen.** Die Bildschirme brauchen fast immer
 *    denselben Datensatz: alle Mitglieder einer Crew mit ihren Aktivitaeten und
 *    Challenges. Frueher wurde das in verschachtelten Schleifen Zeile fuer
 *    Zeile geholt - bei fuenf Mitgliedern und drei Challenges kamen so ueber
 *    fuenfzig aufeinanderfolgende Netzanfragen zusammen. [loadCrewSnapshot]
 *    holt denselben Bestand mit fuenf Abfragen, davon drei parallel.
 *
 * Alle Netz- und Dateizugriffe laufen auf [Dispatchers.IO]. Die Aufrufer
 * starten aus dem lifecycleScope, also im Main-Thread, und duerfen dort weder
 * JSON dekodieren noch Dateien lesen.
 */
class AppRepository private constructor(context: Context) {

    companion object {
        /**
         * Projekt-URL und oeffentlicher anon-Key. Der anon-Key ist zur
         * Veroeffentlichung in Clients vorgesehen; der Datenschutz haengt an
         * den Row-Level-Security-Regeln in Supabase, nicht an seiner
         * Geheimhaltung. Ein Service-Role-Key duerfte hier niemals stehen.
         */
        private const val SUPABASE_URL = "https://ghhtaaoedlvhipmnuziu.supabase.co"
        private const val SUPABASE_ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdoaHRhYW9lZGx2aGlwbW51eml1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYzODQ3NjUsImV4cCI6MjEwMTk2MDc2NX0.YVXEZgVJpkSTd7Ms5LrwmQgD0cUFBvfzYykYe9KILhg"

        private const val PREFS_NAME = "CrewFitDatabase"
        private const val KEY_SESSION_USER = "current_session_user"
        private const val KEY_JOINED_CREW = "user_joined_crew_code"

        @Volatile
        private var instance: AppRepository? = null

        /** Liefert die prozessweit einzige Instanz. */
        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client: SupabaseClient = createSupabaseClient(SUPABASE_URL, SUPABASE_ANON_KEY) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        // Realtime war installiert, wurde aber nirgends verwendet. Das Modul
        // haelt eine WebSocket-Verbindung offen und kostet Akku, ohne dass ein
        // Bildschirm auf Live-Updates hoert.
    }

    // --- SESSION ---

    /**
     * Die Benutzer-ID der laufenden Sitzung, oder null wenn niemand angemeldet ist.
     *
     * [Auth.awaitInitialization] ist hier entscheidend: Supabase stellt eine
     * gespeicherte Sitzung beim Start asynchron wieder her. Wer direkt nach dem
     * Kaltstart `currentUserOrNull()` abfragt, bekommt null zurueck, obwohl der
     * Nutzer angemeldet ist. Genau daran scheiterten frueher die ersten
     * Schreibzugriffe nach dem App-Start - sie brachen still ab, weil keine ID
     * ermittelt werden konnte.
     */
    suspend fun currentUserId(): String? = withContext(Dispatchers.IO) {
        try {
            client.auth.awaitInitialization()
            client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Session check failed: ${e.message}")
            null
        }
    }

    /**
     * Prueft, ob eine gueltige Sitzung besteht, und raeumt den lokalen
     * Sitzungsmarker auf, wenn das Token abgelaufen oder ungueltig ist.
     * Ohne diesen Abgleich landete man mit einer toten Sitzung im Hauptmenue,
     * wo dann jeder Schreibzugriff wirkungslos blieb.
     */
    suspend fun hasValidSession(): Boolean {
        if (getCurrentUser() == null) return false
        if (currentUserId() != null) return true
        clearLocalSession()
        return false
    }

    fun setCurrentUser(email: String) =
        prefs.edit().putString(KEY_SESSION_USER, email).apply()

    fun getCurrentUser(): String? = prefs.getString(KEY_SESSION_USER, null)

    fun getJoinedCrewCode(): String? = prefs.getString(KEY_JOINED_CREW, null)

    fun setJoinedCrewCode(code: String?) =
        prefs.edit().putString(KEY_JOINED_CREW, code).apply()

    private fun clearLocalSession() {
        prefs.edit().remove(KEY_SESSION_USER).remove(KEY_JOINED_CREW).apply()
    }

    // --- AUTH ---

    suspend fun registerUser(
        email: String,
        password: String,
        name: String,
        birthDate: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val userInfo = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val userId = userInfo?.id
            if (userId == null) {
                // Tritt auf, wenn in Supabase "Confirm Email" aktiv ist: das
                // Konto existiert, eine Sitzung gibt es aber erst nach der
                // Bestaetigung per Mail.
                Log.w("SupabaseAuth", "Registered without session, email confirmation is likely enabled")
                return@withContext false
            }

            try {
                client.postgrest["profiles"].insert(
                    UserProfile(id = userId, email = email, name = name, birthdate = birthDate)
                )
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Profile creation failed: ${e.message}. Check the RLS policies.")
            }

            setCurrentUser(email)
            true
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Registration failed: ${e.message}")
            false
        }
    }

    suspend fun loginUser(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = client.auth.currentUserOrNull() ?: return@withContext false
            setCurrentUser(user.email ?: email)
            cacheJoinedCrew(user.id)
            true
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Login failed: ${e.message}")
            false
        }
    }

    /**
     * Meldet den Nutzer ab. Neben dem lokalen Sitzungsmarker muss auch die
     * Supabase-Sitzung beendet werden - sonst bleibt das Auth-Token auf dem
     * Geraet gueltig und die naechste Abfrage liefert weiterhin die ID des
     * abgemeldeten Nutzers.
     */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                client.auth.signOut()
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Sign out failed: ${e.message}")
            }
        }
        clearLocalSession()
    }

    /**
     * Loescht alle Daten des angemeldeten Nutzers.
     *
     * Die Reihenfolge ist wichtig: erst die hochgeladenen Dateien und die
     * abhaengigen Zeilen, danach das Profil. Umgekehrt waeren die Storage-Pfade
     * nicht mehr zu ermitteln.
     *
     * Der Auth-Benutzer selbst laesst sich vom Client aus nicht loeschen, das
     * verlangt den Service-Role-Key und muesste serverseitig passieren
     * (Edge Function). Siehe Future Work in der Dokumentation.
     */
    suspend fun deleteUserProfile() {
        val userId = currentUserId() ?: return

        withContext(Dispatchers.IO) {
            val ownActivities = try {
                client.postgrest["activities"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<Activity>()
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Could not read activities before deletion: ${e.message}")
                emptyList()
            }

            for (activity in ownActivities) {
                deleteFileByPublicUrl("photos", activity.photoUrl)
                deleteFileByPublicUrl("voice_notes", activity.voiceUrl)
            }

            deleteFileByPublicUrl("avatars", getProfileById(userId)?.avatarUrl)

            deleteRows("activities", "user_id", userId)
            deleteRows("crew_members", "user_id", userId)
            deleteRows("challenge_rewards", "user_id", userId)
            deleteRows("profiles", "id", userId)
        }

        logout()
    }

    // --- PROFILE ---

    suspend fun getProfile(email: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["profiles"].select {
                filter { eq("email", email) }
                limit(1)
            }.decodeSingleOrNull<UserProfile>()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load profile for $email: ${e.message}")
            null
        }
    }

    private suspend fun getProfileById(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["profiles"].select {
                filter { eq("id", userId) }
                limit(1)
            }.decodeSingleOrNull<UserProfile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(
        email: String,
        name: String,
        age: String,
        height: String,
        weight: String,
        birthDate: String
    ): Boolean {
        val userId = currentUserId() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                // Das vorhandene Profil wird zuerst gelesen, damit ein bereits
                // gesetztes Avatar-Bild beim Speichern nicht verloren geht -
                // upsert ersetzt die komplette Zeile.
                val existing = getProfileById(userId)
                client.postgrest["profiles"].upsert(
                    UserProfile(
                        id = userId,
                        email = email,
                        name = name,
                        age = age,
                        height = height,
                        weight = weight,
                        birthdate = birthDate,
                        avatarUrl = existing?.avatarUrl
                    )
                )
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the profile failed: ${e.message}")
                false
            }
        }
    }

    /** Laedt ein neues Profilbild hoch und hinterlegt dessen URL. */
    suspend fun saveUserImage(localPath: String): Boolean {
        val userId = currentUserId() ?: return false
        val cloudUrl = uploadImage("avatars", localPath)
        if (cloudUrl.isEmpty()) return false

        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["profiles"].update({
                    UserProfile::avatarUrl setTo cloudUrl
                }) {
                    filter { eq("id", userId) }
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the avatar URL failed: ${e.message}")
                false
            }
        }
    }

    // --- CREWS ---

    /**
     * Legt eine Crew an. Der Code ist zugleich der Primaerschluessel, ein
     * bereits vergebener Code laesst den Insert fehlschlagen. Das Ergebnis wird
     * zurueckgegeben, damit die UI keinen Erfolg meldet, wenn der Nutzer in
     * Wahrheit einer fremden Crew mit demselben Code beigetreten waere.
     */
    suspend fun createCrew(crewName: String, code: String): Boolean {
        val userId = currentUserId() ?: return false
        val created = withContext(Dispatchers.IO) {
            try {
                client.postgrest["crews"].insert(Crew(id = code, name = crewName, creatorId = userId))
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Creating the crew failed: ${e.message}")
                false
            }
        }
        return created && joinCrew(code)
    }

    suspend fun joinCrew(code: String): Boolean {
        val userId = currentUserId() ?: return false
        return withContext(Dispatchers.IO) {
            if (!crewExists(code)) {
                Log.d("SupabaseDB", "Join rejected, no crew with code $code")
                return@withContext false
            }
            try {
                client.postgrest["crew_members"].insert(CrewMember(crewId = code, userId = userId))
                setJoinedCrewCode(code)
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Joining the crew failed: ${e.message}")
                false
            }
        }
    }

    private suspend fun crewExists(code: String): Boolean = try {
        client.postgrest["crews"].select {
            filter { eq("id", code) }
            limit(1)
        }.decodeList<Crew>().isNotEmpty()
    } catch (e: Exception) {
        false
    }

    suspend fun getCrewName(code: String): String = withContext(Dispatchers.IO) {
        try {
            client.postgrest["crews"].select {
                filter { eq("id", code) }
                limit(1)
            }.decodeSingleOrNull<Crew>()?.name ?: "Unknown Crew"
        } catch (e: Exception) {
            "Unknown Crew"
        }
    }

    suspend fun leaveCrew(code: String): Boolean {
        val userId = currentUserId() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["crew_members"].delete {
                    filter {
                        eq("crew_id", code)
                        eq("user_id", userId)
                    }
                }
                setJoinedCrewCode(null)
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Leaving the crew failed: ${e.message}")
                false
            }
        }
    }

    /**
     * Holt den kompletten Datenbestand einer Crew.
     *
     * Die drei voneinander unabhaengigen Abfragen laufen parallel, die beiden
     * abhaengigen (Profile brauchen die Mitglieder-IDs, Belohnungen die
     * Challenge-IDs) danach. Statt einer langen Kette einzelner Aufrufe bleiben
     * so zwei Wartezeiten uebrig.
     */
    suspend fun loadCrewSnapshot(crewCode: String): CrewSnapshot = withContext(Dispatchers.IO) {
        coroutineScope {
            val memberIdsAsync = async { selectMemberIds(crewCode) }
            val activitiesAsync = async { selectCrewActivities(crewCode) }
            val challengesAsync = async { selectCrewChallenges(crewCode) }

            val memberIds = memberIdsAsync.await()
            val challenges = challengesAsync.await()

            val profilesAsync = async { selectProfiles(memberIds) }
            val rewardsAsync = async { selectRewards(challenges.map { it.id }) }

            CrewSnapshot(
                members = profilesAsync.await(),
                activities = activitiesAsync.await(),
                challenges = challenges,
                rewards = rewardsAsync.await()
            )
        }
    }

    /**
     * Nur die Mitglieder einer Crew - fuer Bildschirme, die keine Aktivitaeten
     * und Challenges brauchen. Zwei Abfragen statt der fuenf eines vollen
     * Snapshots.
     */
    suspend fun getCrewMembers(crewCode: String): List<UserProfile> = withContext(Dispatchers.IO) {
        selectProfiles(selectMemberIds(crewCode))
    }

    private suspend fun selectMemberIds(crewCode: String): List<String> = try {
        client.postgrest["crew_members"].select {
            filter { eq("crew_id", crewCode) }
        }.decodeList<CrewMember>().map { it.userId }
    } catch (e: Exception) {
        Log.e("SupabaseDB", "Could not load crew members: ${e.message}")
        emptyList()
    }

    private suspend fun selectProfiles(userIds: List<String>): List<UserProfile> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["profiles"].select {
                filter { isIn("id", userIds) }
            }.decodeList<UserProfile>()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load profiles: ${e.message}")
            emptyList()
        }
    }

    private suspend fun selectCrewActivities(crewCode: String): List<Activity> = try {
        client.postgrest["activities"].select {
            filter { eq("crew_id", crewCode) }
        }.decodeList<Activity>()
    } catch (e: Exception) {
        Log.e("SupabaseDB", "Could not load crew activities: ${e.message}")
        emptyList()
    }

    private suspend fun selectCrewChallenges(crewCode: String): List<Challenge> = try {
        client.postgrest["challenges"].select {
            filter { eq("crew_id", crewCode) }
        }.decodeList<Challenge>()
            // Feste Reihenfolge: die ID ist der Anlagezeitpunkt in Millisekunden.
            // Vorher wurde ein Set zurueckgegeben, wodurch die Challenges bei
            // jedem Neuladen in anderer Reihenfolge auf dem Bildschirm standen.
            .sortedBy { it.id }
    } catch (e: Exception) {
        Log.e("SupabaseDB", "Could not load challenges: ${e.message}")
        emptyList()
    }

    private suspend fun selectRewards(challengeIds: List<String>): List<ChallengeReward> {
        if (challengeIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["challenge_rewards"].select {
                filter { isIn("challenge_id", challengeIds) }
            }.decodeList<ChallengeReward>()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load challenge rewards: ${e.message}")
            emptyList()
        }
    }

    private suspend fun cacheJoinedCrew(userId: String) {
        try {
            val membership = client.postgrest["crew_members"].select {
                filter { eq("user_id", userId) }
                limit(1)
            }.decodeList<CrewMember>().firstOrNull()
            setJoinedCrewCode(membership?.crewId)
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not determine crew membership: ${e.message}")
        }
    }

    // --- AKTIVITAETEN ---

    suspend fun addActivity(
        sport: String,
        photoPath: String,
        location: String,
        duration: String,
        voicePath: String = "",
        distance: String = "0",
        intensity: String = WorkoutIntensity.MEDIUM.name
    ): Boolean {
        val userId = currentUserId() ?: return false
        val crewId = getJoinedCrewCode() ?: "no_crew"

        return withContext(Dispatchers.IO) {
            try {
                // Foto und Sprachnotiz gleichzeitig hochladen statt nacheinander.
                val uploads = coroutineScope {
                    val photo = async { uploadImage("photos", photoPath) }
                    val voice = async { uploadFile("voice_notes", voicePath) }
                    photo.await() to voice.await()
                }

                client.postgrest["activities"].insert(
                    Activity(
                        userId = userId,
                        crewId = crewId,
                        sport = sport,
                        duration = duration.toIntOrNull() ?: 0,
                        distance = distance.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        location = location,
                        voiceUrl = uploads.second,
                        photoUrl = uploads.first,
                        intensity = intensity,
                        // ISO-8601, damit die Sortierung als Text der
                        // chronologischen Reihenfolge entspricht.
                        timestamp = ActivityTime.now()
                    )
                )
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the activity failed: ${e.message}")
                false
            }
        }
    }

    /** Die eigenen Aktivitaeten, neueste zuerst. */
    suspend fun getOwnActivities(): List<Activity> {
        val userId = currentUserId() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["activities"].select {
                    filter { eq("user_id", userId) }
                }.decodeList<Activity>()
                    // Sortiert wird im Client, weil aeltere Datensaetze noch im
                    // deutschen Datumsformat vorliegen koennen und die Datenbank
                    // diese Mischform nicht chronologisch ordnen kann.
                    .sortedByDescending { ActivityTime.sortKey(it.timestamp) }
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Could not load own activities: ${e.message}")
                emptyList()
            }
        }
    }

    // --- CHALLENGES ---

    suspend fun addCrewChallenge(crewCode: String, type: String, goal: Int, reward: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                client.postgrest["challenges"].insert(
                    Challenge(
                        id = System.currentTimeMillis().toString(),
                        crewId = crewCode,
                        type = type,
                        goal = goal,
                        reward = reward
                    )
                )
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Adding the challenge failed: ${e.message}")
                false
            }
        }

    suspend fun deleteCrewChallenge(challengeId: String): Boolean = withContext(Dispatchers.IO) {
        val deleted = try {
            client.postgrest["challenges"].delete {
                filter { eq("id", challengeId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Deleting the challenge failed: ${e.message}")
            false
        }

        // Die Belohnungen der Challenge mitloeschen, sonst zaehlen ihre Punkte
        // in der Rangliste weiter, obwohl die Challenge weg ist.
        //
        // Bewusst in einem eigenen try-Block: fehlt fuer challenge_rewards eine
        // DELETE-Regel in den RLS-Einstellungen, schlaegt nur das Aufraeumen
        // fehl. Die Challenge ist dann trotzdem geloescht, und der Nutzer darf
        // keine Fehlermeldung fuer etwas bekommen, das funktioniert hat.
        deleteRows("challenge_rewards", "challenge_id", challengeId)

        deleted
    }

    /**
     * Schreibt die Belohnungen einer abgeschlossenen Challenge.
     *
     * Alle Eintraege gehen in einem einzigen Insert raus statt in einem Aufruf
     * pro Person. Die tatsaechlich vergebene Punktzahl steht im Eintrag selbst,
     * damit sie sich nicht aendert, wenn die Crew spaeter waechst oder
     * schrumpft.
     */
    suspend fun awardChallenge(award: PendingAward): Boolean = withContext(Dispatchers.IO) {
        try {
            client.postgrest["challenge_rewards"].insert(
                award.userIds.map { ChallengeReward(award.challengeId, it, award.pointsPerUser) }
            )
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Awarding the challenge failed: ${e.message}")
            false
        }
    }

    // --- STORAGE ---

    /**
     * Laedt ein Bild verkleinert hoch. Kamerafotos sind schnell mehrere
     * Megabyte gross; ohne die Verkleinerung wandert das Original ins Netz und
     * muss von jedem Crew-Mitglied wieder heruntergeladen werden.
     */
    private suspend fun uploadImage(bucket: String, localPath: String): String =
        withContext(Dispatchers.IO) {
            if (localPath.isEmpty()) return@withContext ""
            // Laesst sich die Datei nicht als Bild lesen, wandert sie unveraendert hoch.
            val bytes = ImageLoader.compressForUpload(localPath)
                ?: return@withContext uploadFile(bucket, localPath)
            putBytes(bucket, File(localPath).name, bytes)
        }

    private suspend fun uploadFile(bucket: String, localPath: String): String =
        withContext(Dispatchers.IO) {
            if (localPath.isEmpty()) return@withContext ""
            val file = File(localPath)
            if (!file.exists()) return@withContext ""
            putBytes(bucket, file.name, file.readBytes())
        }

    private suspend fun putBytes(bucket: String, name: String, bytes: ByteArray): String {
        val remoteName = "${System.currentTimeMillis()}_$name"
        return try {
            client.storage[bucket].upload(remoteName, bytes)
            client.storage[bucket].publicUrl(remoteName)
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Upload to $bucket failed: ${e.message}")
            ""
        }
    }

    /**
     * Entfernt eine Datei aus einem Bucket anhand ihrer oeffentlichen URL.
     * Der Dateiname ist das letzte Pfadsegment.
     */
    private suspend fun deleteFileByPublicUrl(bucket: String, publicUrl: String?) {
        if (publicUrl.isNullOrEmpty() || !publicUrl.startsWith("http")) return
        val fileName = publicUrl.substringAfterLast('/')
        if (fileName.isEmpty()) return
        try {
            client.storage[bucket].delete(fileName)
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Deleting $fileName from $bucket failed: ${e.message}")
        }
    }

    private suspend fun deleteRows(table: String, column: String, value: String) {
        try {
            client.postgrest[table].delete {
                filter { eq(column, value) }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Deleting from $table failed: ${e.message}")
        }
    }
}

/**
 * Der Datenbestand einer Crew zu einem Zeitpunkt. Alle Bildschirme rechnen
 * ihre Anzeige aus diesem Objekt aus, statt einzeln nachzufragen.
 */
data class CrewSnapshot(
    val members: List<UserProfile>,
    val activities: List<Activity>,
    val challenges: List<Challenge>,
    val rewards: List<ChallengeReward>
)
