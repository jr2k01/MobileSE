package com.example.mobilese

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ein simuliertes Backend für die App.
 * Speichert Daten in SharedPreferences, aber strukturiert sie so, dass 
 * individuelle Nutzerdaten und geteilte Crew-Daten möglich sind.
 */
class AppBackend(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("CrewFitDatabase", Context.MODE_PRIVATE)

    // --- NUTZER VERWALTUNG ---

    fun registerUser(email: String, password: String, name: String, birthDate: String): Boolean {
        if (prefs.contains("user_${email}_password")) return false
        
        prefs.edit().apply {
            putString("user_${email}_password", password)
            putString("user_${email}_name", name)
            putString("user_${email}_birthdate", birthDate)
            apply()
        }
        return true
    }

    fun loginUser(email: String, password: String): Boolean {
        val storedPass = prefs.getString("user_${email}_password", null)
        return storedPass != null && storedPass == password
    }

    fun saveUserProfile(email: String, name: String, age: String, height: String, weight: String, birthDate: String) {
        prefs.edit().apply {
            putString("user_${email}_name", name)
            putString("user_${email}_age", age)
            putString("user_${email}_height", height)
            putString("user_${email}_weight", weight)
            putString("user_${email}_birthdate", birthDate)
            apply()
        }
    }

    fun getUserName(email: String): String = prefs.getString("user_${email}_name", "Unknown") ?: "Unknown"
    fun getUserData(email: String, key: String): String = prefs.getString("user_${email}_$key", "") ?: ""

    fun saveUserImagePath(email: String, path: String) {
        prefs.edit().putString("user_${email}_profile_image_path", path).apply()
    }

    // --- CREW VERWALTUNG ---

    fun createCrew(crewName: String, creatorEmail: String, code: String) {
        prefs.edit().apply {
            putString("crew_data_${code}_name", crewName)
            val members = getCrewMembers(code).toMutableSet()
            members.add(creatorEmail)
            putStringSet("crew_data_${code}_members", members)
            putString("user_${creatorEmail}_crew_code", code)
            apply()
        }
    }

    fun joinCrew(code: String, userEmail: String): Boolean {
        if (!prefs.contains("crew_data_${code}_name")) return false
        val members = getCrewMembers(code).toMutableSet()
        members.add(userEmail)
        prefs.edit().apply {
            putStringSet("crew_data_${code}_members", members)
            putString("user_${userEmail}_crew_code", code)
            apply()
        }
        return true
    }

    fun getCrewName(code: String): String = prefs.getString("crew_data_${code}_name", "Unknown Crew") ?: "Unknown Crew"
    fun getCrewMembers(code: String): Set<String> = prefs.getStringSet("crew_data_${code}_members", emptySet()) ?: emptySet()
    
    fun leaveCrew(code: String, userEmail: String) {
        val members = getCrewMembers(code).toMutableSet()
        members.remove(userEmail)
        prefs.edit().apply {
            putStringSet("crew_data_${code}_members", members)
            remove("user_${userEmail}_crew_code")
            apply()
        }
    }

    // --- AKTIVITÄTEN VERWALTUNG ---

    /**
     * Speichert eine Aktivität mit Zusatzinfos.
     * Format: sport|timestamp|photoPath|location|crewCode|duration|voicePath|distance|intensity
     */
    fun addActivity(email: String, sport: String, photoPath: String, location: String, duration: String, voicePath: String = "", distance: String = "0", intensity: String = "MEDIUM") {
        val currentActivities = getUserActivities(email).toMutableList()
        
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        sdf.timeZone = TimeZone.getTimeZone("Europe/Berlin")
        val timestamp = sdf.format(Date())

        val crewCode = getJoinedCrewCode() ?: "no_crew"
        
        // Wir nutzen ein Trennzeichen, das unwahrscheinlich in Pfaden vorkommt
        val activityEntry = "$sport|$timestamp|$photoPath|$location|$crewCode|$duration|$voicePath|$distance|$intensity"
        currentActivities.add(activityEntry)
        
        prefs.edit().putStringSet("user_${email}_activities", currentActivities.toSet()).apply()
    }

    // --- CHALLENGES ---

    fun addCrewChallenge(crewCode: String, type: String, goal: Int, reward: Int = 0) {
        val challenges = getCrewChallenges(crewCode).toMutableSet()
        // Format: type|goal|id|reward
        val challengeId = System.currentTimeMillis().toString()
        challenges.add("$type|$goal|$challengeId|$reward")
        prefs.edit().putStringSet("crew_${crewCode}_challenges", challenges).apply()
    }

    fun getCrewChallenges(crewCode: String): Set<String> {
        return prefs.getStringSet("crew_${crewCode}_challenges", emptySet()) ?: emptySet()
    }

    fun deleteCrewChallenge(crewCode: String, challengeId: String) {
        val challenges = getCrewChallenges(crewCode).toMutableSet()
        val toRemove = challenges.find { it.endsWith("|$challengeId") }
        if (toRemove != null) {
            challenges.remove(toRemove)
            prefs.edit().putStringSet("crew_${crewCode}_challenges", challenges).apply()
        }
    }

    /**
     * Berechnet die Punkte für eine Crew basierend auf der Dauer und Intensität.
     * Nutzt den PointsCalculator für die Logik.
     */
    fun getPointsForCrew(email: String, crewCode: String): Int {
        val activities = getUserActivitiesForCrew(email, crewCode)
        var totalPoints = 0
        for (activity in activities) {
            val parts = activity.split("|")
            if (parts.size >= 6) {
                val duration = parts[5].toIntOrNull() ?: 0
                val intensityStr = if (parts.size >= 9) parts[8] else "MEDIUM"
                val intensity = try { WorkoutIntensity.valueOf(intensityStr) } catch (e: Exception) { WorkoutIntensity.MEDIUM }
                
                totalPoints += PointsCalculator.calculateWorkoutPoints(duration, intensity)
            } else {
                totalPoints += 1
            }
        }
        
        // Add points from completed challenges
        totalPoints += getUserChallengePoints(email, crewCode)
        
        return totalPoints
    }

    fun getUserChallengePoints(email: String, crewCode: String): Int {
        return prefs.getInt("user_${email}_${crewCode}_challenge_points", 0)
    }

    fun addUserChallengePoints(email: String, crewCode: String, points: Int) {
        val current = getUserChallengePoints(email, crewCode)
        prefs.edit().putInt("user_${email}_${crewCode}_challenge_points", current + points).apply()
    }

    fun isChallengeRewarded(email: String, challengeId: String): Boolean {
        return prefs.getBoolean("user_${email}_challenge_${challengeId}_rewarded", false)
    }

    fun markChallengeRewarded(email: String, challengeId: String) {
        prefs.edit().putBoolean("user_${email}_challenge_${challengeId}_rewarded", true).apply()
    }

    fun getUserActivities(email: String): List<String> {
        val set = prefs.getStringSet("user_${email}_activities", emptySet()) ?: emptySet()
        return set.toList().sortedByDescending { it.split("|").getOrNull(1) ?: "" }
    }

    /**
     * Gibt nur Aktivitäten zurück, die in der angegebenen Crew gemacht wurden.
     */
    fun getUserActivitiesForCrew(email: String, crewCode: String): List<String> {
        val all = getUserActivities(email)
        return all.filter { entry ->
            val parts = entry.split("|")
            // parts[4] ist der crewCode (falls vorhanden)
            parts.size >= 5 && parts[4] == crewCode
        }
    }

    /**
     * LÖSCHT ALLE AKTIVITÄTEN, PUNKTE UND CHALLENGE-FORTSCHRITTE ALLER NUTZER.
     * Nur für die Entwicklung gedacht!
     */
    fun fullResetAllData() {
        val allPrefs = prefs.all
        val editor = prefs.edit()
        for (key in allPrefs.keys) {
            // Lösche alles, was mit Aktivitäten, Challenge-Punkten oder Belohnungen zu tun hat
            if (key.endsWith("_activities") || 
                key.contains("_challenge_points") || 
                key.contains("_rewarded") ||
                key.endsWith("_challenges")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    // --- SESSION ---
    
    fun setCurrentUser(email: String) = prefs.edit().putString("current_session_user", email).apply()
    fun getCurrentUser(): String? = prefs.getString("current_session_user", null)
    fun logout() = prefs.edit().remove("current_session_user").apply()
    
    fun getJoinedCrewCode(): String? {
        val email = getCurrentUser() ?: return null
        return prefs.getString("user_${email}_crew_code", null)
    }

    fun setJoinedCrewCode(code: String?) {
        val email = getCurrentUser() ?: return
        if (code == null) prefs.edit().remove("user_${email}_crew_code").apply()
        else prefs.edit().putString("user_${email}_crew_code", code).apply()
    }

    fun getJoinedCrew(): String? = getJoinedCrewCode()
}
