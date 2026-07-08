package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrewOverviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_overview)

        val backend = AppBackend(this)
        val currentUser = backend.getCurrentUser() ?: return
        val joinedCrewCode = backend.getJoinedCrewCode() ?: return
        val crewName = backend.getCrewName(joinedCrewCode)

        findViewById<TextView>(R.id.tvCrewNameDisplay).text = crewName
        
        // Mitgliederliste (Text-Format für die Übersicht)
        val members = backend.getCrewMembers(joinedCrewCode)
        val membersText = members.joinToString("\n") { email -> "- ${backend.getUserName(email)}" }
        findViewById<TextView>(R.id.tvMembersList).text = membersText

        findViewById<Button>(R.id.btnLeaveCrew).setOnClickListener {
            backend.leaveCrew(joinedCrewCode, currentUser)
            backend.setJoinedCrewCode(null)
            
            Toast.makeText(this, "Crew '$crewName' verlassen", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(this, StartActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}
