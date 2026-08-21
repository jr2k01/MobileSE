package com.example.mobilese

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Sucht das Crew-Mitglied, mit dem gerade zusammen trainiert wird.
 *
 * Beide Telefone stehen in diesem Bildschirm, senden ihre Kennung ueber
 * Bluetooth Low Energy aus und hoeren zugleich auf die der anderen - siehe
 * [PartnerBeacon]. Wer gefunden wird, erscheint in der Liste; ein Antippen
 * waehlt ihn und schliesst den Bildschirm.
 *
 * Eigener Bildschirm und kein Dialog im Workout-Formular: die Suche laeuft
 * ueber Sekunden, braucht Berechtigungen und kann auf mehrerlei Art
 * schiefgehen. In einem Dialog waere fuer die Erklaerung dazu kein Platz.
 *
 * Das Ergebnis geht als Kennung zurueck; das Formular holt sich den Namen
 * selbst aus der Crew.
 */
class TrainingPartnerActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private var beacon: PartnerBeacon? = null

    private val requestNearby =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AppPermission.NEARBY.isGranted(this)) {
                startSearching()
            } else {
                showProblem(R.string.partner_permission_missing)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_training_partner)
        setUpTopBar(R.string.partner_title)

        repository = AppRepository.get(this)

        findViewById<View>(R.id.btnPartnerSkip).setOnClickListener {
            // Allein weiter: kein Ergebnis, das Formular bleibt beim
            // einfachen Punktestand.
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (AppPermission.NEARBY.isGranted(this)) {
            startSearching()
        } else {
            requestNearby.launch(AppPermission.NEARBY.manifestNames)
        }
    }

    /**
     * Die Crew wird zuerst geholt: gefunden werden soll nur, wer auch dazu
     * gehoert. Ein fremdes CrewFit in Reichweite - im Fitnessstudio durchaus
     * denkbar - darf keine doppelten Punkte verschaffen.
     */
    private fun startSearching() {
        lifecycleScope.launch {
            val crewCode = repository.getJoinedCrewCode()
            if (crewCode == null) {
                showProblem(R.string.partner_no_crew)
                return@launch
            }

            val members = repository.getCrewMembers(crewCode)
            val me = repository.currentUserId()
            if (me == null) {
                showProblem(R.string.partner_permission_missing)
                return@launch
            }

            beacon = PartnerBeacon(
                context = this@TrainingPartnerActivity,
                ownUserId = me,
                members = members,
                // Beide Rueckrufe kommen vom Bluetooth-System, nicht vom
                // Bildschirm-Thread. runOnUiThread ist deshalb Pflicht, nicht
                // Vorsicht: Views von dort anzufassen stuerzt ab.
                onFound = { member -> runOnUiThread { addFound(member) } },
                onProblem = { res -> runOnUiThread { showProblem(res) } }
            ).also { it.start() }
        }
    }

    private fun addFound(member: UserProfile) {
        val container = findViewById<LinearLayout>(R.id.llPartnerFound)
        findViewById<View>(R.id.tvPartnerEmpty).visibility = View.GONE
        findViewById<TextView>(R.id.tvPartnerStatus).setText(R.string.partner_pick)

        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_crew_member_row, container, false)

        row.findViewById<TextView>(R.id.tvMemberName).text =
            DisplayName.of(member).ifEmpty { getString(R.string.unknown_member) }
        ImageLoader.into(
            row.findViewById(R.id.ivMemberPhoto),
            member.avatarUrl,
            circular = true,
            placeholder = android.R.drawable.ic_menu_gallery
        )
        row.setOnClickListener { choose(member) }

        container.addView(row)
    }

    private fun choose(member: UserProfile) {
        Toast.makeText(
            this,
            getString(R.string.partner_chosen, DisplayName.of(member)),
            Toast.LENGTH_SHORT
        ).show()

        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PARTNER_ID, member.id))
        finish()
    }

    /**
     * Der Kreis hoert auf sich zu drehen, und der Text sagt, was fehlt.
     *
     * Kein Abbruch: manche Geraete koennen empfangen, aber nicht senden. Dann
     * findet man die anderen weiterhin, wird nur selbst nicht gefunden - die
     * Liste kann sich also trotzdem noch fuellen.
     */
    private fun showProblem(messageRes: Int) {
        findViewById<View>(R.id.piPartnerSearch).visibility = View.GONE
        findViewById<TextView>(R.id.tvPartnerStatus).setText(messageRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Senden und Empfangen laufen sonst weiter und ziehen Akku, auch wenn
        // niemand mehr hinsieht.
        beacon?.stop()
        beacon = null
    }

    companion object {

        private const val EXTRA_PARTNER_ID = "partner_id"

        fun intent(context: Context): Intent =
            Intent(context, TrainingPartnerActivity::class.java)

        /** Die gewaehlte Person, oder null wenn allein trainiert wird. */
        fun partnerIdFrom(data: Intent?): String? = data?.getStringExtra(EXTRA_PARTNER_ID)
    }
}
