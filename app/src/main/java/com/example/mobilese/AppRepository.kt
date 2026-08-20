package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

        /**
         * Adresse, unter der die App vom Link aus der Mail erreicht wird:
         * crewfit://reset-password. Steht hier, weil sie an drei Stellen
         * zusammenpassen muss - hier, im intent-filter der
         * ResetPasswordActivity und in den Redirect-URLs des Supabase-Projekts.
         */
        const val DEEPLINK_SCHEME = "crewfit"
        const val DEEPLINK_HOST = "reset-password"

        /**
         * Ziel des Links aus der Bestaetigungsmail einer Registrierung.
         *
         * Muss ausdruecklich gesetzt werden. Sonst nimmt die Bibliothek auch
         * hier den Deeplink - und der zeigt auf den Bildschirm zum Neusetzen
         * des Passworts, der beim Bestaetigen einer Adresse nichts zu suchen
         * hat. Zudem kann ein Mailprogramm ein crewfit://-Ziel nicht oeffnen;
         * bei der Bestaetigung genuegt eine gewoehnliche Webseite, die sagt,
         * dass es geklappt hat.
         *
         * Die Seite liegt als docs/confirmed.html im Projekt und wird ueber
         * GitHub Pages ausgeliefert. Sie muss in Supabase unter den erlaubten
         * Redirect-URLs stehen.
         */
        const val CONFIRM_REDIRECT_URL =
            "https://jr2k01.github.io/MobileSE/confirmed.html"

        /**
         * Bucket fuer das Bild der Nummer eins, und darin der Ordner mit der
         * vorgegebenen Auswahl.
         *
         * Beides im selben Bucket: die Auswahl ist oeffentlich lesbar wie die
         * hochgeladenen Bilder auch, und ein zweiter Bucket haette dieselben
         * Regeln noch einmal gebraucht. Der Ordner unterscheidet sie - was
         * darin liegt, gehoert keiner Crew und wird nie geloescht.
         */
        /** Ab wie vielen Zeichen gesucht wird - kuerzer traefe fast alles. */
        const val SEARCH_MIN_LENGTH = 2
        private const val SEARCH_LIMIT = 25L

        const val MEME_BUCKET = "memes"
        const val MEME_PRESET_FOLDER = "presets"

        private const val PREFS_NAME = "CrewFitDatabase"
        private const val KEY_SESSION_USER = "current_session_user"
        private const val KEY_JOINED_CREW = "user_joined_crew_code"
        private const val KEY_THEME_MODE = "theme_mode"

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
        install(Auth) {
            // Ziel des Links aus der Mail zum Zuruecksetzen des Passworts.
            // Muss zum intent-filter von ResetPasswordActivity passen und in
            // Supabase unter den erlaubten Redirect-URLs stehen, sonst leitet
            // der Server nicht dorthin weiter.
            scheme = DEEPLINK_SCHEME
            host = DEEPLINK_HOST
        }
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

    /**
     * Das gewaehlte Erscheinungsbild.
     *
     * Bleibt bewusst lokal und geht nicht in die Datenbank: es beschreibt
     * dieses Geraet, nicht das Konto. Auf dem Telefon dunkel und auf dem
     * Tablet hell zu wollen, ist kein Widerspruch, den man aufloesen muesste.
     * Ein Abmelden loescht es deshalb auch nicht mit.
     */
    fun getThemeMode(): ThemeMode = ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: ThemeMode) =
        prefs.edit().putString(KEY_THEME_MODE, mode.storedName).apply()


    // --- PUSH ---

    /**
     * Hinterlegt die Push-Kennung dieses Geraets beim angemeldeten Nutzer.
     *
     * Der Schluessel ist die Kennung, nicht der Nutzer: ein Nutzer kann mehrere
     * Geraete haben und soll auf allen benachrichtigt werden. Meldet sich auf
     * demselben Geraet jemand anderes an, wandert die Zeile durch das Upsert
     * mit, statt dass zwei Konten auf dieselbe Kennung zeigen.
     *
     * Fehlt die Tabelle oder ist Firebase nicht eingerichtet, bleibt es beim
     * Protokolleintrag - ohne Push ist die App vollstaendig benutzbar.
     */
    suspend fun savePushToken(token: String): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext false
        try {
            client.postgrest["device_tokens"].upsert(
                DeviceToken(token = token, userId = userId)
            )
            true
        } catch (e: Exception) {
            Log.e("SupabasePush", "Could not store the push token: ${e.message}")
            false
        }
    }

    /**
     * Nimmt die Kennung dieses Geraets wieder aus der Datenbank.
     *
     * Muss beim Abmelden geschehen: sonst bekaeme das Geraet weiterhin die
     * Benachrichtigungen der Crew des vorherigen Nutzers.
     */
    suspend fun deletePushToken(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.postgrest["device_tokens"].delete { filter { eq("token", token) } }
            true
        } catch (e: Exception) {
            Log.e("SupabasePush", "Could not remove the push token: ${e.message}")
            false
        }
    }
    private fun clearLocalSession() {
        prefs.edit().remove(KEY_SESSION_USER).remove(KEY_JOINED_CREW).apply()
    }

    // --- AUTH ---

    /**
     * Legt ein Konto an.
     *
     * Entscheidend ist die Bedeutung des Rueckgabewerts von [Auth.signUpWith]:
     * laut Dokumentation liefert er "the result of the sign-up or **null if
     * auto-confirm is enabled** (resulting in a login)".
     *
     * Das wurde vorher genau verkehrt herum ausgewertet - null galt als Fehler,
     * obwohl es die direkte Anmeldung bedeutet, und ein zurueckgegebenes
     * UserInfo galt als fertige Registrierung, obwohl es gerade heisst, dass
     * die Bestaetigung per Mail noch aussteht. In diesem Fall gibt es noch
     * keine Sitzung.
     *
     * Name und Geburtsdatum wandern deshalb als Metadaten mit zum Konto: ohne
     * Sitzung laesst sich noch keine Zeile in "profiles" schreiben, das
     * verbieten die RLS-Regeln zu Recht. Das Profil entsteht bei der ersten
     * Anmeldung aus diesen Metadaten, siehe [ensureProfileExists].
     */
    suspend fun registerUser(
        email: String,
        password: String,
        name: String,
        birthDate: String
    ): RegistrationResult = withContext(Dispatchers.IO) {
        try {
            val userInfo = client.auth.signUpWith(Email, redirectUrl = CONFIRM_REDIRECT_URL) {
                this.email = email
                this.password = password
                this.data = buildJsonObject {
                    put("name", name)
                    put("birthdate", birthDate)
                }
            }

            if (userInfo != null) {
                // Ist die Adresse schon vergeben, antwortet Supabase trotzdem
                // mit Erfolg - damit sich ueber das Registrierungsformular
                // nicht herausfinden laesst, wer ein Konto hat. Erkennbar ist
                // es nur an der leeren Liste der Identitaeten. Verschickt wird
                // in diesem Fall keine Bestaetigungsmail.
                //
                // Ohne diese Pruefung meldet die App "Bestaetigungsmail
                // unterwegs" und es kommt nie eine an.
                if (userInfo.identities.isNullOrEmpty()) {
                    Log.w("SupabaseAuth", "Sign-up for an address that already exists: $email")
                    return@withContext RegistrationResult.Failed(
                        AuthError.EMAIL_ALREADY_REGISTERED
                    )
                }

                Log.d("SupabaseAuth", "Sign-up accepted, confirmation email sent to $email")
                return@withContext RegistrationResult.ConfirmationRequired
            }

            // Kein UserInfo: die Bestaetigung ist im Projekt abgeschaltet und
            // der Nutzer ist bereits angemeldet.
            val user = client.auth.currentUserOrNull()
                ?: return@withContext RegistrationResult.Failed(AuthError.UNKNOWN)
            setCurrentUser(user.email ?: email)
            ensureProfileExists(user.id, user.email ?: email)
            RegistrationResult.SignedIn
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Registration failed: ${e.message}")
            RegistrationResult.Failed(errorFor(e))
        }
    }

    suspend fun loginUser(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                val user = client.auth.currentUserOrNull()
                    ?: return@withContext LoginResult.Failed(AuthError.UNKNOWN)

                setCurrentUser(user.email ?: email)
                // Bei bestaetigter Registrierung ist dies die erste Sitzung des
                // Nutzers - erst jetzt kann das Profil angelegt werden.
                ensureProfileExists(user.id, user.email ?: email)
                cacheJoinedCrew(user.id)
                LoginResult.Success
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Login failed: ${e.message}")
                LoginResult.Failed(errorFor(e))
            }
        }

    /** Verschickt die Bestaetigungsmail erneut. */
    /**
     * Schickt eine Mail zum Zuruecksetzen des Passworts.
     *
     * Gibt null zurueck, wenn sie unterwegs ist, sonst den Grund.
     *
     * Der Link darin fuehrt ueber den Deeplink zurueck in die App. Dass die
     * Adresse ueberhaupt existiert, wird bewusst nicht geprueft und auch nicht
     * gemeldet: sonst liesse sich ueber dieses Feld herausfinden, wer ein Konto
     * hat. Supabase antwortet aus demselben Grund auch fuer unbekannte
     * Adressen mit Erfolg.
     */
    suspend fun sendPasswordReset(email: String): AuthError? = withContext(Dispatchers.IO) {
        try {
            // Ausdruecklich der Deeplink, auch wenn es hier der Standardwert
            // waere - damit an beiden Stellen sichtbar ist, wohin die jeweilige
            // Mail fuehrt.
            client.auth.resetPasswordForEmail(
                email,
                redirectUrl = "$DEEPLINK_SCHEME://$DEEPLINK_HOST"
            )
            null
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Could not send the password reset mail: ${e.message}")
            errorFor(e)
        }
    }

    /**
     * Loest den Code aus der Mail gegen eine Sitzung ein.
     *
     * Der zweite Weg neben dem Link - und der verlaesslichere. Ein Link auf
     * crewfit:// muss vom Browser an die App uebergeben werden; oeffnet das
     * Mailprogramm ihn in seiner eingebauten Ansicht, kennt die das Schema
     * nicht und es bleibt bei einer leeren Seite. Ein abgetippter Code geht
     * diesen Weg gar nicht erst.
     */
    suspend fun verifyRecoveryCode(email: String, code: String): AuthError? =
        withContext(Dispatchers.IO) {
            try {
                client.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code.trim())
                null
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Could not verify the recovery code: ${e.message}")
                errorFor(e)
            }
        }

    /**
     * Setzt das Passwort der laufenden Sitzung neu.
     *
     * Verlangt eine gueltige Sitzung. Nach dem Link aus der Mail ist genau das
     * gegeben - Supabase legt beim Oeffnen des Deeplinks eine Sitzung an, die
     * fuer diesen einen Zweck reicht.
     */
    suspend fun updatePassword(newPassword: String): AuthError? = withContext(Dispatchers.IO) {
        try {
            client.auth.updateUser { password = newPassword }
            null
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Could not update the password: ${e.message}")
            errorFor(e)
        }
    }

    /**
     * Nimmt den Link aus der Mail entgegen und legt daraus die Sitzung an.
     *
     * Gibt zurueck, ob der Link zu uns gehoerte und eine Sitzung entstanden
     * ist. Die Bibliothek prueft dabei Schema und Host gegen ihre eigene
     * Konfiguration und ignoriert alles andere.
     */
    fun handleResetLink(intent: Intent, onReady: () -> Unit) {
        client.handleDeeplinks(intent) { onReady() }
    }

    /**
     * Schickt die Bestaetigungsmail erneut.
     *
     * Gibt null zurueck, wenn sie unterwegs ist, sonst den Grund. Vorher war es
     * ein einfaches Ja/Nein, und der Nutzer las bloss "hat nicht geklappt" -
     * gerade beim haeufigsten Fall, dem Stundenlimit des Maildienstes, ist aber
     * genau der Grund die Auskunft, die weiterhilft.
     */
    suspend fun resendConfirmationEmail(email: String): AuthError? = withContext(Dispatchers.IO) {
        try {
            client.auth.resendEmail(OtpType.Email.SIGNUP, email)
            null
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Could not resend the confirmation email: ${e.message}")
            errorFor(e)
        }
    }

    /**
     * Legt die Profilzeile an, falls sie noch fehlt.
     *
     * Bei aktivierter Mailbestaetigung gibt es waehrend der Registrierung noch
     * keine Sitzung, also auch keine Schreibrechte. Name und Geburtsdatum
     * stehen so lange in den Metadaten des Kontos und werden hier uebernommen.
     */
    private suspend fun ensureProfileExists(userId: String, email: String) {
        if (getProfileById(userId) != null) return

        val metadata = client.auth.currentUserOrNull()?.userMetadata
        try {
            client.postgrest["profiles"].insert(
                UserProfile(
                    id = userId,
                    email = email,
                    name = metadata.stringOrNull("name"),
                    birthdate = metadata.stringOrNull("birthdate")
                )
            )
            Log.d("SupabaseDB", "Profile created for $email")
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Profile creation failed: ${e.message}. Check the RLS policies.")
        }
    }

    private fun JsonObject?.stringOrNull(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    /**
     * Uebersetzt eine Ausnahme von Supabase in einen Grund, den die Oberflaeche
     * dem Nutzer erklaeren kann. Ohne diese Unterscheidung stand bei jedem
     * Problem nur "Registration failed" auf dem Bildschirm - auch dann, wenn
     * lediglich das Stundenlimit fuer Bestaetigungsmails erreicht war.
     */
    private fun errorFor(e: Exception): AuthError {
        if (e is AuthWeakPasswordException) return AuthError.WEAK_PASSWORD
        if (e is AuthRestException) {
            return when (e.errorCode) {
                AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists ->
                    AuthError.EMAIL_ALREADY_REGISTERED
                AuthErrorCode.WeakPassword -> AuthError.WEAK_PASSWORD
                AuthErrorCode.ValidationFailed -> AuthError.INVALID_EMAIL
                AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
                    AuthError.RATE_LIMITED
                AuthErrorCode.SignupDisabled -> AuthError.SIGNUP_DISABLED
                AuthErrorCode.EmailNotConfirmed -> AuthError.EMAIL_NOT_CONFIRMED
                AuthErrorCode.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
                // Haeufigster Fall beim Zuruecksetzen: vertippt, oder der Code
                // ist zu alt. Ohne eigene Zuordnung stuende dort nur die
                // Sammelmeldung fuer unbekannte Fehler.
                AuthErrorCode.OtpExpired -> AuthError.CODE_INVALID
                else -> AuthError.UNKNOWN
            }
        }
        if (e is java.io.IOException) return AuthError.NETWORK
        return AuthError.UNKNOWN
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

    suspend fun getProfileById(userId: String): UserProfile? = withContext(Dispatchers.IO) {
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
        birthDate: String,
        displayName: String
    ): Boolean {
        val userId = currentUserId() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                // Das vorhandene Profil wird zuerst gelesen, damit ein bereits
                // gesetztes Avatar-Bild beim Speichern nicht verloren geht -
                // upsert ersetzt die komplette Zeile.
                val existing = getProfileById(userId)
                val profile = UserProfile(
                    id = userId,
                    email = email,
                    name = name,
                    age = age,
                    height = height,
                    weight = weight,
                    birthdate = birthDate,
                    avatarUrl = existing?.avatarUrl,
                    // Bewusst der leere Text und nicht null.
                    //
                    // Supabase serialisiert mit den Voreinstellungen von
                    // kotlinx: encodeDefaults ist aus, ein Feld mit seinem
                    // Standardwert wird also gar nicht erst mitgeschickt. Der
                    // Standardwert von displayName ist null - ein geleertes
                    // Kuerzel fiel damit aus dem Upsert heraus, und Postgrest
                    // liess die Spalte unveraendert. Das Kuerzel liess sich
                    // setzen und aendern, aber nie wieder entfernen.
                    //
                    // Der leere Text entspricht nicht dem Standardwert, wird
                    // also uebertragen und ueberschreibt. Fuer die Anzeige ist
                    // er gleichbedeutend mit "nicht gesetzt", siehe
                    // DisplayName.resolve.
                    displayName = displayName.trim()
                )

                try {
                    client.postgrest["profiles"].upsert(profile)
                } catch (e: Exception) {
                    if (!isMissingColumn(e, "display_name")) throw e
                    // Die Spalte fehlt in dieser Datenbank. Das restliche Profil
                    // muss sich trotzdem speichern lassen; in der Crew erscheint
                    // dann eben der gekuerzte volle Name.
                    Log.w(
                        "SupabaseDB",
                        "Saving without the chosen short name, the display_name column is missing. " +
                                "See the documentation for the required ALTER TABLE."
                    )
                    client.postgrest["profiles"].upsert(profile.withoutDisplayName())
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the profile failed: ${e.message}")
                false
            }
        }
    }

    // --- BILD DER NUMMER EINS ---

    /**
     * Das aktuell aufgehaengte Bild einer Crew, oder null.
     *
     * Fehlt die Tabelle noch, bleibt es bei null: der Bildschirm zeigt dann die
     * leere Flaeche und alles Uebrige funktioniert weiter.
     */
    suspend fun getCrewMeme(crewCode: String): CrewMeme? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["crew_memes"].select {
                filter { eq("crew_id", crewCode) }
            }.decodeList<CrewMeme>().firstOrNull()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load the crew picture: ${e.message}")
            null
        }
    }

    /**
     * Die vorgegebene Auswahl an Bildern.
     *
     * Sie liegt als Ordner im selben Bucket wie die hochgeladenen Bilder. Damit
     * laesst sie sich erweitern, ohne die App neu zu bauen - eine Datei mehr im
     * Ordner, und sie steht beim naechsten Oeffnen zur Wahl.
     *
     * Eine leere Liste ist kein Fehler, sondern der Zustand vor der ersten
     * hinterlegten Datei: der Auswahldialog sagt das dann und der Upload aus
     * der Galerie funktioniert weiterhin.
     */
    suspend fun listMemePresets(): List<MemePreset> = withContext(Dispatchers.IO) {
        try {
            client.storage[MEME_BUCKET].list(MEME_PRESET_FOLDER)
                // Ordner haben keine Kennung, und in einem leeren Ordner legt
                // Supabase eine versteckte Platzhalterdatei ab.
                .filter { it.id != null && !it.name.startsWith(".") }
                .sortedBy { it.name }
                .map { MemePreset(it.name, presetUrl(it.name)) }
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Could not load the picture choices: ${e.message}")
            emptyList()
        }
    }

    private fun presetUrl(fileName: String): String =
        client.storage[MEME_BUCKET].publicUrl("$MEME_PRESET_FOLDER/$fileName")

    /**
     * Haengt ein eigenes Bild auf und ersetzt dabei das vorherige.
     *
     * Das alte Bild wird erst aus dem Speicher geloescht, nachdem das neue
     * oben ist - schlaegt der Upload fehl, bleibt lieber das alte haengen als
     * gar keins.
     */
    suspend fun saveCrewMeme(crewCode: String, localPath: String, caption: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = uploadImage(MEME_BUCKET, localPath)
            if (url.isEmpty()) {
                Log.e("SupabaseDB", "Uploading the crew picture failed, keeping the old one")
                return@withContext false
            }
            putCrewMeme(crewCode, url, caption)
        }

    /**
     * Haengt ein Bild aus der Auswahl auf.
     *
     * Ohne Upload: die Datei liegt bereits im Bucket, gespeichert wird nur ihre
     * Adresse.
     */
    suspend fun saveCrewMemePreset(crewCode: String, preset: MemePreset, caption: String): Boolean =
        withContext(Dispatchers.IO) { putCrewMeme(crewCode, preset.url, caption) }

    /**
     * Schreibt die Zeile der Crew und raeumt das vorherige Bild weg.
     *
     * Dass nur der Fuehrende aufhaengen darf, prueft der Bildschirm. In der
     * Datenbank laesst sich das nicht durchsetzen, weil der Rang aus
     * Aktivitaeten, Belohnungen und Schritten in der App gerechnet wird und
     * dort gar nicht bekannt ist. Die Regel in der Datenbank lautet deshalb
     * nur: schreiben darf jeder ausschliesslich unter seinem eigenen Namen.
     */
    private suspend fun putCrewMeme(crewCode: String, imageUrl: String, caption: String): Boolean {
        val userId = currentUserId() ?: return false
        val previous = getCrewMeme(crewCode)

        return try {
            client.postgrest["crew_memes"].upsert(
                CrewMeme(
                    crewId = crewCode,
                    userId = userId,
                    imageUrl = imageUrl,
                    caption = caption.trim()
                )
            )
            deleteUploadedMemeFile(previous?.imageUrl)
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not save the crew picture: ${e.message}")
            false
        }
    }

    /** Nimmt das Bild wieder ab, samt Datei. */
    suspend fun deleteCrewMeme(crewCode: String): Boolean = withContext(Dispatchers.IO) {
        val existing = getCrewMeme(crewCode)
        try {
            client.postgrest["crew_memes"].delete { filter { eq("crew_id", crewCode) } }
            deleteUploadedMemeFile(existing?.imageUrl)
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not remove the crew picture: ${e.message}")
            false
        }
    }

    /**
     * Loescht die abgehaengte Datei - aber nur, wenn sie hochgeladen wurde.
     *
     * Ein Bild aus der Auswahl gehoert keiner Crew: es liegt einmal im Bucket
     * und kann in mehreren Crews gleichzeitig haengen. Wuerde es hier mit
     * geloescht, verschwaende es fuer alle anderen mit - und aus der Auswahl
     * gleich dazu.
     */
    private suspend fun deleteUploadedMemeFile(publicUrl: String?) {
        if (publicUrl == null || isMemePreset(publicUrl)) return
        deleteFileByPublicUrl(MEME_BUCKET, publicUrl)
    }

    private fun isMemePreset(url: String): Boolean =
        url.contains("/$MEME_BUCKET/$MEME_PRESET_FOLDER/")

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
                // Wer schon Mitglied ist, soll nicht an einer doppelten Zeile
                // scheitern - der Code kann aus einem alten QR-Code stammen
                // oder aus einer Nachricht, die zweimal geoeffnet wurde. In dem
                // Fall genuegt es, die Crew zur angezeigten zu machen.
                if (!isMemberOf(code, userId)) {
                    client.postgrest["crew_members"].insert(
                        CrewMember(crewId = code, userId = userId)
                    )
                }
                setJoinedCrewCode(code)
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Joining the crew failed: ${e.message}")
                false
            }
        }
    }

    private suspend fun isMemberOf(code: String, userId: String): Boolean = try {
        client.postgrest["crew_members"].select {
            filter {
                eq("crew_id", code)
                eq("user_id", userId)
            }
            limit(1)
        }.decodeList<CrewMember>().isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private suspend fun crewExists(code: String): Boolean = try {
        client.postgrest["crews"].select {
            filter { eq("id", code) }
            limit(1)
        }.decodeList<Crew>().isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Alle Crews, in denen der Nutzer ist - nach Namen sortiert.
     *
     * Die Datenbank konnte das von Anfang an: crew_members ist eine Zuordnung
     * ohne Beschraenkung auf eine Zeile je Nutzer. Nur die App ging bisher von
     * genau einer aus.
     */
    suspend fun getJoinedCrews(): List<Crew> = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext emptyList()
        try {
            val codes = client.postgrest["crew_members"].select {
                filter { eq("user_id", userId) }
            }.decodeList<CrewMember>().map { it.crewId }

            if (codes.isEmpty()) return@withContext emptyList()

            client.postgrest["crews"].select {
                filter { isIn("id", codes) }
            }.decodeList<Crew>().sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load the crews: ${e.message}")
            emptyList()
        }
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
                // Wer in weiteren Crews ist, soll nicht auf dem Startbildschirm
                // fuer "keine Crew" landen - dann waere die naechste Crew zwar
                // vorhanden, aber erst nach erneutem Beitreten wieder zu sehen.
                setJoinedCrewCode(getJoinedCrews().firstOrNull()?.id)
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
            // Mit im Snapshot statt als eigene Abfrage aus dem
            // Startbildschirm heraus: die Abfragen wurden hier bewusst
            // gebuendelt, und eine sechste nebenher liefe an dieser
            // Buendelung vorbei.
            val crewImageAsync = async { getCrewImageUrl(crewCode) }

            val memberIds = memberIdsAsync.await()
            val challenges = challengesAsync.await()

            val profilesAsync = async { selectProfiles(memberIds) }
            val rewardsAsync = async { selectRewards(challenges.map { it.id }) }
            val stepDaysAsync = async { selectStepDays(memberIds) }

            CrewSnapshot(
                members = profilesAsync.await(),
                activities = activitiesAsync.await(),
                challenges = challenges,
                rewards = rewardsAsync.await(),
                stepDays = stepDaysAsync.await(),
                crewImageUrl = crewImageAsync.await()
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

    /**
     * Die Schritt-Tage aller Mitglieder.
     *
     * Ohne Einschraenkung auf heute, weil dieselben Zeilen zwei Zwecken dienen:
     * der Ring in der Rangliste braucht den heutigen Tag, die Bonuspunkte alle
     * erreichten. Eine Zeile je Nutzer und Tag - das bleibt auch nach einem Jahr
     * eine kleine Menge.
     *
     * Fehlt die Tabelle noch, bleibt es bei einer leeren Liste: dann gibt es
     * keine Ringe und keine Bonuspunkte, aber die Rangliste steht.
     */
    private suspend fun selectStepDays(userIds: List<String>): List<StepDay> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["step_days"].select {
                filter { isIn("user_id", userIds) }
            }.decodeList<StepDay>()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load the step days: ${e.message}")
            emptyList()
        }
    }

    /**
     * Haelt die heutige Schrittzahl des angemeldeten Nutzers fest.
     *
     * Upsert auf den Schluessel aus Nutzer und Tag: im Laufe des Tages wird
     * dieselbe Zeile immer weiter hochgezaehlt, statt dass fuer jeden Abruf eine
     * neue entsteht. Geschrieben wird nur die eigene Zeile.
     */
    suspend fun saveTodaySteps(steps: Int): Boolean {
        val userId = currentUserId() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                client.postgrest["step_days"].upsert(
                    StepDay(userId = userId, day = HealthSteps.todayKey(), steps = steps)
                )
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Could not save today's steps: ${e.message}")
                false
            }
        }
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

    /**
     * Gleicht nach der Anmeldung ab, welche Crew angezeigt wird.
     *
     * Eine bereits gewaehlte bleibt stehen, solange sie noch eine Mitgliedschaft
     * ist - sonst spraenge die App bei jedem Start auf eine andere Crew, sobald
     * jemand in mehreren ist. Nur wenn sie nicht mehr gilt oder noch keine
     * gewaehlt war, wird die erste genommen.
     */
    private suspend fun cacheJoinedCrew(userId: String) {
        try {
            val codes = client.postgrest["crew_members"].select {
                filter { eq("user_id", userId) }
            }.decodeList<CrewMember>().map { it.crewId }

            val active = getJoinedCrewCode()
            if (active != null && active in codes) return
            setJoinedCrewCode(codes.firstOrNull())
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not determine crew membership: ${e.message}")
        }
    }

    // --- SUCHE ---

    /**
     * Sucht Personen nach Kuerzel oder Name.
     *
     * Gesucht wird ohne Ruecksicht auf Gross- und Kleinschreibung und an
     * beliebiger Stelle im Namen - wer jemanden sucht, weiss selten, wie der
     * Eintrag genau geschrieben ist. Die eigene Person faellt heraus: sich
     * selbst zu finden hilft nicht weiter.
     *
     * Private Profile bleiben draussen. Das Aussieben macht die Datenbank und
     * nicht die App: ein Profil, das nicht gefunden werden will, soll auch
     * nicht uebertragen werden.
     */
    suspend fun searchPeople(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.length < SEARCH_MIN_LENGTH) return@withContext emptyList()
        val me = currentUserId()

        // Fehlt die Spalte is_public im Projekt, weist Postgrest die Abfrage ab,
        // die sie nennt. Dann wird ohne sie gesucht - ohne diesen Rueckfall
        // faende die Suche in einem solchen Projekt ueberhaupt niemanden mehr.
        val found = searchProfiles(term, onlyPublic = true)
            ?: searchProfiles(term, onlyPublic = false)
            ?: return@withContext emptyList()

        found.filter { it.id != me }.sortedBy { DisplayName.of(it).lowercase() }
    }

    /** Null bedeutet: die Abfrage ist gescheitert, nicht "nichts gefunden". */
    private suspend fun searchProfiles(term: String, onlyPublic: Boolean): List<UserProfile>? = try {
        val pattern = "%$term%"
        client.postgrest["profiles"].select {
            filter {
                or {
                    ilike("display_name", pattern)
                    ilike("name", pattern)
                }
                if (onlyPublic) eq("is_public", true)
            }
            limit(SEARCH_LIMIT)
        }.decodeList<UserProfile>()
    } catch (e: Exception) {
        Log.e("SupabaseDB", "Could not search for people: ${e.message}")
        null
    }

    /**
     * Sucht Crews nach Namen oder Code.
     *
     * Der Code ist mit aufgenommen, weil er auf QR-Codes und in Nachrichten
     * herumgereicht wird - wer ihn hat, soll ihn hier eintippen koennen, statt
     * den Umweg ueber den Beitrittsbildschirm zu nehmen.
     */
    suspend fun searchCrews(query: String): List<Crew> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.length < SEARCH_MIN_LENGTH) return@withContext emptyList()

        try {
            val pattern = "%$term%"
            client.postgrest["crews"].select {
                filter {
                    or {
                        ilike("name", pattern)
                        ilike("id", pattern)
                    }
                }
                limit(SEARCH_LIMIT)
            }.decodeList<Crew>().sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not search for crews: ${e.message}")
            emptyList()
        }
    }

    // --- SICHTBARKEIT DES PROFILS ---

    /**
     * Ob das eigene Profil in der Suche auftaucht.
     *
     * Bei Zweifeln oeffentlich: das ist die Voreinstellung der Spalte, und ein
     * Lesefehler darf nicht dazu fuehren, dass die Einstellungen einen anderen
     * Stand anzeigen als die Datenbank tatsaechlich hat.
     */
    suspend fun isProfilePublic(): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext true
        try {
            client.postgrest["profiles"].select(Columns.list("id", "is_public")) {
                filter { eq("id", userId) }
                limit(1)
            }.decodeSingleOrNull<ProfileVisibility>()?.isPublic ?: true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Reading the profile visibility failed: ${e.message}")
            true
        }
    }

    /**
     * Setzt die Sichtbarkeit. Nur diese eine Spalte, nicht das ganze Profil:
     * ein Upsert wuerde jedes Feld mitschicken und koennte dabei ueberschreiben,
     * was gerade auf einem anderen Geraet geaendert wurde.
     */
    suspend fun setProfilePublic(isPublic: Boolean): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext false
        try {
            client.postgrest["profiles"].update({
                ProfileVisibility::isPublic setTo isPublic
            }) {
                filter { eq("id", userId) }
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Saving the profile visibility failed: ${e.message}")
            false
        }
    }

    // --- BEITRITTSANFRAGEN ---

    /**
     * Ob der angemeldete Nutzer diese Crew gegruendet hat.
     *
     * Nicht zu verwechseln mit dem Fuehrenden der Rangliste: dieser hier darf
     * ueber Anfragen entscheiden, jener hat nur die meisten Punkte.
     */
    suspend fun isCrewCreator(crewCode: String): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext false
        try {
            client.postgrest["crews"].select {
                filter { eq("id", crewCode) }
                limit(1)
            }.decodeSingleOrNull<Crew>()?.creatorId == userId
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Bittet um Aufnahme in eine Crew.
     *
     * Wer schon Mitglied ist, fragt nicht an - das kann passieren, wenn ein
     * Suchergebnis noch von vor dem Beitritt stammt. Eine zweite Anfrage
     * derselben Person scheitert am Schluessel, deshalb wird vorher geprueft.
     */
    suspend fun requestToJoinCrew(crewCode: String): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext false
        if (isMemberOf(crewCode, userId)) return@withContext false

        try {
            if (!hasRequestedToJoin(crewCode)) {
                client.postgrest["crew_join_requests"].insert(
                    CrewJoinRequest(crewId = crewCode, userId = userId)
                )
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Requesting to join failed: ${e.message}")
            false
        }
    }

    /** Ob fuer diese Crew schon eine eigene Anfrage offen ist. */
    suspend fun hasRequestedToJoin(crewCode: String): Boolean = withContext(Dispatchers.IO) {
        val userId = currentUserId() ?: return@withContext false
        try {
            client.postgrest["crew_join_requests"].select {
                filter {
                    eq("crew_id", crewCode)
                    eq("user_id", userId)
                }
                limit(1)
            }.decodeList<CrewJoinRequest>().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Die offenen Anfragen an eine Crew, als Profile.
     *
     * Leer, solange die Tabelle im Projekt fehlt - dann zeigt der
     * Crew-Bildschirm den Abschnitt schlicht nicht an, statt eine Fehlermeldung
     * zu bringen fuer etwas, das niemand ausgeloest hat.
     */
    suspend fun getJoinRequests(crewCode: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val ids = try {
            client.postgrest["crew_join_requests"].select {
                filter { eq("crew_id", crewCode) }
            }.decodeList<CrewJoinRequest>().map { it.userId }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Reading the join requests failed: ${e.message}")
            emptyList()
        }

        if (ids.isEmpty()) emptyList() else selectProfiles(ids)
    }

    /**
     * Nimmt eine Anfrage an: Mitglied eintragen, Anfrage loeschen.
     *
     * In dieser Reihenfolge. Bricht es dazwischen ab, ist die Person Mitglied
     * und die Anfrage steht noch - das faellt auf und laesst sich mit einem
     * zweiten Antippen beheben. Andersherum waere die Anfrage weg und niemand
     * wuesste mehr, dass sie bestand.
     */
    suspend fun acceptJoinRequest(crewCode: String, userId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!isMemberOf(crewCode, userId)) {
                    client.postgrest["crew_members"].insert(
                        CrewMember(crewId = crewCode, userId = userId)
                    )
                }
                deleteJoinRequest(crewCode, userId)
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Accepting the join request failed: ${e.message}")
                false
            }
        }

    /** Lehnt eine Anfrage ab. Die Person kann danach erneut anfragen. */
    suspend fun rejectJoinRequest(crewCode: String, userId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                deleteJoinRequest(crewCode, userId)
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Rejecting the join request failed: ${e.message}")
                false
            }
        }

    private suspend fun deleteJoinRequest(crewCode: String, userId: String) {
        client.postgrest["crew_join_requests"].delete {
            filter {
                eq("crew_id", crewCode)
                eq("user_id", userId)
            }
        }
    }

    // --- BILD DER CREW ---

    /**
     * Laedt das Bild der Crew hoch und traegt seine Adresse ein.
     *
     * Derselbe Bucket wie beim Profilbild: die Bilder sind gleich gross, gleich
     * oeffentlich und werden gleich behandelt - ein zweiter Bucket haette
     * dieselben Regeln und muesste von Hand angelegt werden.
     */
    suspend fun saveCrewImage(crewCode: String, localPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = uploadImage("avatars", localPath)
            if (url.isEmpty()) return@withContext false

            try {
                client.postgrest["crews"].update({
                    Crew::imageUrl setTo url
                }) {
                    filter { eq("id", crewCode) }
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the crew image failed: ${e.message}")
                false
            }
        }

    /** Die Adresse des Crew-Bilds, oder null wenn keines gesetzt ist. */
    suspend fun getCrewImageUrl(crewCode: String): String? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["crews"].select {
                filter { eq("id", crewCode) }
                limit(1)
            }.decodeSingleOrNull<Crew>()?.imageUrl
        } catch (e: Exception) {
            null
        }
    }

    // --- FOLGEN ---

    /** Ob der angemeldete Nutzer dieser Person folgt. */
    suspend fun isFollowing(userId: String): Boolean = withContext(Dispatchers.IO) {
        val me = currentUserId() ?: return@withContext false
        try {
            client.postgrest["follows"].select {
                filter {
                    eq("follower_id", me)
                    eq("followee_id", userId)
                }
                limit(1)
            }.decodeList<Follow>().isNotEmpty()
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not check the follow: ${e.message}")
            false
        }
    }

    /**
     * Folgt einer Person, oder beendet es.
     *
     * Sich selbst zu folgen wird abgewiesen - es waere kein Fehler, aber die
     * eigene Person in der eigenen Liste zu fuehren hat keinen Sinn.
     */
    suspend fun setFollowing(userId: String, follow: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val me = currentUserId() ?: return@withContext false
            if (me == userId) return@withContext false

            try {
                if (follow) {
                    client.postgrest["follows"].upsert(Follow(followerId = me, followeeId = userId))
                } else {
                    client.postgrest["follows"].delete {
                        filter {
                            eq("follower_id", me)
                            eq("followee_id", userId)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Could not change the follow: ${e.message}")
                false
            }
        }

    /** Die Profile derer, denen der angemeldete Nutzer folgt, nach Namen sortiert. */
    suspend fun getFollowing(): List<UserProfile> = withContext(Dispatchers.IO) {
        val me = currentUserId() ?: return@withContext emptyList()
        try {
            val ids = client.postgrest["follows"].select {
                filter { eq("follower_id", me) }
            }.decodeList<Follow>().map { it.followeeId }

            if (ids.isEmpty()) return@withContext emptyList()

            client.postgrest["profiles"].select {
                filter { isIn("id", ids) }
            }.decodeList<UserProfile>().sortedBy { DisplayName.of(it).lowercase() }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load who is followed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Die Crews, in denen eine Person ist.
     *
     * Grundlage dafuer, ueber jemanden einer Crew beizutreten: man sieht, wo
     * die Person mitmacht, und kann von dort aus dazukommen.
     */
    suspend fun getCrewsOf(userId: String): List<Crew> = withContext(Dispatchers.IO) {
        try {
            val codes = client.postgrest["crew_members"].select {
                filter { eq("user_id", userId) }
            }.decodeList<CrewMember>().map { it.crewId }

            if (codes.isEmpty()) return@withContext emptyList()

            client.postgrest["crews"].select {
                filter { isIn("id", codes) }
            }.decodeList<Crew>().sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Could not load the crews of a member: ${e.message}")
            emptyList()
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
        intensity: String = WorkoutIntensity.MEDIUM.name,
        latitude: Double? = null,
        longitude: Double? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null
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

                val activity = Activity(
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
                    timestamp = ActivityTime.now(),
                    latitude = latitude,
                    longitude = longitude,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate
                )

                try {
                    client.postgrest["activities"].insert(activity)
                } catch (e: Exception) {
                    if (isMissingColumn(e, "heart_rate")) {
                        // Die Pulsspalten fehlen in dieser Datenbank. Wie bei
                        // den Koordinaten: das Workout ist wichtiger als die
                        // Beigabe.
                        Log.w(
                            "SupabaseDB",
                            "Saving without the heart rate, the avg/max_heart_rate columns " +
                                    "are missing. See the documentation for the required ALTER TABLE."
                        )
                        client.postgrest["activities"].insert(activity.withoutHeartRate())
                        return@withContext true
                    }
                    if (!isMissingColumn(e, "latitude", "longitude")) throw e
                    // Die Koordinatenspalten fehlen in dieser Datenbank. Das
                    // Workout selbst darf daran nicht scheitern - es wird ohne
                    // sie gespeichert und bekommt in der Historie eben keine
                    // Karte.
                    Log.w(
                        "SupabaseDB",
                        "Saving without coordinates, the latitude/longitude columns are missing. " +
                                "See the documentation for the required ALTER TABLE."
                    )
                    client.postgrest["activities"].insert(activity.withoutCoordinates())
                }
                true
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Saving the activity failed: ${e.message}")
                false
            }
        }
    }

    /** Erkennt die Meldung von Postgrest ueber eine der genannten unbekannten Spalten. */
    private fun isMissingColumn(e: Exception, vararg columns: String): Boolean {
        val message = e.message.orEmpty()
        return message.contains("column", ignoreCase = true) &&
                columns.any { message.contains(it, ignoreCase = true) }
    }

    private fun UserProfile.withoutDisplayName() = UserProfileWithoutDisplayName(
        id = id,
        email = email,
        name = name,
        age = age,
        height = height,
        weight = weight,
        birthdate = birthdate,
        avatarUrl = avatarUrl
    )

    private fun Challenge.withoutDeadline() = ChallengeWithoutDeadline(
        id = id,
        crewId = crewId,
        type = type,
        goal = goal,
        reward = reward
    )

    private fun Activity.withoutHeartRate() = ActivityWithoutHeartRate(
        userId = userId,
        crewId = crewId,
        sport = sport,
        duration = duration,
        distance = distance,
        location = location,
        voiceUrl = voiceUrl,
        photoUrl = photoUrl,
        intensity = intensity,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude
    )

    private fun Activity.withoutCoordinates() = ActivityWithoutCoordinates(
        userId = userId,
        crewId = crewId,
        sport = sport,
        duration = duration,
        distance = distance,
        location = location,
        voiceUrl = voiceUrl,
        photoUrl = photoUrl,
        intensity = intensity,
        timestamp = timestamp
    )

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

    /** @param deadline letzter zaehlender Tag als ISO-Datum, oder null ohne Frist. */
    suspend fun addCrewChallenge(
        crewCode: String,
        type: String,
        goal: Int,
        reward: Int,
        deadline: String?
    ): Boolean =
        withContext(Dispatchers.IO) {
            val challenge = Challenge(
                id = System.currentTimeMillis().toString(),
                crewId = crewCode,
                type = type,
                goal = goal,
                reward = reward,
                deadline = deadline
            )

            try {
                try {
                    client.postgrest["challenges"].insert(challenge)
                } catch (e: Exception) {
                    if (!isMissingColumn(e, "deadline")) throw e
                    // Die Spalte fehlt in dieser Datenbank. Eine Challenge
                    // anlegen zu koennen ist wichtiger als die Frist; sie
                    // laeuft dann eben unbefristet weiter.
                    Log.w(
                        "SupabaseDB",
                        "Saving without the deadline, the deadline column is missing. " +
                                "See the documentation for the required ALTER TABLE."
                    )
                    client.postgrest["challenges"].insert(challenge.withoutDeadline())
                }
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
     * Schuettet alle faelligen Challenge-Belohnungen aus.
     *
     * Wird von der Rangliste und vom Challenge-Bildschirm aufgerufen: beide
     * laden ohnehin einen Snapshot, und die Punkte sollen unabhaengig davon
     * ankommen, welchen der beiden Bildschirme jemand oeffnet.
     *
     * @return true, wenn etwas geschrieben wurde und der Snapshot damit
     *         veraltet ist.
     */
    suspend fun awardCompletedChallenges(snapshot: CrewSnapshot): Boolean {
        val memberIds = snapshot.members.map { it.id }
        var awarded = false

        for (challenge in snapshot.challenges) {
            val total = ChallengeManager.progressByMember(challenge, snapshot).sumOf { it.second }
            val award = ChallengeManager.pendingAward(challenge, total, memberIds, snapshot)
                ?: continue
            if (awardChallenge(award)) awarded = true
        }
        return awarded
    }

    /**
     * Schreibt die Belohnungen einer abgeschlossenen Challenge.
     *
     * Alle Eintraege gehen in einem einzigen Insert raus statt in einem Aufruf
     * pro Person. Die tatsaechlich vergebene Punktzahl steht im Eintrag selbst,
     * damit sie sich nicht aendert, wenn die Crew spaeter waechst oder
     * schrumpft.
     */
    private suspend fun awardChallenge(award: PendingAward): Boolean = withContext(Dispatchers.IO) {
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
            // Leere Dateien entstehen, wenn eine Aufnahme abgebrochen wurde.
            // Hochgeladen ergaeben sie eine URL, hinter der nichts steht.
            if (!file.exists() || file.length() == 0L) return@withContext ""
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
    val rewards: List<ChallengeReward>,
    /** Schritte je Mitglied und Tag; leer, solange die Tabelle fehlt. */
    val stepDays: List<StepDay> = emptyList(),
    /**
     * Das Bild der Crew fuer den Kopf des Startbildschirms. Null heisst: die
     * Crew hat keines gesetzt, dann steht dort das CrewFit-Logo.
     */
    val crewImageUrl: String? = null
)
