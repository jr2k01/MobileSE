package com.example.mobilese

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class StartActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend

    // QR-Scanner initialisieren
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            joinCrew(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        backend = AppBackend(this)
        
        // Prüfen, ob bereits in Crew (via Code)
        if (backend.getJoinedCrewCode() != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_start)

        findViewById<Button>(R.id.btnCreateCrew).setOnClickListener {
            startActivity(Intent(this, CreateCrewActivity::class.java))
        }

        findViewById<Button>(R.id.btnJoinCrew).setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scanne den QR-Code deiner Crew")
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
        builder.setTitle("Crew beitreten")
        val input = EditText(this)
        input.hint = "Einzigartigen Crew-Code eingeben"
        builder.setView(input)
        
        builder.setPositiveButton("Beitreten") { _, _ ->
            joinCrew(input.text.toString().trim())
        }
        builder.setNegativeButton("Abbrechen") { d, _ -> d.cancel() }
        builder.show()
    }

    private fun joinCrew(code: String) {
        if (code.isEmpty()) return
        
        val currentUser = backend.getCurrentUser() ?: return
        
        // Versuch, der Crew via CODE beizutreten
        if (backend.joinCrew(code, currentUser)) {
            backend.setJoinedCrewCode(code)
            val crewName = backend.getCrewName(code)
            
            Toast.makeText(this, "Crew '$crewName' beigetreten!", Toast.LENGTH_LONG).show()
            
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            Toast.makeText(this, "Ungültiger Crew-Code!", Toast.LENGTH_SHORT).show()
        }
    }
}
