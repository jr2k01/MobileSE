package com.example.mobilese

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class WorkoutTrackingActivity : AppCompatActivity() {

    private lateinit var backend: AppRepository
    private var currentUser: String = ""
    
    private lateinit var ivPreview: ImageView
    private lateinit var tvLocation: TextView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnRecord: Button
    
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var currentPath: String = ""
    private var currentLocationString: String = "Waiting for GPS..."
    
    private var mediaRecorder: MediaRecorder? = null
    private var voicePath: String = ""
    private var isRecording = false

    // Referenzen, damit laufende Sensor-Zugriffe beim Verlassen des Screens
    // wieder abgemeldet werden koennen.
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ivPreview.setPadding(0, 0, 0, 0)
            ivPreview.alpha = 1.0f
            ivPreview.load(photoUri) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
            currentPath = photoFile?.absolutePath ?: ""
        } else {
            Log.e("Camera", "Photo capture failed or cancelled")
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getLocation()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_tracking)

        backend = AppRepository(this)
        currentUser = backend.getCurrentUser() ?: run { finish(); return }

        val actvSport = findViewById<AutoCompleteTextView>(R.id.actvSport)
        val etDuration = findViewById<EditText>(R.id.etDuration)
        val tilDistance = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilDistance)
        val etDistance = findViewById<EditText>(R.id.etDistance)
        ivPreview = findViewById(R.id.ivWorkoutPhoto)
        tvLocation = findViewById(R.id.tvLocationStatus)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        btnRecord = findViewById(R.id.btnRecordVoice)
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnGetLocation = findViewById<Button>(R.id.btnGetLocation)
        val btnSave = findViewById<Button>(R.id.btnSaveActivity)
        val btnCancel = findViewById<Button>(R.id.btnCancelAdd)

        val sports = arrayOf("Running", "Cycling", "Swimming", "Yoga", "Football", "Gym")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sports)
        actvSport.setAdapter(adapter)

        actvSport.setOnItemClickListener { _, _, position, _ ->
            val selection = adapter.getItem(position)
            if (selection == "Running") {
                tilDistance.visibility = android.view.View.VISIBLE
            } else {
                tilDistance.visibility = android.view.View.GONE
                etDistance.setText("")
            }
        }

        btnTakePhoto.setOnClickListener {
            preparePhotoFile()
            photoUri?.let { takePictureLauncher.launch(it) }
        }

        btnGetLocation.setOnClickListener {
            checkLocationPermissions()
        }
        
        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkAudioPermissions()
            }
        }

        btnSave.setOnClickListener {
            val sport = actvSport.text.toString()
            val duration = etDuration.text.toString().trim()
            val distance = etDistance.text.toString().trim().ifEmpty { "0" }
            
            if (sport.isEmpty()) {
                Toast.makeText(this, "Please select a sport!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (duration.isEmpty()) {
                Toast.makeText(this, "Please enter duration!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sport == "Running" && distance == "0") {
                Toast.makeText(this, "Please enter kilometers run!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intensity = when (sport) {
                "Yoga" -> WorkoutIntensity.LOW
                "Football" -> WorkoutIntensity.MEDIUM
                "Running", "Swimming", "Cycling", "Gym" -> WorkoutIntensity.HIGH
                else -> WorkoutIntensity.MEDIUM
            }.name

            lifecycleScope.launch {
                backend.addActivity(currentUser, sport, currentPath, currentLocationString, duration, voicePath, distance, intensity)
                Toast.makeText(this@WorkoutTrackingActivity, "Activity saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        btnCancel.setOnClickListener { finish() }
    }

    private fun checkAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun startRecording() {
        val fileName = "voice_${System.currentTimeMillis()}.3gp"
        val file = File(getExternalFilesDir(null), fileName)
        voicePath = file.absolutePath

        // Der parameterlose Konstruktor ist seit API 31 deprecated.
        @Suppress("DEPRECATION")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        mediaRecorder = recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(voicePath)
            try {
                prepare()
                start()
                isRecording = true
                btnRecord.text = "STOP"
                tvVoiceStatus.text = getString(R.string.stop_recording)
                findViewById<ImageView>(R.id.ivVoiceStatus).setColorFilter(ContextCompat.getColor(this@WorkoutTrackingActivity, R.color.error))
            } catch (e: Exception) {
                // Recorder wieder freigeben, sonst bleibt das Mikrofon belegt.
                Log.e("Audio", "Start recording failed: ${e.message}")
                try { release() } catch (ignored: Exception) {}
                mediaRecorder = null
                isRecording = false
                voicePath = ""
                Toast.makeText(this@WorkoutTrackingActivity, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                // stop() wirft, wenn noch keine Daten aufgenommen wurden
                Log.e("Audio", "Stop recording failed: ${e.message}")
            }
            try {
                release()
            } catch (e: Exception) {
                Log.e("Audio", "Release failed: ${e.message}")
            }
        }
        mediaRecorder = null
        isRecording = false
        btnRecord.text = "REC"
        tvVoiceStatus.text = getString(R.string.voice_recorded)
        findViewById<ImageView>(R.id.ivVoiceStatus).setColorFilter(ContextCompat.getColor(this, R.color.accent))
    }

    private fun preparePhotoFile() {
        val fileName = "workout_${System.currentTimeMillis()}.jpg"
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        photoFile = File(storageDir, fileName)
        photoUri = FileProvider.getUriForFile(this, "com.example.mobilese.fileprovider", photoFile!!)
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun getLocation() {
        tvLocation.text = getString(R.string.location_fetching)

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = manager

        // Einen eventuell noch laufenden Versuch zuerst abmelden.
        stopLocationUpdates()

        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocationString = String.format(
                        Locale.getDefault(),
                        "Lat: %.4f, Lon: %.4f",
                        location.latitude,
                        location.longitude
                    )
                    tvLocation.text = getString(R.string.location_status, currentLocationString)
                    stopLocationUpdates()
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener

            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)

        } catch (e: SecurityException) {
            tvLocation.text = getString(R.string.location_permission_denied)
        }
    }

    /**
     * Meldet die Standortabfrage ab. Ohne das lief die Ortung weiter, wenn der
     * Nutzer den Screen verliess, bevor ein Fix vorlag - inklusive Stromverbrauch
     * und einer gehaltenen Referenz auf die Activity.
     */
    private fun stopLocationUpdates() {
        val listener = locationListener ?: return
        try {
            locationManager?.removeUpdates(listener)
        } catch (e: SecurityException) {
            Log.e("Location", "Could not remove updates: ${e.message}")
        }
        locationListener = null
    }

    override fun onStop() {
        super.onStop()
        // Laufende Aufnahme beenden, sonst bleibt das Mikrofon belegt.
        if (isRecording) {
            stopRecording()
        }
        stopLocationUpdates()
    }
}
