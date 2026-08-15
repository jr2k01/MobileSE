package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class CreateCrewActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_crew_creation)

        repository = AppRepository.get(this)

        val etCrewName = findViewById<EditText>(R.id.etCrewName)
        val btnSave = findViewById<Button>(R.id.btnSaveCrew)
        val btnBack = findViewById<Button>(R.id.btnBackFromCrew)

        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val name = etCrewName.text.toString().trim()
            if (!InputRules.isValidCrewName(name)) {
                Toast.makeText(
                    this,
                    getString(
                        R.string.error_crew_name_invalid,
                        InputRules.CREW_NAME_MIN_LENGTH,
                        InputRules.CREW_NAME_MAX_LENGTH
                    ),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            lifecycleScope.launch {
                val code = createCrewWithUniqueCode(name)
                btnSave.isEnabled = true

                if (code == null) {
                    Toast.makeText(this@CreateCrewActivity, R.string.crew_create_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showCreatedCrew(name, code, btnBack)
            }
        }
    }

    /**
     * Legt die Crew an und versucht es bei einem bereits vergebenen Code
     * erneut. Der Code ist der Primaerschluessel und besteht nur aus drei
     * Buchstaben und drei Ziffern - Kollisionen sind selten, aber moeglich, und
     * fuehrten vorher zu einer Fehlermeldung, obwohl ein zweiter Versuch
     * gereicht haette.
     */
    private suspend fun createCrewWithUniqueCode(name: String): String? {
        repeat(5) {
            val code = (name.take(3).uppercase() + (100..999).random()).replace(" ", "X")
            if (repository.createCrew(name, code)) return code
        }
        return null
    }

    private suspend fun showCreatedCrew(name: String, code: String, btnBack: Button) {
        findViewById<TextView>(R.id.tvUniqueCrewCode).text = code
        findViewById<MaterialCardView>(R.id.cvQrResult).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvQrInstruction).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvCrewCodeLabel).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvUniqueCrewCode).visibility = View.VISIBLE

        val ivQrCode = findViewById<ImageView>(R.id.ivCrewQrCode)
        val qr = QrCodes.generate(code)
        if (qr == null) {
            Toast.makeText(this, R.string.qr_generation_failed, Toast.LENGTH_SHORT).show()
        } else {
            ivQrCode.setImageBitmap(qr)
            ivQrCode.visibility = View.VISIBLE
        }

        Toast.makeText(this, getString(R.string.crew_created, name), Toast.LENGTH_SHORT).show()

        btnBack.setText(R.string.continue_home)
        btnBack.setOnClickListener {
            val intent = Intent(this, MainHubActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
