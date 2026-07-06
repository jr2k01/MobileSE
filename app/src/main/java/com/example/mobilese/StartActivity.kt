package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnCreate = findViewById<Button>(R.id.btnCreateCrew)
        val btnJoin = findViewById<Button>(R.id.btnJoinCrew)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)

        btnCreate.setOnClickListener {
            startActivity(Intent(this, CreateCrewActivity::class.java))
        }

        btnJoin.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Crew beitreten")
            val input = EditText(this)
            input.hint = "Code eingeben"
            builder.setView(input)
            builder.setPositiveButton("Beitreten") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    Toast.makeText(this, "Beigetreten!", Toast.LENGTH_SHORT).show()
                }
            }
            builder.show()
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
