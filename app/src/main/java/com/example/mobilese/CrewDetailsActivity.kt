package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
            findViewById<TextView>(R.id.tvMembersList).text = members.joinToString("\n") { member ->
                "- " + (member.name?.takeIf { it.isNotBlank() } ?: member.email.orEmpty())
            }

            if (qr == null) {
                Toast.makeText(this@CrewDetailsActivity, R.string.qr_generation_failed, Toast.LENGTH_SHORT).show()
            } else {
                findViewById<ImageView>(R.id.ivOverviewQrCode).setImageBitmap(qr)
            }
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

            val intent = Intent(this@CrewDetailsActivity, CrewLandingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
