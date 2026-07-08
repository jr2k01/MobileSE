package com.example.mobilese

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
    private var currentUserEmail: String = ""
    private lateinit var backend: AppBackend

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            ivProfilePicture.setImageURI(it)
            saveImageToInternalStorage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        backend = AppBackend(this)
        currentUserEmail = backend.getCurrentUser() ?: run {
            finish()
            return
        }

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

        btnBack.setOnClickListener { finish() }

        ivProfilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Daten für aktuellen Nutzer laden
        etName.setText(backend.getUserData(currentUserEmail, "name"))
        etBirthDate.setText(backend.getUserData(currentUserEmail, "birthdate"))
        etEmail.setText(currentUserEmail)
        etAge.setText(backend.getUserData(currentUserEmail, "age"))
        etHeight.setText(backend.getUserData(currentUserEmail, "height"))
        etWeight.setText(backend.getUserData(currentUserEmail, "weight"))

        // Profilbild laden
        val imagePath = backend.getUserData(currentUserEmail, "profile_image_path")
        if (imagePath.isNotEmpty()) {
            val imgFile = File(imagePath)
            if (imgFile.exists()) {
                ivProfilePicture.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
            }
        }

        btnSave.setOnClickListener {
            backend.saveUserProfile(
                currentUserEmail,
                etName.text.toString(),
                etAge.text.toString(),
                etHeight.text.toString(),
                etWeight.text.toString(),
                etBirthDate.text.toString()
            )

            Toast.makeText(this, "Profil gespeichert!", Toast.LENGTH_SHORT).show()

            val targetActivity = if (backend.getJoinedCrew() != null) HomeActivity::class.java else StartActivity::class.java
            startActivity(Intent(this, targetActivity))
            finish()
        }

        btnLogout.setOnClickListener {
            backend.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "profile_${currentUserEmail.replace("@", "_")}.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            
            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }

            // Pfad im Backend speichern
            backend.saveUserImagePath(currentUserEmail, file.absolutePath)
            
            Toast.makeText(this, "Bild gespeichert!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Speichern", Toast.LENGTH_SHORT).show()
        }
    }
}
