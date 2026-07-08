package com.example.mobilese

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

    private lateinit var backend: AppBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        backend = AppBackend(this)
        val tvCrewName = findViewById<TextView>(R.id.tvHomeCrewName)
        val llMembersContainer = findViewById<LinearLayout>(R.id.llMembersContainer)

        val joinedCrewCode = backend.getJoinedCrewCode()
        if (joinedCrewCode != null) {
            val crewName = backend.getCrewName(joinedCrewCode)
            tvCrewName.text = "Deine Crew: $crewName"
            populateMembers(llMembersContainer, joinedCrewCode)
        } else {
            tvCrewName.text = "Keiner Crew beigetreten"
        }

        // Navigation zu den neuen Screens
        findViewById<ImageButton>(R.id.btnAddActivityIcon).setOnClickListener {
            startActivity(Intent(this, AddActivityActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnActivitiesListIcon).setOnClickListener {
            startActivity(Intent(this, ActivitiesActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnDashboardIcon).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        // Bestehende Navigation
        findViewById<ImageButton>(R.id.btnProfileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnCrewOverviewIcon).setOnClickListener {
            startActivity(Intent(this, CrewOverviewActivity::class.java))
        }
    }

    private fun populateMembers(container: LinearLayout, crewCode: String) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val members = backend.getCrewMembers(crewCode)

        for (memberEmail in members) {
            val name = backend.getUserName(memberEmail)
            val imagePath = backend.getUserData(memberEmail, "profile_image_path")
            addUserToContainer(container, inflater, name, imagePath)
        }
    }

    private fun addUserToContainer(container: LinearLayout, inflater: LayoutInflater, name: String, imagePath: String?) {
        val view = inflater.inflate(R.layout.item_crew_member, container, false)
        view.findViewById<TextView>(R.id.tvMemberName).text = name
        
        val iv = view.findViewById<ImageView>(R.id.ivMemberPhoto)
        if (!imagePath.isNullOrEmpty()) {
            val file = File(imagePath)
            if (file.exists()) {
                iv.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            }
        }
        container.addView(view)
    }

    override fun onResume() {
        super.onResume()
        val joinedCrewCode = backend.getJoinedCrewCode() ?: return
        populateMembers(findViewById(R.id.llMembersContainer), joinedCrewCode)
    }
}
