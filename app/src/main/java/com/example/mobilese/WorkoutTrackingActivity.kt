package com.example.mobilese

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

class WorkoutTrackingActivity : AppCompatActivity() {

    companion object {
        /**
         * Das Workout von der Uhr, das hier ergaenzt werden soll - erkannt an
         * seinem Ende in Millisekunden.
         *
         * Ohne diesen Zusatz bleibt der Bildschirm, was er war: ein leeres
         * Formular. Er sucht sich also nicht von sich aus ein wartendes
         * Workout, sonst waere beim Antippen von "+" ploetzlich etwas
         * ausgefuellt, das man gar nicht gemeint hat.
         */
        const val EXTRA_PENDING_AT = "pending_at"

        fun intent(context: Context, workout: PendingWorkout): Intent =
            Intent(context, WorkoutTrackingActivity::class.java)
                .putExtra(EXTRA_PENDING_AT, workout.endedAt)

        private const val FILE_PROVIDER_AUTHORITY = "com.example.mobilese.fileprovider"
        private const val STATE_PHOTO_PATH = "photo_path"
        private const val STATE_PHOTO_URI = "photo_uri"
        private const val STATE_VOICE_PATH = "voice_path"
        private const val STATE_LOCATION = "location"
        private const val STATE_SPORT = "sport"
        private const val STATE_LATITUDE = "latitude"
        private const val STATE_LONGITUDE = "longitude"
        private const val STATE_PENDING_AT = "pending_at_state"
        private const val STATE_PARTNER_IDS = "partner_ids_state"
    }

    private lateinit var repository: AppRepository

    private lateinit var ivPreview: ImageView
    private lateinit var tvLocation: TextView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnRecord: Button
    private lateinit var ivVoiceStatus: ImageView
    private lateinit var llMapPreview: LinearLayout
    private lateinit var ivLocationMap: ImageView
    private lateinit var tvMapAttribution: TextView

    /** Die zuletzt ermittelte Position, Grundlage fuer die Kartenvorschau. */
    private var pickedLatitude: Double? = null
    private var pickedLongitude: Double? = null

    /** Ein selbst vergebener Ortsname, getrennt von den Geocoder-Vorschlaegen. */
    private var customLocationName: String = ""

    private var photoUri: Uri? = null
    private var photoPath: String = ""
    private var voicePath: String = ""
    private var locationText: String = ""
    private var selectedSport: String = ""

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    /**
     * Das Workout von der Uhr, das gerade ergaenzt wird - und sein Puls.
     *
     * Der Puls wird mitgefuehrt und nicht bei jedem Speichern neu aus der
     * Ablage geholt: die Uhr hat waehrend des Workouts gemessen, das ist die
     * genauere Zahl als alles, was sich hinterher aus Health Connect
     * zusammensuchen laesst.
     */
    private var pendingEndedAt: Long? = null
    private var watchAvgHeartRate: Int? = null
    private var watchMaxHeartRate: Int? = null

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
            if (permissions[Manifest.permission.CAMERA] == true) {
                takePhoto()
            } else if (permissions.containsKey(Manifest.permission.CAMERA)) {
                toast(R.string.camera_permission_denied)
            }
        }

    // --- Gemeinsames Training ---

    /**
     * Wer mittrainiert hat - leer, wenn allein.
     *
     * Ueberlebt das Drehen des Geraets: die Kopplung war ein eigener
     * Bildschirm und womoeglich ein Weg quer durch den Raum - sie noch einmal
     * zu verlangen, waere die schlechteste Antwort auf eine Drehung.
     */
    private var partnerIds: List<String> = emptyList()

    private val pickPartner =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (TrainingPartnerActivity.isJoint(result.data)) {
                partnerIds = JointSession.partners.map { it.id }
            }
            applyJointSession()
            showPartnerBanner()
            CoachTour.start(this, Tours.WORKOUT)
        }

    /**
     * Uebernimmt ein gemeinsam absolviertes Training in das Formular.
     *
     * Sportart und Dauer stehen dann fest: die eine hat der Fuehrende
     * gewaehlt, die andere hat die Uhr gemessen. Beide Felder werden gesperrt -
     * waeren sie aenderbar, koennte einer der beiden hinterher etwas anderes
     * eintragen als tatsaechlich zusammen trainiert wurde.
     *
     * Alles Weitere - Foto, Sprachnotiz, Ort - traegt jeder fuer sich ein,
     * wann und wo er will. Die Verbindung wird dafuer nicht mehr gebraucht.
     */
    private fun applyJointSession() {
        if (!JointSession.isFinished()) return

        val sport = JointSession.sport
        val minutes = JointSession.seconds?.let { it / 60 }

        if (sport != null) {
            selectedSport = sport
            findViewById<EditText>(R.id.etSport).setText(sport)
            findViewById<TextInputLayout>(R.id.tilSport).isEnabled = false
            findViewById<View>(R.id.tilDistance).visibility =
                if (Sports.tracksDistance(sport)) View.VISIBLE else View.GONE
        }
        if (minutes != null) {
            findViewById<EditText>(R.id.etDuration).setText(minutes.toString())
            findViewById<TextInputLayout>(R.id.tilDuration).isEnabled = false
        }
    }

    /**
     * Die erste Frage: allein oder zu zweit.
     *
     * Vor allem anderen, weil die Antwort den Wert des Workouts verdoppelt -
     * und weil die Kopplung waehrend des Trainings stattfindet, nicht
     * danach. Wer erst das Formular ausfuellt und dann gefragt wird, steht
     * womoeglich schon wieder allein da.
     *
     * Nicht abbrechbar: eine der beiden Antworten muss kommen. Der Weg zurueck
     * ist der Pfeil in der Kopfzeile.
     */
    private fun askIfTrainingTogether() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.together_title)
            .setMessage(R.string.together_message)
            .setPositiveButton(R.string.together_with) { _, _ ->
                pickPartner.launch(TrainingPartnerActivity.intent(this))
            }
            .setNegativeButton(R.string.together_alone) { _, _ ->
                CoachTour.start(this, Tours.WORKOUT)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Der Hinweis ueber dem Formular, dass dieses Workout doppelt zaehlt.
     *
     * Der Name wird nachgeladen: aus der Kopplung kommt nur die Kennung
     * zurueck, und den Namen kennt die Crew.
     */
    private fun showPartnerBanner() {
        val banner = findViewById<TextView>(R.id.tvPartnerBanner)
        if (partnerIds.isEmpty()) {
            banner.visibility = View.GONE
            return
        }

        banner.visibility = View.VISIBLE
        lifecycleScope.launch {
            // Die Namen stehen meist schon in der Sitzung; nur wenn die nach
            // einer Drehung weg ist, werden sie nachgeladen.
            val names = partnerIds.map { id ->
                JointSession.partners.firstOrNull { it.id == id }
                    ?: repository.getProfileById(id)
            }.map { profile ->
                profile?.let { DisplayName.of(it) }?.ifEmpty { null }
                    ?: getString(R.string.unknown_member)
            }
            banner.text = getString(R.string.partner_banner, names.joinToString(", "))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_workout_tracking)

        setUpTopBar(R.string.track_workout)

        repository = AppRepository.get(this)

        // Nur beim ersten Aufbau fragen. Nach dem Drehen des Geraets stuende
        // der Dialog sonst wieder da, obwohl die Frage laengst beantwortet
        // ist - und die Antwort waere verloren.
        // Die Einfuehrung erst nach der Partnerfrage: der Dialog ist ein
        // eigenes Fenster und liegt ueber der Ebene der Einfuehrung. Beides
        // gleichzeitig zu zeigen hiesse, zwei Dinge gleichzeitig zu verlangen.
        if (savedInstanceState == null && !JointSession.isFinished()) {
            askIfTrainingTogether()
        } else {
            CoachTour.start(this, Tours.WORKOUT)
        }
        partnerIds = savedInstanceState?.getStringArrayList(STATE_PARTNER_IDS).orEmpty()
        showPartnerBanner()

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
        llMapPreview = findViewById(R.id.llMapPreview)
        ivLocationMap = findViewById(R.id.ivLocationMap)
        tvMapAttribution = findViewById(R.id.tvMapAttribution)

        restoreState(savedInstanceState, etSport, tilDistance)
        applyWatchWorkout(savedInstanceState, etSport, etDuration, tilDistance, etDistance)

        // Sowohl das Feld als auch der Pfeil rechts oeffnen die Auswahl.
        val openSportPicker = View.OnClickListener {
            showSportPicker(etSport, tilDistance, etDistance)
        }
        etSport.setOnClickListener(openSportPicker)
        tilSport.setEndIconOnClickListener(openSportPicker)

        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener { checkCameraPermission() }
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
    /**
     * Das Formular wird endgueltig verlassen.
     *
     * Wer abbricht oder zurueckgeht, verwirft damit auch das gemeinsame
     * Training. Ohne das blieb die Sitzung im Speicher stehen: die naechste
     * Aufnahme fragte nicht mehr, ob man zusammen trainiert hat, und trug
     * stillschweigend den alten Partner und die alte Dauer ein - ein Workout,
     * das nie stattgefunden hat, mit doppelten Punkten.
     *
     * Nur bei [isFinishing]: eine Drehung zerstoert die Activity ebenfalls,
     * und dabei soll die Sitzung gerade nicht verloren gehen.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) JointSession.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, photoPath)
        outState.putString(STATE_PHOTO_URI, photoUri?.toString())
        outState.putString(STATE_VOICE_PATH, voicePath)
        outState.putString(STATE_LOCATION, locationText)
        outState.putString(STATE_SPORT, selectedSport)
        pickedLatitude?.let { outState.putDouble(STATE_LATITUDE, it) }
        pickedLongitude?.let { outState.putDouble(STATE_LONGITUDE, it) }
        pendingEndedAt?.let { outState.putLong(STATE_PENDING_AT, it) }
        // Die Kopplung war ein eigener Bildschirm und womoeglich ein Weg quer
        // durch den Raum. Sie nach einer Drehung noch einmal zu verlangen,
        // waere die schlechteste aller Antworten.
        outState.putStringArrayList(STATE_PARTNER_IDS, ArrayList(partnerIds))
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
        if (state.containsKey(STATE_LATITUDE)) pickedLatitude = state.getDouble(STATE_LATITUDE)
        if (state.containsKey(STATE_LONGITUDE)) pickedLongitude = state.getDouble(STATE_LONGITUDE)

        if (photoPath.isNotEmpty()) showPhotoPreview()
        if (voicePath.isNotEmpty()) tvVoiceStatus.setText(R.string.voice_recorded)
        if (locationText.isNotEmpty()) {
            showLocation()
            showMapPreview()
        }
    }

    /**
     * Uebernimmt ein auf der Uhr aufgezeichnetes Workout ins Formular.
     *
     * Sportart und Dauer stehen damit schon da, Foto und Ort fehlen weiterhin -
     * genau die beiden Dinge, die nur am Telefon zu holen sind. Die
     * uebernommenen Werte bleiben aenderbar: die Uhr misst die Zeit bis zum
     * Antippen von "Stopp", und die letzten zwei Minuten waren vielleicht schon
     * der Heimweg.
     */
    private fun applyWatchWorkout(
        state: Bundle?,
        etSport: EditText,
        etDuration: EditText,
        tilDistance: TextInputLayout,
        etDistance: EditText
    ) {
        val endedAt = when {
            state?.containsKey(STATE_PENDING_AT) == true -> state.getLong(STATE_PENDING_AT)
            intent.hasExtra(EXTRA_PENDING_AT) -> intent.getLongExtra(EXTRA_PENDING_AT, 0L)
            else -> return
        }
        // Weg ist es, wenn dasselbe Workout inzwischen an anderer Stelle
        // eingetragen wurde - etwa ueber die Benachrichtigung auf einem
        // zweiten Weg. Dann bleibt hier ein leeres Formular stehen statt einer
        // Vorgabe, die es nicht mehr gibt.
        val workout = PendingWorkouts.find(this, endedAt) ?: return

        pendingEndedAt = workout.endedAt
        watchAvgHeartRate = workout.avgHeartRate
        watchMaxHeartRate = workout.maxHeartRate

        // Nach einer Drehung stehen die Werte schon im Formular, womoeglich von
        // Hand geaendert - dann waere ein zweites Befuellen ein Rueckschritt.
        if (state == null) {
            applySport(workout.sport, etSport, tilDistance, etDistance)
            etDuration.setText(workout.minutes.toString())
        }

        val notice = findViewById<TextView>(R.id.tvWatchNotice)
        notice.visibility = View.VISIBLE
        notice.text = getString(
            R.string.watch_notice,
            WatchFacts.line(this, workout, WatchFacts.SEPARATOR_SENTENCE)
        )
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
        // Ganz unten der Weg zur eigenen Sportart. Als letzter Eintrag der
        // Liste und nicht als zweiter Knopf am Dialog: es ist dieselbe Frage,
        // nur mit einer Antwort mehr.
        val choices = Sports.ALL.map { ChoiceAdapter.Entry(it, Sports.iconFor(it)) } +
                ChoiceAdapter.Entry(getString(R.string.sport_other), R.drawable.ic_sport_other)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.train_question)
            .setAdapter(ChoiceAdapter(this, choices)) { _, index ->
                if (index == Sports.ALL.size) askForCustomSport(etSport, tilDistance, etDistance)
                else applySport(Sports.ALL[index], etSport, tilDistance, etDistance)
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    /**
     * Fragt nach einer selbst eingetragenen Sportart.
     *
     * Punkte gibt es dafuer wie fuer jedes andere Workout: sie haengen an Dauer
     * und Intensitaet, und eine unbekannte Sportart bekommt die mittlere - siehe
     * Sports.intensityFor.
     */
    private fun askForCustomSport(
        etSport: EditText,
        tilDistance: TextInputLayout,
        etDistance: EditText
    ) {
        val input = EditText(this).apply {
            setHint(R.string.sport_custom_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(selectedSport.takeIf { !Sports.isKnown(it) }.orEmpty())
        }
        val container = FrameLayout(this).apply {
            val padding = resources.getDimensionPixelSize(R.dimen.card_padding)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sport_custom_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_btn, null)
            .setPositiveButton(R.string.add_btn) { _, _ ->
                val sport = Sports.customOrNull(input.text.toString())
                if (sport == null) {
                    toastFormatted(
                        R.string.error_sport_custom_invalid,
                        Sports.CUSTOM_MIN_LENGTH,
                        Sports.CUSTOM_MAX_LENGTH
                    )
                    return@setPositiveButton
                }
                applySport(sport, etSport, tilDistance, etDistance)
            }
            .show()
    }

    private fun applySport(
        sport: String,
        etSport: EditText,
        tilDistance: TextInputLayout,
        etDistance: EditText
    ) {
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

    // --- Speichern ---

    private fun saveActivity(sport: String, duration: String, distance: String, btnSave: Button) {
        if (sport.isEmpty()) {
            toast(R.string.select_sport)
            return
        }
        // Obergrenzen halten offensichtlichen Unsinn aus der Rangliste heraus:
        // ein Workout ueber 5000 Minuten dauert dreieinhalb Tage und bringt
        // fast 1500 Punkte.
        val minutes = InputRules.durationOrNull(duration)
        if (minutes == null) {
            toastFormatted(
                R.string.error_duration_range,
                InputRules.MIN_DURATION_MINUTES,
                InputRules.MAX_DURATION_MINUTES
            )
            return
        }

        var kilometers = 0.0
        if (Sports.tracksDistance(sport)) {
            val parsed = InputRules.distanceOrNull(distance)
            if (parsed == null) {
                toastFormatted(
                    R.string.error_distance_range,
                    InputRules.MIN_DISTANCE_KM.toString(),
                    InputRules.MAX_DISTANCE_KM.toInt()
                )
                return
            }
            kilometers = parsed
        }

        // Foto und Standort sind Pflicht - sie sind der Nachweis, dass das
        // Workout wirklich stattgefunden hat, und genau darum geht es in der
        // Crew. Nur die Sprachnotiz bleibt freiwillig.
        if (photoPath.isEmpty()) {
            toast(R.string.error_photo_required)
            return
        }
        if (locationText.isEmpty()) {
            toast(R.string.error_location_required)
            return
        }

        // Eine laufende Aufnahme zuerst abschliessen, sonst waere die Datei
        // beim Hochladen noch unvollstaendig.
        if (isRecording) stopRecording()

        btnSave.isEnabled = false
        lifecycleScope.launch {
            // Der Puls fuer den Zeitraum des Workouts, den eine Uhr nach Health
            // Connect geschrieben hat. Das Ende ist jetzt, der Anfang liegt die
            // eingetragene Dauer davor - genauer geht es nicht, solange die
            // Dauer von Hand eingegeben wird und nicht mitlaeuft.
            // Kam das Workout von der Uhr, hat sie waehrenddessen gemessen -
            // das schlaegt jede nachtraegliche Suche im Zeitfenster.
            val end = Instant.now()
            val beats = if (pendingEndedAt != null) null else HealthHeartRate.forWindow(
                this@WorkoutTrackingActivity,
                end.minus(minutes.toLong(), ChronoUnit.MINUTES),
                end
            )

            val saved = repository.addActivity(
                sport = sport,
                photoPath = photoPath,
                location = locationText,
                duration = minutes.toString(),
                voicePath = voicePath,
                distance = kilometers.toString(),
                intensity = Sports.intensityFor(sport).name,
                // Fuer die Kartenvorschau in der Historie.
                latitude = pickedLatitude,
                longitude = pickedLongitude,
                avgHeartRate = watchAvgHeartRate ?: beats?.average,
                maxHeartRate = watchMaxHeartRate ?: beats?.max,
                // Wurde gemeinsam trainiert, zaehlt das Workout doppelt. Die
                // Kennungen werden mitgespeichert und nicht nur ein Ja/Nein:
                // so steht spaeter noch da, mit wem - und die ganze Crew sieht
                // es unter dem Eintrag.
                partnerIds = partnerIds
            )

            if (saved) {
                // Das gemeinsame Training ist abgeschlossen - die Verbindung
                // wird nicht mehr gebraucht und wuerde sonst weiter Akku
                // ziehen.
                JointSession.clear()
                // Erst jetzt aus der Warteschlange nehmen: waere es vorher
                // geschehen und der Upload gescheitert, waere das Workout weg.
                pendingEndedAt?.let { endedAt ->
                    PendingWorkouts.remove(this@WorkoutTrackingActivity, endedAt)
                    // Und der Uhr Bescheid geben, die es aufgezeichnet hat.
                    // Die Punkte wie im Detail eines Workouts gerechnet: ohne
                    // den Aufschlag fuer eine Serie und ohne den Faktor fuer
                    // ein gemeinsames Training, damit auf der Uhr dieselbe Zahl
                    // steht wie in der App.
                    WatchAck.confirm(
                        context = this@WorkoutTrackingActivity,
                        endedAt = endedAt,
                        sport = sport,
                        minutes = minutes,
                        points = PointsCalculator.calculateWorkoutPoints(
                            minutes,
                            Sports.intensityFor(sport)
                        )
                    )
                }
                toast(R.string.activity_saved)
                finish()
            } else {
                btnSave.isEnabled = true
                toast(R.string.activity_save_failed)
            }
        }
    }

    // --- Foto ---

    /**
     * Holt die Kamera-Berechtigung, bevor die Kamera-App gestartet wird.
     *
     * Notwendig, weil die App CAMERA im Manifest deklariert: Android verlangt
     * die Berechtigung dann auch fuer ACTION_IMAGE_CAPTURE, obwohl nur die
     * fremde Kamera-App geoeffnet wird. Ohne diese Abfrage warf das System
     * eine SecurityException und die App stuerzte ab - reproduzierbar, sobald
     * die Berechtigung einmal abgelehnt worden war.
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            takePhoto()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

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
        } catch (e: SecurityException) {
            // Zusaetzliche Absicherung, falls die Berechtigung zwischen Abfrage
            // und Start entzogen wird.
            Log.e("Camera", "Camera permission missing: ${e.message}")
            photoPath = ""
            toast(R.string.camera_permission_denied)
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
        // Vom klein zentrierten Platzhalter auf ein formatfuellendes Foto.
        ivPreview.scaleType = ImageView.ScaleType.CENTER_CROP
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
                // Ein Fix genuegt - danach abmelden, statt weiter zu orten.
                stopLocationUpdates()
                onPositionFound(location.latitude, location.longitude)
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
     * Sobald die Position feststeht, wird sie nicht mehr als Zahlenpaar
     * uebernommen, sondern in Adressen uebersetzt, aus denen der Nutzer
     * auswaehlt. Frueher landete "Lat: 52.1548, Lon: 9.9580" in der Datenbank -
     * damit kann im Feed niemand etwas anfangen.
     */
    private fun onPositionFound(latitude: Double, longitude: Double) {
        pickedLatitude = latitude
        pickedLongitude = longitude
        tvLocation.setText(R.string.location_looking_up)

        lifecycleScope.launch {
            val suggestions = LocationNames.suggestionsFor(this@WorkoutTrackingActivity, latitude, longitude)
            showLocationChoices(suggestions, latitude, longitude)
        }
    }

    /**
     * Auswahl aus den gefundenen Adressen, dazu immer die Moeglichkeit, den
     * Ort selbst zu benennen - ein Studio heisst "McFit" und nicht
     * "Kaiserstrasse 12".
     */
    private fun showLocationChoices(
        suggestions: List<String>,
        latitude: Double,
        longitude: Double
    ) {
        val coordinates = LocationNames.coordinatesLabel(latitude, longitude)
        val entries = suggestions + listOf(
            getString(R.string.location_enter_name),
            getString(R.string.location_use_coordinates)
        )

        if (suggestions.isEmpty()) {
            toast(R.string.location_lookup_failed)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_pick_title)
            .setItems(entries.toTypedArray()) { _, index ->
                when (index) {
                    suggestions.size -> askForOwnName()
                    suggestions.size + 1 -> applyLocation(coordinates)
                    else -> applyLocation(suggestions[index])
                }
            }
            .setOnCancelListener { showLocation() }
            .show()
    }

    /**
     * Eingabe eines eigenen Ortsnamens.
     *
     * Vorbelegt wird nur ein zuvor selbst vergebener Name, damit ein Tippfehler
     * korrigiert werden kann - nicht die zuletzt gewaehlte Adresse. Sonst
     * muesste man erst eine lange Adresszeile loeschen, um "McFit" zu
     * schreiben, und getippter Text landete vor dem vorhandenen.
     */
    private fun askForOwnName() {
        val input = EditText(this).apply {
            setHint(R.string.location_name_hint)
            setText(customLocationName)
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_name_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    showLocation()
                    return@setPositiveButton
                }
                customLocationName = name
                applyLocation(name)
            }
            .setNegativeButton(R.string.cancel_btn) { _, _ -> showLocation() }
            .show()
    }

    private fun applyLocation(label: String) {
        locationText = label
        showLocation()
        showMapPreview()
    }

    private fun showLocation() {
        tvLocation.text =
            if (locationText.isEmpty()) getString(R.string.location_not_shared)
            else getString(R.string.location_status, locationText)
    }

    /** Kartenausschnitt der gewaehlten Stelle, aus OpenStreetMap-Kacheln. */
    private fun showMapPreview() {
        val latitude = pickedLatitude
        val longitude = pickedLongitude
        if (latitude == null || longitude == null) return

        lifecycleScope.launch {
            val map = StaticMap.preview(
                this@WorkoutTrackingActivity,
                latitude,
                longitude,
                ContextCompat.getColor(this@WorkoutTrackingActivity, R.color.primary)
            )
            if (map == null) {
                llMapPreview.visibility = View.GONE
                return@launch
            }
            ivLocationMap.setImageBitmap(map)
            tvMapAttribution.text = StaticMap.ATTRIBUTION
            llMapPreview.visibility = View.VISIBLE
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

    private fun toastFormatted(resId: Int, vararg args: Any) =
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
}
