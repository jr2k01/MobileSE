package com.example.mobilese

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class WorkoutTrackingActivity : AppCompatActivity() {

    private companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.example.mobilese.fileprovider"
        const val STATE_PHOTO_PATH = "photo_path"
        const val STATE_PHOTO_URI = "photo_uri"
        const val STATE_VOICE_PATH = "voice_path"
        const val STATE_LOCATION = "location"
        const val STATE_SPORT = "sport"
    }

    private lateinit var repository: AppRepository

    private lateinit var ivPreview: ImageView
    private lateinit var tvLocation: TextView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnRecord: Button
    private lateinit var ivVoiceStatus: ImageView

    private var photoUri: Uri? = null
    private var photoPath: String = ""
    private var voicePath: String = ""
    private var locationText: String = ""
    private var selectedSport: String = ""

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    // Referenzen, damit laufende Sensor-Zugriffe beim Verlassen des Screens
    // wieder abgemeldet werden koennen.
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                showPhotoPreview()
            } else {
                Log.d("Camera", "Photo capture cancelled")
                photoPath = ""
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                requestLocation()
            }
            if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                startRecording()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_tracking)

        repository = AppRepository.get(this)

        val etSport = findViewById<EditText>(R.id.etSport)
        val tilSport = findViewById<TextInputLayout>(R.id.tilSport)
        val etDuration = findViewById<EditText>(R.id.etDuration)
        val tilDistance = findViewById<TextInputLayout>(R.id.tilDistance)
        val etDistance = findViewById<EditText>(R.id.etDistance)
        val btnSave = findViewById<Button>(R.id.btnSaveActivity)

        ivPreview = findViewById(R.id.ivWorkoutPhoto)
        tvLocation = findViewById(R.id.tvLocationStatus)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        btnRecord = findViewById(R.id.btnRecordVoice)
        ivVoiceStatus = findViewById(R.id.ivVoiceStatus)

        restoreState(savedInstanceState, etSport, tilDistance)

        // Sowohl das Feld als auch der Pfeil rechts oeffnen die Auswahl.
        val openSportPicker = View.OnClickListener {
            showSportPicker(etSport, tilDistance, etDistance)
        }
        etSport.setOnClickListener(openSportPicker)
        tilSport.setEndIconOnClickListener(openSportPicker)

        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btnGetLocation).setOnClickListener { checkLocationPermissions() }
        findViewById<Button>(R.id.btnCancelAdd).setOnClickListener { finish() }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else checkAudioPermission()
        }

        btnSave.setOnClickListener {
            saveActivity(
                sport = etSport.text.toString().trim(),
                duration = etDuration.text.toString().trim(),
                distance = etDistance.text.toString().trim(),
                btnSave = btnSave
            )
        }
    }

    /**
     * Ohne diese Sicherung war ein aufgenommenes Foto oder eine Sprachnotiz
     * nach einer Bildschirmdrehung verloren: die Activity wird dabei neu
     * erzeugt, und die Pfade standen nur in Feldern.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, photoPath)
        outState.putString(STATE_PHOTO_URI, photoUri?.toString())
        outState.putString(STATE_VOICE_PATH, voicePath)
        outState.putString(STATE_LOCATION, locationText)
        outState.putString(STATE_SPORT, selectedSport)
    }

    private fun restoreState(
        state: Bundle?,
        etSport: EditText,
        tilDistance: TextInputLayout
    ) {
        if (state == null) return
        photoPath = state.getString(STATE_PHOTO_PATH).orEmpty()
        photoUri = state.getString(STATE_PHOTO_URI)?.let { Uri.parse(it) }
        voicePath = state.getString(STATE_VOICE_PATH).orEmpty()
        locationText = state.getString(STATE_LOCATION).orEmpty()
        selectedSport = state.getString(STATE_SPORT).orEmpty()

        if (selectedSport.isNotEmpty()) {
            etSport.setText(selectedSport)
            // Sichtbarkeit des Distanzfelds passend zur Sportart wiederherstellen.
            tilDistance.visibility =
                if (Sports.tracksDistance(selectedSport)) View.VISIBLE else View.GONE
        }
        if (photoPath.isNotEmpty()) showPhotoPreview()
        if (voicePath.isNotEmpty()) tvVoiceStatus.setText(R.string.voice_recorded)
        if (locationText.isNotEmpty()) {
            tvLocation.text = getString(R.string.location_status, locationText)
        }
    }

    /**
     * Auswahl der Sportart als Liste im Dialog.
     *
     * Bewusst ein Dialog und kein aufklappendes Menue: das Popup des
     * ExposedDropdownMenu blieb auf dem Geraet unsichtbar und liess sich nicht
     * antippen. Ein Dialog wird vom System als eigene, regulaere Oberflaeche
     * gezeichnet und funktioniert unabhaengig davon.
     */
    private fun showSportPicker(
        etSport: EditText,
        tilDistance: TextInputLayout,
        etDistance: EditText
    ) {
        AlertDialog.Builder(this)
            .setTitle(R.string.train_question)
            .setItems(Sports.ALL) { _, index ->
                val sport = Sports.ALL[index]
                etSport.setText(sport)
                selectedSport = sport

                // Die Distanz wird nur bei Sportarten abgefragt, die eine haben.
                if (Sports.tracksDistance(sport)) {
                    tilDistance.visibility = View.VISIBLE
                } else {
                    tilDistance.visibility = View.GONE
                    etDistance.setText("")
                }
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    // --- Speichern ---

    private fun saveActivity(sport: String, duration: String, distance: String, btnSave: Button) {
        if (sport.isEmpty()) {
            toast(R.string.select_sport)
            return
        }
        val minutes = duration.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            toast(R.string.enter_duration)
            return
        }

        val kilometers = distance.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (Sports.tracksDistance(sport) && kilometers <= 0.0) {
            toast(R.string.enter_distance)
            return
        }

        // Eine laufende Aufnahme zuerst abschliessen, sonst waere die Datei
        // beim Hochladen noch unvollstaendig.
        if (isRecording) stopRecording()

        btnSave.isEnabled = false
        lifecycleScope.launch {
            val saved = repository.addActivity(
                sport = sport,
                photoPath = photoPath,
                location = locationText.ifEmpty { getString(R.string.location_not_shared) },
                duration = minutes.toString(),
                voicePath = voicePath,
                distance = kilometers.toString(),
                intensity = Sports.intensityFor(sport).name
            )

            if (saved) {
                toast(R.string.activity_saved)
                finish()
            } else {
                btnSave.isEnabled = true
                toast(R.string.activity_save_failed)
            }
        }
    }

    // --- Foto ---

    private fun takePhoto() {
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (directory == null) {
            toast(R.string.photo_storage_unavailable)
            return
        }

        val file = File(directory, "workout_${System.currentTimeMillis()}.jpg")
        photoPath = file.absolutePath
        photoUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)

        try {
            photoUri?.let { takePictureLauncher.launch(it) }
        } catch (e: android.content.ActivityNotFoundException) {
            // Auf Geraeten ohne Kamera-App wuerde launch() sonst die App beenden.
            Log.e("Camera", "No camera app available: ${e.message}")
            photoPath = ""
            toast(R.string.no_camera_app)
        }
    }

    /**
     * Zeigt das aufgenommene Foto in der Vorschau.
     *
     * Die ImageView ist im Layout als Platzhalter gestaltet: eingefaerbtes
     * Kamera-Symbol, viel Innenabstand, halb durchsichtig. Alle drei
     * Eigenschaften muessen zurueckgesetzt werden, sonst wird das Foto flaechig
     * eingefaerbt angezeigt. Der Farbfilter blieb bisher stehen - die Vorschau
     * war deshalb ein grauer Fleck statt eines Bildes.
     *
     * Ohne runden Zuschnitt: das Feld ist querformatig, ein Kreis wurde darin
     * zur Ellipse verzerrt.
     */
    private fun showPhotoPreview() {
        ivPreview.setPadding(0, 0, 0, 0)
        ivPreview.alpha = 1.0f
        ImageViewCompat.setImageTintList(ivPreview, null)
        ImageLoader.into(ivPreview, photoPath)
    }

    // --- Sprachnotiz ---

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun startRecording() {
        val file = File(getExternalFilesDir(null), "voice_${System.currentTimeMillis()}.3gp")

        // Der parameterlose Konstruktor ist seit API 31 deprecated.
        @Suppress("DEPRECATION")
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else MediaRecorder()

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            voicePath = file.absolutePath
            isRecording = true
            btnRecord.setText(R.string.stop_short)
            tvVoiceStatus.setText(R.string.stop_recording)
            ivVoiceStatus.setColorFilter(ContextCompat.getColor(this, R.color.error))
        } catch (e: Exception) {
            // Recorder wieder freigeben, sonst bleibt das Mikrofon belegt.
            Log.e("Audio", "Start recording failed: ${e.message}")
            try {
                recorder.release()
            } catch (ignored: Exception) {
            }
            mediaRecorder = null
            isRecording = false
            voicePath = ""
            toast(R.string.recording_failed)
        }
    }

    private fun stopRecording() {
        mediaRecorder?.let { recorder ->
            try {
                recorder.stop()
            } catch (e: Exception) {
                // stop() wirft, wenn noch keine Daten aufgenommen wurden. Die
                // Datei ist dann unbrauchbar und wird verworfen.
                Log.e("Audio", "Stop recording failed: ${e.message}")
                File(voicePath).delete()
                voicePath = ""
            }
            try {
                recorder.release()
            } catch (e: Exception) {
                Log.e("Audio", "Release failed: ${e.message}")
            }
        }

        mediaRecorder = null
        isRecording = false
        btnRecord.setText(R.string.record_short)
        tvVoiceStatus.setText(
            if (voicePath.isEmpty()) R.string.recording_failed else R.string.voice_recorded
        )
        ivVoiceStatus.setColorFilter(ContextCompat.getColor(this, R.color.accent))
    }

    // --- Standort ---

    private fun checkLocationPermissions() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {
            requestLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun requestLocation() {
        tvLocation.setText(R.string.location_fetching)

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = manager

        // Einen eventuell noch laufenden Versuch zuerst abmelden.
        stopLocationUpdates()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationText = String.format(
                    Locale.getDefault(),
                    "Lat: %.4f, Lon: %.4f",
                    location.latitude,
                    location.longitude
                )
                tvLocation.text = getString(R.string.location_status, locationText)
                // Ein Fix genuegt - danach abmelden, statt weiter zu orten.
                stopLocationUpdates()
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locationListener = listener

        // Jeder Provider einzeln: fehlt einer auf dem Geraet, wirft
        // requestLocationUpdates eine IllegalArgumentException und der zweite
        // Provider waere nie angefragt worden.
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val active = providers.count { provider ->
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener)
                true
            } catch (e: SecurityException) {
                tvLocation.setText(R.string.location_permission_denied)
                false
            } catch (e: IllegalArgumentException) {
                Log.d("Location", "Provider $provider is not available")
                false
            }
        }

        if (active == 0) {
            locationListener = null
            tvLocation.setText(R.string.location_unavailable)
        }
    }

    /**
     * Meldet die Standortabfrage ab. Ohne das lief die Ortung weiter, wenn der
     * Nutzer den Screen verliess, bevor ein Fix vorlag - inklusive
     * Stromverbrauch und einer gehaltenen Referenz auf die Activity.
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
        if (isRecording) stopRecording()
        stopLocationUpdates()
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
