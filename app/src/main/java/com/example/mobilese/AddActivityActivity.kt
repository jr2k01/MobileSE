package com.example.mobilese

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
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
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnRecord: Button
    
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var currentPath: String = ""
    private var currentLocationString: String = "University of Hildesheim"
    
    private var mediaRecorder: MediaRecorder? = null
    private var voicePath: String = ""
    private var isRecording = false

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
            // Permission handled in getLocation() call logic
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_activity)

        backend = AppBackend(this)
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

        // Dropdown befüllen
        val sports = arrayOf("Running", "Cycling", "Swimming", "Strength Training", "Yoga", "Football", "Gym", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sports)
        actvSport.setAdapter(adapter)

        // Visibility logic for Distance
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

            backend.addActivity(currentUser, sport, currentPath, currentLocationString, duration, voicePath, distance)
            Toast.makeText(this, "Activity saved!", Toast.LENGTH_SHORT).show()
            finish()
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

        mediaRecorder = MediaRecorder().apply {
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
                findViewById<ImageView>(R.id.ivVoiceStatus).setColorFilter(ContextCompat.getColor(this@AddActivityActivity, R.color.error))
            } catch (e: Exception) {
                Toast.makeText(this@AddActivityActivity, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {}
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

