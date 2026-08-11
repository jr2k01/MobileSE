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

    private lateinit var backend: AppRepository

    // QR-Scanner initialisieren
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            joinCrew(result.contents)
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        backend = AppRepository(this)
        
        // Prüfen, ob bereits in Crew (via Code)
        if (backend.getJoinedCrewCode() != null) {
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
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan your crew's QR code")
            barcodeLauncher.launch(options)
        }

        findViewById<Button>(R.id.btnJoinByCode).setOnClickListener {
            showJoinDialog()
        }

        findViewById<ImageButton>(R.id.btnProfileIcon).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun showJoinDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Join Crew")
        val input = EditText(this)
        input.hint = "Enter unique crew code"
        builder.setView(input)
        
        builder.setPositiveButton("Join") { _, _ ->
            joinCrew(input.text.toString().trim())
        }
        builder.setNegativeButton("Cancel") { d, _ -> d.cancel() }
        builder.show()
    }

    private fun joinCrew(code: String) {
        if (code.isEmpty()) return
        
        val currentUser = backend.getCurrentUser() ?: return
        
        lifecycleScope.launch {
            // Versuch, der Crew via CODE beizutreten
            if (backend.joinCrew(code, currentUser)) {
                val crewName = backend.getCrewName(code)
                Toast.makeText(this@CrewLandingActivity, "Joined crew '$crewName'!", Toast.LENGTH_LONG).show()
                
                val intent = Intent(this@CrewLandingActivity, MainHubActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                Toast.makeText(this@CrewLandingActivity, "Invalid crew code!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
