package com.example.mobilese

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddActivityActivity : AppCompatActivity() {

    private lateinit var backend: AppBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_activity)

        backend = AppBackend(this)
        val currentUser = backend.getCurrentUser() ?: run { finish(); return }

        val rgSports = findViewById<RadioGroup>(R.id.rgSports)
        val btnSave = findViewById<Button>(R.id.btnSaveActivity)
        val btnCancel = findViewById<Button>(R.id.btnCancelAdd)

        btnSave.setOnClickListener {
            val selectedId = rgSports.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Bitte wähle eine Sportart aus!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val radioButton = findViewById<RadioButton>(selectedId)
            val sportName = radioButton.text.toString()

            backend.addActivity(currentUser, sportName)
            Toast.makeText(this, "$sportName wurde hinzugefügt!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnCancel.setOnClickListener { finish() }
    }
}
