package com.example.mobilese

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

class CreateCrewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_crew)

        val etCrewName = findViewById<EditText>(R.id.etCrewName)
        val btnSave = findViewById<Button>(R.id.btnSaveCrew)
        val ivQrCode = findViewById<ImageView>(R.id.ivCrewQrCode)
        val tvInstruction = findViewById<TextView>(R.id.tvQrInstruction)
        val btnBack = findViewById<Button>(R.id.btnBackFromCrew)

        btnSave.setOnClickListener {
            val name = etCrewName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Bitte einen Namen eingeben!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Crew speichern und beitreten
            val sharedPref = getSharedPreferences("CrewFitPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putString("joined_crew", name).apply()

            // QR-Code generieren
            try {
                val encoder = BarcodeEncoder()
                val bitmap: Bitmap = encoder.encodeBitmap(name, BarcodeFormat.QR_CODE, 500, 500)
                
                ivQrCode.setImageBitmap(bitmap)
                ivQrCode.visibility = View.VISIBLE
                tvInstruction.visibility = View.VISIBLE
                
                Toast.makeText(this, "Crew '$name' erstellt!", Toast.LENGTH_SHORT).show()

                // Optional: Nach Verzögerung zum Home Screen wechseln
                // Wir lassen den Nutzer den Code aber erstmal sehen
                btnBack.text = "Weiter zum Home Screen"
                btnBack.setOnClickListener {
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Fehler bei QR-Generierung", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
