package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class CrewLandingActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    private val barcodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            val code = result.contents
            if (code != null) {
                joinCrew(code.trim())
            } else {
                Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = AppRepository.get(this)

        if (repository.getJoinedCrewCode() != null) {
            startActivity(Intent(this, MainHubActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.screen_crew_landing)

        findViewById<Button>(R.id.btnCreateCrew).setOnClickListener {
            startActivity(Intent(this, CreateCrewActivity::class.java))
        }

        findViewById<Button>(R.id.btnJoinCrew).setOnClickListener {
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_crew_prompt))
            barcodeLauncher.launch(options)
        }

        findViewById<Button>(R.id.btnJoinByCode).setOnClickListener { showJoinDialog() }

        findViewById<ImageButton>(R.id.btnProfileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun showJoinDialog() {
        val input = EditText(this).apply { setHint(R.string.crew_code_hint) }

        AlertDialog.Builder(this)
            .setTitle(R.string.join_crew_title)
            .setView(input)
            .setPositiveButton(R.string.join_btn) { _, _ ->
                joinCrew(input.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun joinCrew(code: String) {
        if (code.isEmpty()) return

        lifecycleScope.launch {
            if (!repository.joinCrew(code)) {
                Toast.makeText(this@CrewLandingActivity, R.string.invalid_crew_code, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val crewName = repository.getCrewName(code)
            Toast.makeText(
                this@CrewLandingActivity,
                getString(R.string.joined_crew, crewName),
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(this@CrewLandingActivity, MainHubActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
