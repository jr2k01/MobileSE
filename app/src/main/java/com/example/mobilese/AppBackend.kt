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
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ein Backend für die App, das nun Supabase nutzt.
 */
class AppBackend(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("CrewFitDatabase", Context.MODE_PRIVATE)

    val client = createSupabaseClient(
        supabaseUrl = "https://ghhtaaoedlvhipmnuziu.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdoaHRhYW9lZGx2aGlwbW51eml1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYzODQ3NjUsImV4cCI6MjEwMTk2MDc2NX0.YVXEZgVJpkSTd7Ms5LrwmQgD0cUFBvfzYykYe9KILhg"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }

    // --- STORAGE ---

    private suspend fun uploadFile(bucket: String, localPath: String): String {
        if (localPath.isEmpty()) return ""
        val file = File(localPath)
        if (!file.exists()) return ""
        val fileName = "${System.currentTimeMillis()}_${file.name}"
        return try {
            client.storage[bucket].upload(fileName, file.readBytes())
            // Correct way to get the public URL in current SDK versions
            client.storage[bucket].publicUrl(fileName)
        } catch (e: Exception) {
            Log.e("SupabaseStorage", "Upload error in bucket $bucket: ${e.message}")
            ""
        }
    }

    // --- AUTH ---

    suspend fun registerUser(email: String, password: String, name: String, birthDate: String): Boolean {
        return try {
            Log.d("SupabaseAuth", "Starting registration for $email")
            val userInfo = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            val userId = userInfo?.id ?: run {
                Log.e("SupabaseAuth", "Registration successful but no ID returned. CHECK SUPABASE 'Confirm Email' settings!")
                return false
            }

            Log.d("SupabaseAuth", "User registered, ID: $userId. Now creating profile...")
            
            // Create profile
            try {
                client.postgrest["profiles"].insert(UserProfile(id = userId, email = email, name = name, birthdate = birthDate))
                Log.d("SupabaseDB", "Profile created successfully")
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Profile creation FAILED: ${e.message}. Check RLS settings!")
                // We return true because Auth was successful, but the profile might need fixing
            }
            
            setCurrentUser(email)
            true
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Registration FAILED: ${e.message}")
            false
        }
    }

    suspend fun loginUser(email: String, password: String): Boolean {
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                setCurrentUser(user.email ?: "")
                fetchAndCacheUserCrew(user.id)
                true
            } else false
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Login error: ${e.message}")
            false
        }
    }

    fun logout() {
        prefs.edit().remove("current_session_user").remove("user_joined_crew_code").apply()
    }

    suspend fun deleteUserProfile(email: String) {
        val userId = getCurrentUserId() ?: return
        try {
            client.postgrest["profiles"].delete {
                filter { eq("id", userId) }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Delete profile error: ${e.message}")
        }
        logout()
    }

    // --- PROFILES ---

    suspend fun saveUserProfile(email: String, name: String, age: String, height: String, weight: String, birthDate: String) {
        val userId = getCurrentUserId() ?: return
        try {
            client.postgrest["profiles"].upsert(
                UserProfile(id = userId, email = email, name = name, age = age, height = height, weight = weight, birthdate = birthDate)
            )
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Save profile error: ${e.message}")
        }
    }

    suspend fun getUserName(email: String): String {
        return try {
            val response = client.postgrest["profiles"].select {
                filter { eq("email", email) }
            }.decodeSingle<UserProfile>()
            response.name ?: "Unknown"
        } catch (e: Exception) { "Unknown" }
    }

    suspend fun getUserData(email: String, key: String): String {
        return try {
            val profile = client.postgrest["profiles"].select {
                filter { eq("email", email) }
            }.decodeSingle<UserProfile>()
            when(key) {
                "name" -> profile.name
                "age" -> profile.age
                "height" -> profile.height
                "weight" -> profile.weight
                "birthdate" -> profile.birthdate
                "profile_image_path" -> profile.avatarUrl
                else -> ""
            } ?: ""
        } catch (e: Exception) { "" }
    }

    suspend fun saveUserImagePath(email: String, localPath: String) {
        val userId = getCurrentUserId() ?: return
        val cloudUrl = uploadFile("avatars", localPath)
        if (cloudUrl.isNotEmpty()) {
            try {
                Log.d("SupabaseDB", "Updating avatar URL to: $cloudUrl")
                client.postgrest["profiles"].update({
                    UserProfile::avatarUrl setTo cloudUrl
                }) {
                    filter { eq("id", userId) }
                }
            } catch (e: Exception) {
                Log.e("SupabaseDB", "Update image path error: ${e.message}")
            }
        }
    }

    // --- CREWS ---

    suspend fun createCrew(crewName: String, creatorEmail: String, code: String) {
        val userId = getCurrentUserId() ?: return
        try {
            client.postgrest["crews"].insert(Crew(id = code, name = crewName, creatorId = userId))
            joinCrew(code, creatorEmail)
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Create crew error: ${e.message}")
        }
    }

    suspend fun joinCrew(code: String, userEmail: String): Boolean {
        val userId = getCurrentUserId() ?: return false
        return try {
            client.postgrest["crew_members"].insert(CrewMember(crewId = code, userId = userId))
            setJoinedCrewCode(code)
            true
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Join crew error: ${e.message}")
            false
        }
    }

    suspend fun getCrewName(code: String): String {
        return try {
            val response = client.postgrest["crews"].select {
                filter { eq("id", code) }
            }.decodeSingle<Crew>()
            response.name
        } catch (e: Exception) { "Unknown Crew" }
    }

    suspend fun getCrewMembers(code: String): Set<String> {
        return try {
            // Join with profiles to get emails
            val memberIds = client.postgrest["crew_members"].select {
                filter { eq("crew_id", code) }
            }.decodeList<CrewMember>().map { it.userId }
            
            val profiles = client.postgrest["profiles"].select {
                filter {
                    isIn("id", memberIds)
                }
            }.decodeList<UserProfile>()
            
            profiles.mapNotNull { it.email }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    suspend fun leaveCrew(code: String, userEmail: String) {
        val userId = getCurrentUserId() ?: return
        try {
            client.postgrest["crew_members"].delete {
                filter {
                    eq("crew_id", code)
                    eq("user_id", userId)
                }
            }
            setJoinedCrewCode(null)
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Leave crew error: ${e.message}")
        }
    }

    // --- ACTIVITIES ---

    suspend fun addActivity(email: String, sport: String, photoPath: String, location: String, duration: String, voicePath: String = "", distance: String = "0", intensity: String = "MEDIUM") {
        try {
            val userId = getCurrentUserId() ?: return
            val crewId = getJoinedCrewCode() ?: "no_crew"
            val timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).apply { 
                timeZone = TimeZone.getTimeZone("Europe/Berlin") 
            }.format(Date())

            val cloudPhotoUrl = uploadFile("photos", photoPath)
            val cloudVoiceUrl = uploadFile("voice_notes", voicePath)

            client.postgrest["activities"].insert(
                Activity(
                    userId = userId,
                    crewId = crewId,
                    sport = sport,
                    duration = duration.toIntOrNull() ?: 0,
                    distance = distance.replace(",", ".").toDoubleOrNull() ?: 0.0,
                    location = location,
                    voiceUrl = cloudVoiceUrl,
                    photoUrl = cloudPhotoUrl,
                    intensity = intensity,
                    timestamp = timestamp
                )
            )
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Add activity error: ${e.message}")
        }
    }

    suspend fun getUserActivities(email: String): List<Activity> {
        val userId = emailToId(email) ?: return emptyList()
        return try {
            client.postgrest["activities"].select {
                filter { eq("user_id", userId) }
            }.decodeList<Activity>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getUserActivitiesForCrew(email: String, crewCode: String): List<Activity> {
        val userId = emailToId(email) ?: return emptyList()
        return try {
            client.postgrest["activities"].select {
                filter {
                    eq("user_id", userId)
                    eq("crew_id", crewCode)
                }
            }.decodeList<Activity>()
        } catch (e: Exception) { emptyList() }
    }

    // --- CHALLENGES ---

    suspend fun addCrewChallenge(crewCode: String, type: String, goal: Int, reward: Int = 0) {
        val id = System.currentTimeMillis().toString()
        try {
            client.postgrest["challenges"].insert(Challenge(id = id, crewId = crewCode, type = type, goal = goal, reward = reward))
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Add challenge error: ${e.message}")
        }
    }

    suspend fun getCrewChallenges(crewCode: String): Set<Challenge> {
        return try {
            client.postgrest["challenges"].select {
                filter { eq("crew_id", crewCode) }
            }.decodeList<Challenge>().toSet()
        } catch (e: Exception) { emptySet() }
    }

    suspend fun deleteCrewChallenge(crewCode: String, challengeId: String) {
        try {
            client.postgrest["challenges"].delete {
                filter { eq("id", challengeId) }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Delete challenge error: ${e.message}")
        }
    }

    suspend fun getPointsForCrew(email: String, crewCode: String): Int {
        val activities = getUserActivitiesForCrew(email, crewCode)
        var totalPoints = 0
        for (activity in activities) {
            val intensity = try { WorkoutIntensity.valueOf(activity.intensity) } catch (e: Exception) { WorkoutIntensity.MEDIUM }
            totalPoints += PointsCalculator.calculateWorkoutPoints(activity.duration, intensity)
        }
        totalPoints += getUserChallengePoints(email, crewCode)
        return totalPoints
    }

    suspend fun getUserChallengePoints(email: String, crewCode: String): Int {
        return 0 
    }

    suspend fun isChallengeRewarded(email: String, challengeId: String): Boolean {
        val userId = emailToId(email) ?: return true
        return try {
            val response = client.postgrest["challenge_rewards"].select {
                filter {
                    eq("challenge_id", challengeId)
                    eq("user_id", userId)
                }
            }.decodeList<ChallengeReward>()
            response.isNotEmpty()
        } catch (e: Exception) { true }
    }

    suspend fun markChallengeRewarded(email: String, challengeId: String) {
        val userId = emailToId(email) ?: return
        try {
            client.postgrest["challenge_rewards"].insert(ChallengeReward(challengeId = challengeId, userId = userId))
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Mark reward error: ${e.message}")
        }
    }

    suspend fun addUserChallengePoints(email: String, crewCode: String, points: Int) {}

    fun fullResetAllData() {}

    // --- SESSION & CACHE ---
    
    fun setCurrentUser(email: String) = prefs.edit().putString("current_session_user", email).apply()
    fun getCurrentUser(): String? = prefs.getString("current_session_user", null)
    
    fun getJoinedCrewCode(): String? = prefs.getString("user_joined_crew_code", null)
    fun setJoinedCrewCode(code: String?) = prefs.edit().putString("user_joined_crew_code", code).apply()
    fun getJoinedCrew(): String? = getJoinedCrewCode()

    fun getCurrentUserId(): String? = client.auth.currentUserOrNull()?.id
    
    private suspend fun fetchAndCacheUserCrew(userId: String) {
        try {
            val membership = client.postgrest["crew_members"].select {
                filter { eq("user_id", userId) }
            }.decodeList<CrewMember>().firstOrNull()
            setJoinedCrewCode(membership?.crewId)
        } catch (e: Exception) {}
    }

    private suspend fun emailToId(email: String): String? {
        // First check current user
        val current = client.auth.currentUserOrNull()
        if (current?.email == email) return current.id
        
        // Otherwise look up in profiles
        return try {
            val profile = client.postgrest["profiles"].select {
                filter { eq("email", email) }
            }.decodeSingle<UserProfile>()
            profile.id
        } catch (e: Exception) { null }
    }
}
