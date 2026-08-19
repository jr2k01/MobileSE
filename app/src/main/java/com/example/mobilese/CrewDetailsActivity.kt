package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CrewDetailsActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var crewCode: String
    private var crewName: String = ""

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
        findViewById<TextView>(R.id.tvOverviewCrewCode).text = crewCode
        findViewById<Button>(R.id.btnLeaveCrew).setOnClickListener { leaveCrew() }

        lifecycleScope.launch {
            // Name, Mitglieder und QR-Code haengen nicht voneinander ab und
            // werden deshalb gleichzeitig geholt.
            val (name, members, qr) = coroutineScope {
                val nameAsync = async { repository.getCrewName(crewCode) }
                val membersAsync = async { repository.getCrewMembers(crewCode) }
                val qrAsync = async { QrCodes.generate(crewCode) }
                Triple(nameAsync.await(), membersAsync.await(), qrAsync.await())
            }

            crewName = name
            findViewById<TextView>(R.id.tvCrewNameDisplay).text = name
            showMembers(members)

            if (qr == null) {
                Toast.makeText(this@CrewDetailsActivity, R.string.qr_generation_failed, Toast.LENGTH_SHORT).show()
            } else {
                findViewById<ImageView>(R.id.ivOverviewQrCode).setImageBitmap(qr)
            }
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
