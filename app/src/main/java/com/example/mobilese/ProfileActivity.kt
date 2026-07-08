package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var ivProfilePicture: ImageView
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            ivProfilePicture.setImageURI(it)
            saveImageToInternalStorage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val etName = findViewById<EditText>(R.id.etName)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)

        btnBack.setOnClickListener {
            finish()
        }

        ivProfilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Bestehende Daten laden
        etName.setText(sharedPref.getString("user_name", ""))
        etBirthDate.setText(sharedPref.getString("user_birthdate", ""))
        etEmail.setText(sharedPref.getString("registered_email", ""))
        etAge.setText(sharedPref.getString("user_age", ""))
        etHeight.setText(sharedPref.getString("user_height", ""))
        etWeight.setText(sharedPref.getString("user_weight", ""))

        // Profilbild laden, falls vorhanden
        val imagePath = sharedPref.getString("profile_image_path", null)
        if (imagePath != null) {
            val imgFile = File(imagePath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                ivProfilePicture.setImageBitmap(bitmap)
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val birthDate = etBirthDate.text.toString()
            val email = etEmail.text.toString()
            val age = etAge.text.toString()
            val height = etHeight.text.toString()
            val weight = etWeight.text.toString()

            val editor = sharedPref.edit()
            editor.putString("user_name", name)
            editor.putString("user_birthdate", birthDate)
            editor.putString("registered_email", email)
            editor.putString("user_age", age)
            editor.putString("user_height", height)
            editor.putString("user_weight", weight)
            editor.apply()

            Toast.makeText(this, "Profil gespeichert!", Toast.LENGTH_SHORT).show()

            val targetActivity = if (sharedPref.contains("joined_crew")) {
                HomeActivity::class.java
            } else {
                StartActivity::class.java
            }

            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish()
        }

        btnLogout.setOnClickListener {
            // Logout-Logik: Zurück zur MainActivity (Login-Screen)
            // Wir löschen den Backstack, damit man nicht mit "Zurück" wieder ins Profil kommt
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "profile_picture.jpg")
            val outputStream = FileOutputStream(file)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putString("profile_image_path", file.absolutePath).apply()
            
            Toast.makeText(this, "Profilbild aktualisiert!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Speichern des Bildes", Toast.LENGTH_SHORT).show()
        }
    }
}
