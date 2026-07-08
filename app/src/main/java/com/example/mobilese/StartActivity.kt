package com.example.mobilese

import android.content.Context
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

    // QR-Scanner initialisieren
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan abgebrochen", Toast.LENGTH_LONG).show()
        } else {
            val scannedCode = result.contents
            joinCrew(scannedCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        if (sharedPref.contains("joined_crew")) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_start)

        val btnCreate = findViewById<Button>(R.id.btnCreateCrew)
        val btnJoinScanner = findViewById<Button>(R.id.btnJoinCrew)
        val btnJoinManual = findViewById<Button>(R.id.btnJoinByCode)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)

        btnCreate.setOnClickListener {
            startActivity(Intent(this, CreateCrewActivity::class.java))
        }

        btnJoinScanner.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scanne den QR-Code deiner Crew")
            options.setCameraId(0)
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            options.setOrientationLocked(false)
            barcodeLauncher.launch(options)
        }

        btnJoinManual.setOnClickListener {
            showJoinDialog()
        }

        btnProfile.setOnClickListener {
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
            val code = input.text.toString().trim()
            if (code.isNotEmpty()) {
                joinCrew(code)
            } else {
                Toast.makeText(this, "Bitte Code eingeben!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun joinCrew(code: String) {
        val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
        // In einer echten App würde man hier den Code gegen eine Datenbank prüfen
        // Für diesen Prototyp speichern wir einfach den Code als Crew-Namen
        sharedPref.edit().putString("joined_crew", "Crew ($code)").apply()
        
        Toast.makeText(this, "Crew erfolgreich beigetreten!", Toast.LENGTH_LONG).show()
        
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
