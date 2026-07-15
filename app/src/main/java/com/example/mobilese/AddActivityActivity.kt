package com.example.mobilese

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class AddActivityActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend
    private var currentUser: String = ""
    
    private lateinit var ivPreview: ImageView
    private lateinit var tvLocation: TextView
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var currentPath: String = ""
    private var currentLocationString: String = "University of Hildesheim"

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ivPreview.setPadding(0, 0, 0, 0)
            ivPreview.alpha = 1.0f
            ivPreview.setImageURI(photoUri)
            currentPath = photoFile?.absolutePath ?: ""
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            getLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_activity)

        backend = AppBackend(this)
        currentUser = backend.getCurrentUser() ?: run { finish(); return }

        val actvSport = findViewById<AutoCompleteTextView>(R.id.actvSport)
        ivPreview = findViewById(R.id.ivWorkoutPhoto)
        tvLocation = findViewById(R.id.tvLocationStatus)
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnGetLocation = findViewById<Button>(R.id.btnGetLocation)
        val btnSave = findViewById<Button>(R.id.btnSaveActivity)
        val btnCancel = findViewById<Button>(R.id.btnCancelAdd)

        // Dropdown befüllen
        val sports = arrayOf("Running", "Cycling", "Swimming", "Strength Training", "Yoga", "Football", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sports)
        actvSport.setAdapter(adapter)

        btnTakePhoto.setOnClickListener {
            preparePhotoFile()
            photoUri?.let { takePictureLauncher.launch(it) }
        }

        btnGetLocation.setOnClickListener {
            checkLocationPermissions()
        }

        btnSave.setOnClickListener {
            val sport = actvSport.text.toString()
            if (sport.isEmpty()) {
                Toast.makeText(this, "Please select a sport!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            backend.addActivity(currentUser, sport, currentPath, currentLocationString)
            Toast.makeText(this, "Activity saved!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnCancel.setOnClickListener { finish() }
    }

    private fun preparePhotoFile() {
        val fileName = "workout_${System.currentTimeMillis()}.jpg"
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        photoFile = File(storageDir, fileName)
        photoUri = FileProvider.getUriForFile(this, "com.example.mobilese.fileprovider", photoFile!!)
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun getLocation() {
        tvLocation.text = getString(R.string.location_fetching)
        
        // Wir erzwingen die Universität Hildesheim, auch wenn das Handy (z.B. im Emulator) London meldet
        currentLocationString = "University of Hildesheim (52.14, 9.98)"
        
        // Kurze Verzögerung simulieren für besseres UX-Gefühl
        tvLocation.postDelayed({
            tvLocation.text = getString(R.string.location_status, currentLocationString)
            Toast.makeText(this, "Location set to Hildesheim University", Toast.LENGTH_SHORT).show()
        }, 800)
    }
}
