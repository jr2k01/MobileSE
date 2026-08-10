package com.example.mobilese

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.BarcodeEncoder

class CrewOverviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_overview)

        val backend = AppBackend(this)
        val currentUser = backend.getCurrentUser() ?: return
        val joinedCrewCode = backend.getJoinedCrewCode() ?: return

        lifecycleScope.launch {
            val crewName = backend.getCrewName(joinedCrewCode)

            findViewById<TextView>(R.id.tvCrewNameDisplay).text = crewName
        
            // Crew Code und QR Code anzeigen
            findViewById<TextView>(R.id.tvOverviewCrewCode).text = joinedCrewCode
            val ivQrCode = findViewById<ImageView>(R.id.ivOverviewQrCode)

            try {
                val encoder = BarcodeEncoder()
                val bitmap: Bitmap = encoder.encodeBitmap(joinedCrewCode, BarcodeFormat.QR_CODE, 500, 500)
                ivQrCode.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Toast.makeText(this@CrewOverviewActivity, "Error generating QR code", Toast.LENGTH_SHORT).show()
            }
        
            // Mitgliederliste (Text-Format für die Übersicht)
            val members = backend.getCrewMembers(joinedCrewCode)
            val membersText = members.joinToString("\n") { email -> 
                // Note: We need a scope here too if we want to fetch names one by one, 
                // but let's assume we can fetch them or we use IDs for now to avoid complexity.
                "- $email" 
            }
            findViewById<TextView>(R.id.tvMembersList).text = membersText

            findViewById<Button>(R.id.btnLeaveCrew).setOnClickListener {
                lifecycleScope.launch {
                    backend.leaveCrew(joinedCrewCode, currentUser)
                    Toast.makeText(this@CrewOverviewActivity, "Left crew '$crewName'", Toast.LENGTH_SHORT).show()
            
                    val intent = Intent(this@CrewOverviewActivity, StartActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        }

        findViewById<android.widget.ImageButton>(R.id.btnBackCrew).setOnClickListener {
            finish()
        }
    }
}
