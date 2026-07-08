package com.example.mobilese

import android.content.Context
import android.content.SharedPreferences

/**
 * Ein simuliertes Backend für die App.
 * Speichert Daten in SharedPreferences, aber strukturiert sie so, dass 
 * individuelle Nutzerdaten und geteilte Crew-Daten möglich sind.
 */
class AppBackend(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("CrewFitDatabase", Context.MODE_PRIVATE)

    // --- NUTZER VERWALTUNG ---

    fun registerUser(email: String, password: String, name: String, birthDate: String): Boolean {
        if (prefs.contains("user_${email}_password")) return false // Nutzer existiert schon
        
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

    // --- CREW VERWALTUNG (Geteilt via Code) ---

    /**
     * Erstellt eine Crew. Der 'code' ist die eindeutige ID.
     */
    fun createCrew(crewName: String, creatorEmail: String, code: String) {
        prefs.edit().apply {
            putString("crew_data_${code}_name", crewName)
            
            // Mitgliederliste für diesen Code initialisieren
            val members = getCrewMembers(code).toMutableSet()
            members.add(creatorEmail)
            putStringSet("crew_data_${code}_members", members)
            apply()
        }
    }

    /**
     * Tritt einer Crew via Code bei.
     */
    fun joinCrew(code: String, userEmail: String): Boolean {
        if (!prefs.contains("crew_data_${code}_name")) return false // Code existiert nicht
        
        val members = getCrewMembers(code).toMutableSet()
        members.add(userEmail)
        prefs.edit().putStringSet("crew_data_${code}_members", members).apply()
        return true
    }

    fun getCrewName(code: String): String = prefs.getString("crew_data_${code}_name", "Unbekannte Crew") ?: "Unbekannte Crew"

    fun getCrewMembers(code: String): Set<String> {
        return prefs.getStringSet("crew_data_${code}_members", emptySet()) ?: emptySet()
    }
    
    fun leaveCrew(code: String, userEmail: String) {
        val members = getCrewMembers(code).toMutableSet()
        members.remove(userEmail)
        prefs.edit().putStringSet("crew_data_${code}_members", members).apply()
    }

    // --- SESSION ---
    
    fun setCurrentUser(email: String) = prefs.edit().putString("current_session_user", email).apply()
    fun getCurrentUser(): String? = prefs.getString("current_session_user", null)
    fun logout() = prefs.edit().remove("current_session_user").apply()
    
    // Wir speichern den 'code' in der Session, nicht den Namen
    fun setJoinedCrewCode(code: String?) = prefs.edit().putString("session_crew_code", code).apply()
    fun getJoinedCrewCode(): String? = prefs.getString("session_crew_code", null)
    
    // Kompatibilitätsschicht für alten Code (optional)
    fun getJoinedCrew(): String? = getJoinedCrewCode() 
    fun setJoinedCrew(code: String?) = setJoinedCrewCode(code)
}
