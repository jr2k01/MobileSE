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

    fun getUserName(email: String): String = prefs.getString("user_${email}_name", "Unbekannt") ?: "Unbekannt"
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

    fun getCrewName(code: String): String = prefs.getString("crew_data_${code}_name", "Unbekannte Crew") ?: "Unbekannte Crew"
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
     * Format: sport|timestamp|photoPath|location
     */
    fun addActivity(email: String, sport: String, photoPath: String, location: String) {
        val currentActivities = getUserActivities(email).toMutableList()
        val timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        
        // Wir nutzen ein Trennzeichen, das unwahrscheinlich in Pfaden vorkommt
        val activityEntry = "$sport|$timestamp|$photoPath|$location"
        currentActivities.add(activityEntry)
        
        prefs.edit().putStringSet("user_${email}_activities", currentActivities.toSet()).apply()
    }

    fun getUserActivities(email: String): List<String> {
        val set = prefs.getStringSet("user_${email}_activities", emptySet()) ?: emptySet()
        return set.toList().sortedByDescending { it.split("|").getOrNull(1) ?: "" }
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
