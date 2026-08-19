package com.example.mobilese

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etDisplayName = findViewById<EditText>(R.id.etDisplayName)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etAge = findViewById<EditText>(R.id.etAge)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)

        setUpTopBar(
            R.string.my_profile,
            actionIcon = R.drawable.ic_settings,
            actionDescription = R.string.settings_desc
        ) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        ivProfilePicture.setOnClickListener { pickImageLauncher.launch("image/*") }

        etEmail.setText(currentUserEmail)

        /**
         * Geburtsdatum ueber den Kalender, Alter daraus abgeleitet.
         *
         * Das Altersfeld ist nur noch Anzeige. Vorher waren beides freie
         * Textfelder: das Alter konnte dem Geburtsdatum widersprechen und war
         * spaetestens nach dem naechsten Geburtstag veraltet.
         */
        val applyPickedDate: (String) -> Unit = { picked ->
            etBirthDate.setText(picked)
            etAge.setText(BirthDate.ageTextFrom(picked))
        }
        etBirthDate.setOnClickListener {
            BirthDatePicker.show(this, etBirthDate.text.toString(), applyPickedDate)
        }
        // Falls der Kalender offen war, als das Geraet gedreht wurde.
        BirthDatePicker.reattach(this, applyPickedDate)

        showMedals()
        showFollowing()

        // Beim Drehen des Geraets wird die Activity neu erzeugt. Die Textfelder
        // stellt Android dabei selbst wieder her - sie danach erneut aus der
        // Datenbank zu fuellen wuerde alles ueberschreiben, was seit dem
        // Oeffnen eingetippt und noch nicht gespeichert wurde. Deshalb nur beim
        // ersten Aufbau. Das Profilbild haelt sich nicht von selbst und wird
        // jedes Mal geladen.
        val isFirstStart = savedInstanceState == null

        // Ein einziger Abruf fuer das ganze Formular. Vorher wurde fuer jedes
        // Feld einzeln dieselbe Zeile aus der Datenbank geholt - sechs
        // Abfragen fuer sechs Felder.
        lifecycleScope.launch {
            val profile = repository.getProfile(currentUserEmail) ?: return@launch
            if (isFirstStart) {
                // In der Datenbank steht ein Feld; hier wird es auf die beiden
                // Eingaben verteilt.
                etFirstName.setText(PersonName.firstOf(profile.name))
                etLastName.setText(PersonName.lastOf(profile.name))
                etDisplayName.setText(profile.displayName.orEmpty())
                etBirthDate.setText(profile.birthdate.orEmpty())
                // Nicht das gespeicherte Alter anzeigen, sondern das aus dem
                // Geburtsdatum berechnete - nur das ist heute noch richtig.
                etAge.setText(BirthDate.ageTextFrom(profile.birthdate))
                etHeight.setText(profile.height.orEmpty())
                etWeight.setText(profile.weight.orEmpty())
            }
            ImageLoader.into(
                ivProfilePicture,
                profile.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
        }

        btnSave.setOnClickListener {
            val birthDate = etBirthDate.text.toString().trim()
            val name = PersonName.join(
                etFirstName.text.toString(),
                etLastName.text.toString()
            )
            val displayName = etDisplayName.text.toString().trim()
            val height = etHeight.text.toString().trim()
            val weight = etWeight.text.toString().trim()

            // Groesse und Gewicht sind freiwillig - aber wenn etwas drinsteht,
            // muss es ein plausibler Wert sein.
            if (!InputRules.isValidName(name)) {
                toast(R.string.error_name_invalid)
                return@setOnClickListener
            }
            // Das Kuerzel ist freiwillig; ohne eines wird der volle Name gekuerzt.
            if (displayName.isNotEmpty() && !DisplayName.isValid(displayName)) {
                toastFormatted(
                    R.string.error_display_name_invalid,
                    DisplayName.MIN_LENGTH,
                    DisplayName.MAX_LENGTH
                )
                return@setOnClickListener
            }
            if (height.isNotEmpty() && InputRules.heightOrNull(height) == null) {
                toastFormatted(
                    R.string.error_height_range,
                    InputRules.MIN_HEIGHT_CM,
                    InputRules.MAX_HEIGHT_CM
                )
                return@setOnClickListener
            }
            if (weight.isNotEmpty() && InputRules.weightOrNull(weight) == null) {
                toastFormatted(
                    R.string.error_weight_range,
                    InputRules.MIN_WEIGHT_KG.toInt(),
                    InputRules.MAX_WEIGHT_KG.toInt()
                )
                return@setOnClickListener
            }

            setBusy(true, btnSave)
            lifecycleScope.launch {
                val saved = repository.saveUserProfile(
                    currentUserEmail,
                    name,
                    // Beim Speichern noch einmal aus dem Geburtsdatum
                    // berechnet, damit in der Datenbank nie ein Alter landet,
                    // das nicht dazu passt.
                    BirthDate.ageTextFrom(birthDate),
                    height,
                    weight,
                    birthDate,
                    displayName
                )
                setBusy(false, btnSave)

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

    }


    /**
     * Wem man folgt.
     *
     * Antippen fuehrt auf das Profil der Person - dort stehen ihre Crews und
     * der Weg hinein. Das ist der eigentliche Zweck der Liste: nicht zu
     * sammeln, sondern von hier aus weiterzukommen.
     */
    private fun showFollowing() {
        val container = findViewById<LinearLayout>(R.id.llFollowing)
        val empty = findViewById<TextView>(R.id.tvFollowingEmpty)

        lifecycleScope.launch {
            val people = repository.getFollowing()
            container.removeAllViews()
            empty.visibility = if (people.isEmpty()) View.VISIBLE else View.GONE

            val inflater = LayoutInflater.from(this@ProfileActivity)
            for (person in people) {
                val row = inflater.inflate(R.layout.item_crew_member_row, container, false)
                row.findViewById<TextView>(R.id.tvMemberName).text =
                    DisplayName.of(person).ifEmpty { getString(R.string.unknown_member) }
                ImageLoader.into(
                    row.findViewById<ImageView>(R.id.ivMemberPhoto),
                    person.avatarUrl,
                    circular = true,
                    placeholder = android.R.drawable.ic_menu_gallery
                )
                row.setOnClickListener {
                    startActivity(MemberProfileActivity.intent(this@ProfileActivity, person.id))
                }
                container.addView(row)
            }
        }
    }

    /**
     * Die eigenen Medaillen.
     *
     * Auch die noch offenen stehen abgeblendet mit, damit sichtbar ist, was es
     * zu holen gibt. Berechnet werden sie aus demselben Crew-Snapshot wie in
     * der Rangliste - ohne Crew gibt es keine Grundlage, dann bleibt der
     * Bereich leer.
     */
    private fun showMedals() {
        val grid = findViewById<GridLayout>(R.id.glMedals)
        val progress = findViewById<TextView>(R.id.tvMedalsProgress)

        lifecycleScope.launch {
            val crewCode = repository.getJoinedCrewCode() ?: return@launch
            val userId = repository.getProfile(currentUserEmail)?.id ?: return@launch
            val status = Medals.statusFor(userId, repository.loadCrewSnapshot(crewCode))

            MedalGrid.fill(grid, status)
            progress.text = getString(
                R.string.medals_progress,
                status.count { it.value },
                status.size
            )
        }
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
            val file = File(filesDir, "profile_${currentUserEmail.replace('@', '_')}.jpg")
            val bitmap = withContext(Dispatchers.IO) {
                ImageLoader.saveScaled(this@ProfileActivity, uri, file)
            }

            if (bitmap == null) {
                toast(R.string.image_save_failed)
                return@launch
            }

            ivProfilePicture.setImageBitmap(bitmap)
            toast(
                if (repository.saveUserImage(file.absolutePath)) R.string.image_saved
                else R.string.image_save_failed
            )
        }
    }

    private fun setBusy(busy: Boolean, vararg views: View) {
        views.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun toastFormatted(resId: Int, vararg args: Any) =
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
}
