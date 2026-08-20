package com.example.mobilese

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CrewDetailsActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var crewCode: String
    private var crewName: String = ""

    /** Nur der Gruender darf das Bild aendern und ueber Anfragen entscheiden. */
    private var isCreator = false

    /**
     * Muss beim Anlegen der Activity feststehen, nicht erst in onCreate -
     * Android verlangt, dass Vertraege fuer Ergebnisse vor dem Start
     * registriert sind.
     */
    private val pickCrewImage = GalleryPicker(this) { uri -> storeCrewImage(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_crew_details)

        repository = AppRepository.get(this)
        // finish() verhindert, dass eine leere Activity stehen bleibt.
        crewCode = repository.getJoinedCrewCode() ?: run {
            finish()
            return
        }

        setUpTopBar(R.string.your_crew)
        setUpPullToRefresh { load() }
        findViewById<TextView>(R.id.tvOverviewCrewCode).text = crewCode
        findViewById<Button>(R.id.btnLeaveCrew).setOnClickListener { leaveCrew() }

        load()
    }

    /**
     * Nach dem Annehmen einer Anfrage wandert jemand aus der einen Liste in die
     * andere. Statt beide von Hand nachzufuehren, wird neu geladen - eine
     * Abfrage mehr, dafuer keine zwei Listen, die auseinanderlaufen koennen.
     */
    private fun load() {
        lifecycleScope.launch {
            try {
                coroutineScope {
                    // Alle Abfragen werden angestossen, bevor auf die erste
                    // gewartet wird - sie haengen nicht voneinander ab und laufen
                    // deshalb nebeneinander.
                    val nameAsync = async { repository.getCrewName(crewCode) }
                    val membersAsync = async { repository.getCrewMembers(crewCode) }
                    val qrAsync = async { QrCodes.generate(crewCode) }
                    val creatorAsync = async { repository.isCrewCreator(crewCode) }
                    val imageAsync = async { repository.getCrewImageUrl(crewCode) }

                    crewName = nameAsync.await()
                    isCreator = creatorAsync.await()

                    findViewById<TextView>(R.id.tvCrewNameDisplay).text = crewName
                    showMembers(membersAsync.await())
                    showCrewImage(imageAsync.await())

                    val qr = qrAsync.await()
                    if (qr == null) {
                        Toast.makeText(this@CrewDetailsActivity, R.string.qr_generation_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        findViewById<ImageView>(R.id.ivOverviewQrCode).setImageBitmap(qr)
                    }
                }

                // Erst nach isCreator: wer die Crew nicht gegruendet hat, bekommt
                // die Anfragen gar nicht erst zu sehen, und die Datenbank gibt sie
                // ihm auch nicht heraus.
                if (isCreator) showJoinRequests(repository.getJoinRequests(crewCode))
            } finally {
                finishRefreshing()
            }
        }
    }

    private fun showCrewImage(url: String?) {
        ImageLoader.into(
            findViewById(R.id.ivCrewImage),
            url,
            circular = true,
            placeholder = R.drawable.ic_group
        )

        // Antippen nur fuer den Gruender - sonst faende jemand einen Knopf, der
        // ihm nichts als eine Fehlermeldung einbringt.
        findViewById<View>(R.id.tvCrewImageHint).visibility =
            if (isCreator) View.VISIBLE else View.GONE
        if (isCreator) {
            findViewById<View>(R.id.cvCrewImage).setOnClickListener { pickCrewImage.open() }
        }
    }

    /**
     * Das gewaehlte Bild wird verkleinert zwischengespeichert und dann
     * hochgeladen - genau wie beim Profilbild. Eine Aufnahme aus der Kamera
     * waere sonst mehrere Megabyte gross.
     */
    private fun storeCrewImage(uri: Uri) {
        lifecycleScope.launch {
            val file = File(filesDir, "crew_$crewCode.jpg")
            val bitmap = withContext(Dispatchers.IO) {
                ImageLoader.saveScaled(this@CrewDetailsActivity, uri, file)
            }

            if (bitmap == null) {
                Toast.makeText(this@CrewDetailsActivity, R.string.crew_image_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            findViewById<ImageView>(R.id.ivCrewImage).setImageBitmap(bitmap)
            Toast.makeText(
                this@CrewDetailsActivity,
                if (repository.saveCrewImage(crewCode, file.absolutePath)) R.string.crew_image_saved
                else R.string.crew_image_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Die offenen Anfragen. Ueberschrift und Karte werden zusammen ein- und
     * ausgeblendet: eine Ueberschrift ueber einer leeren Karte waere ein
     * Versprechen auf etwas, das nicht da ist.
     */
    private fun showJoinRequests(requests: List<UserProfile>) {
        val label = findViewById<View>(R.id.tvJoinRequestsLabel)
        val card = findViewById<View>(R.id.cvJoinRequests)
        val container = findViewById<LinearLayout>(R.id.llJoinRequests)

        container.removeAllViews()

        val visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
        label.visibility = visibility
        card.visibility = visibility
        if (requests.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        for (person in requests) {
            val row = inflater.inflate(R.layout.item_join_request_row, container, false)
            row.findViewById<TextView>(R.id.tvRequestName).text =
                DisplayName.of(person).ifEmpty { getString(R.string.unknown_member) }
            ImageLoader.into(
                row.findViewById(R.id.ivRequestPhoto),
                person.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )

            // Nachsehen koennen, wen man da hereinlaesst.
            row.setOnClickListener {
                startActivity(MemberProfileActivity.intent(this, person.id))
            }
            row.findViewById<View>(R.id.btnAcceptRequest).setOnClickListener {
                decide(person, accept = true)
            }
            row.findViewById<View>(R.id.btnRejectRequest).setOnClickListener {
                decide(person, accept = false)
            }

            container.addView(row)
        }
    }

    private fun decide(person: UserProfile, accept: Boolean) {
        lifecycleScope.launch {
            val done =
                if (accept) repository.acceptJoinRequest(crewCode, person.id)
                else repository.rejectJoinRequest(crewCode, person.id)

            if (!done) {
                Toast.makeText(this@CrewDetailsActivity, R.string.join_request_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val name = DisplayName.of(person).ifEmpty { getString(R.string.unknown_member) }
            Toast.makeText(
                this@CrewDetailsActivity,
                if (accept) getString(R.string.join_request_accepted, name)
                else getString(R.string.join_request_rejected),
                Toast.LENGTH_SHORT
            ).show()

            load()
        }
    }

    /** Eine Zeile je Mitglied; angetippt fuehrt sie zum Kurzprofil. */
    private fun showMembers(members: List<UserProfile>) {
        val container = findViewById<LinearLayout>(R.id.llMembersList)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (member in members) {
            val row = inflater.inflate(R.layout.item_crew_member_row, container, false)
            row.findViewById<TextView>(R.id.tvMemberName).text =
                DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
            ImageLoader.into(
                row.findViewById(R.id.ivMemberPhoto),
                member.avatarUrl,
                circular = true,
                placeholder = android.R.drawable.ic_menu_gallery
            )
            row.setOnClickListener {
                startActivity(MemberProfileActivity.intent(this, member.id))
            }
            container.addView(row)
        }
    }

    private fun leaveCrew() {
        lifecycleScope.launch {
            if (!repository.leaveCrew(crewCode)) {
                Toast.makeText(this@CrewDetailsActivity, R.string.leave_crew_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(
                this@CrewDetailsActivity,
                getString(R.string.left_crew, crewName),
                Toast.LENGTH_SHORT
            ).show()

            // Wer noch in einer anderen Crew ist, wird dorthin gebracht -
            // leaveCrew hat sie bereits zur angezeigten gemacht. Nur ohne jede
            // Crew fuehrt der Weg auf die Beitrittsseite.
            val target =
                if (repository.getJoinedCrewCode() != null) MainHubActivity::class.java
                else CrewLandingActivity::class.java

            val intent = Intent(this@CrewDetailsActivity, target)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
