package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
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
            val scannedCrewName = result.contents
            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putString("joined_crew", scannedCrewName).apply()
            
            Toast.makeText(this, "Crew '$scannedCrewName' beigetreten!", Toast.LENGTH_LONG).show()
            
            // Zum Home Screen
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
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
        val btnJoin = findViewById<Button>(R.id.btnJoinCrew)
        val btnProfile = findViewById<ImageButton>(R.id.btnProfileIcon)

        btnCreate.setOnClickListener {
            startActivity(Intent(this, CreateCrewActivity::class.java))
        }

        btnJoin.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scanne den QR-Code deiner Crew")
            options.setCameraId(0)
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            options.setOrientationLocked(false)
            barcodeLauncher.launch(options)
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
