package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tvCrewName = findViewById<TextView>(R.id.tvHomeCrewName)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)
        val btnCrew = findViewById<ImageButton>(R.id.btnCrewOverviewIcon)
        val llMembersContainer = findViewById<LinearLayout>(R.id.llMembersContainer)

        // Crew-Name aus SharedPreferences laden
        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        val joinedCrew = sharedPref.getString("joined_crew", "Keine Crew")
        tvCrewName.text = "Deine Crew: $joinedCrew"

        // Mitglieder-Liste befüllen
        populateMembers(llMembersContainer)

        btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        btnCrew.setOnClickListener {
            val intent = Intent(this, CrewOverviewActivity::class.java)
            startActivity(intent)
        }
    }

    private fun populateMembers(container: LinearLayout) {
        container.removeAllViews()
        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        val inflater = LayoutInflater.from(this)

        // 1. Aktuellen User hinzufügen
        val userName = sharedPref.getString("user_name", "Ich") ?: "Ich"
        val imagePath = sharedPref.getString("profile_image_path", null)
        
        addUserToContainer(container, inflater, userName, imagePath)

        // 2. Demo-Mitglieder hinzufügen (da wir noch kein Backend haben)
        addUserToContainer(container, inflater, "Max", null)
        addUserToContainer(container, inflater, "Erika", null)
        addUserToContainer(container, inflater, "Leon", null)
    }

    private fun addUserToContainer(container: LinearLayout, inflater: LayoutInflater, name: String, imagePath: String?) {
        val memberView = inflater.inflate(R.layout.item_crew_member, container, false)
        val tvName = memberView.findViewById<TextView>(R.id.tvMemberName)
        val ivPhoto = memberView.findViewById<ImageView>(R.id.ivMemberPhoto)

        tvName.text = name
        
        if (imagePath != null) {
            val imgFile = File(imagePath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                ivPhoto.setImageBitmap(bitmap)
            }
        }
        
        container.addView(memberView)
    }

    override fun onResume() {
        super.onResume()
        // Daten aktualisieren, falls man vom Profil zurückkommt
        val llMembersContainer = findViewById<LinearLayout>(R.id.llMembersContainer)
        populateMembers(llMembersContainer)
    }
}
