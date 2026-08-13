package com.example.mobilese

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var ivProfilePicture: ImageView
    private var currentUserEmail: String = ""

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { storeProfilePicture(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_profile)

        repository = AppRepository.get(this)
        currentUserEmail = repository.getCurrentUser() ?: run {
            finish()
            return
        }

        val etName = findViewById<EditText>(R.id.etName)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        ivProfilePicture.setOnClickListener { pickImageLauncher.launch("image/*") }

        etEmail.setText(currentUserEmail)

        // Ein einziger Abruf fuer das ganze Formular. Vorher wurde fuer jedes
        // Feld einzeln dieselbe Zeile aus der Datenbank geholt - sechs
        // Abfragen fuer sechs Felder.
        lifecycleScope.launch {
            val profile = repository.getProfile(currentUserEmail) ?: return@launch
            etName.setText(profile.name.orEmpty())
            etBirthDate.setText(profile.birthdate.orEmpty())
            etAge.setText(profile.age.orEmpty())
            etHeight.setText(profile.height.orEmpty())
            etWeight.setText(profile.weight.orEmpty())
            ImageLoader.into(
                ivProfilePicture,
                profile.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
        }

        btnSave.setOnClickListener {
            setBusy(true, btnSave, btnLogout)
            lifecycleScope.launch {
                val saved = repository.saveUserProfile(
                    currentUserEmail,
                    etName.text.toString().trim(),
                    etAge.text.toString().trim(),
                    etHeight.text.toString().trim(),
                    etWeight.text.toString().trim(),
                    etBirthDate.text.toString().trim()
                )
                setBusy(false, btnSave, btnLogout)

                if (!saved) {
                    toast(R.string.profile_save_failed)
                    return@launch
                }
                toast(R.string.profile_saved)
                val target =
                    if (repository.getJoinedCrewCode() != null) MainHubActivity::class.java
                    else CrewLandingActivity::class.java
                startActivity(Intent(this@ProfileActivity, target))
                finish()
            }
        }

        btnLogout.setOnClickListener {
            // logout() ist suspend, weil es zusaetzlich die Supabase-Sitzung
            // beendet und nicht nur den lokalen Sitzungsmarker loescht.
            lifecycleScope.launch {
                repository.logout()
                openLogin()
            }
        }

        findViewById<Button>(R.id.btnDeleteProfile).setOnClickListener {
            confirmDeletion()
        }
    }

    private fun confirmDeletion() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile_title)
            .setMessage(R.string.delete_profile_message)
            .setPositiveButton(R.string.delete_profile_confirm) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteUserProfile()
                    Toast.makeText(this@ProfileActivity, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                    openLogin()
                }
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    /**
     * Uebernimmt ein ausgewaehltes Bild als Profilbild.
     *
     * Dekodieren und Schreiben laufen auf [Dispatchers.IO]: das Kopieren der
     * Originaldatei lief vorher im Main-Thread, was bei grossen Fotos sichtbar
     * ruckelte. Gespeichert wird eine verkleinerte Fassung - fuer ein
     * Profilbild reicht das, und der Upload wird um ein Vielfaches kleiner.
     */
    private fun storeProfilePicture(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmap = ImageLoader.decodeScaled(this@ProfileActivity, uri)
                    ?: return@withContext null
                try {
                    val file = File(filesDir, "profile_${currentUserEmail.replace('@', '_')}.jpg")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    file.absolutePath to bitmap
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                toast(R.string.image_save_failed)
                return@launch
            }

            val (path, bitmap) = result
            ivProfilePicture.setImageBitmap(bitmap)
            toast(if (repository.saveUserImage(path)) R.string.image_saved else R.string.image_save_failed)
        }
    }

    private fun openLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setBusy(busy: Boolean, vararg views: View) {
        views.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
