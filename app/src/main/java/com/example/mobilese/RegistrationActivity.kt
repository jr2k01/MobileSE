package com.example.mobilese

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_registration)

        val etName = findViewById<EditText>(R.id.etRegName)
        val etBirthDate = findViewById<EditText>(R.id.etRegBirthDate)
        val etEmail = findViewById<EditText>(R.id.etRegEmail)
        val etPassword = findViewById<EditText>(R.id.etRegPassword)
        val btnRegister = findViewById<Button>(R.id.btnDoRegister)
        val btnBack = findViewById<Button>(R.id.btnBackToLogin)

        val repository = AppRepository.get(this)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (name.isEmpty() || birthDate.isEmpty() || email.isEmpty() || password.isEmpty()) {
                toast(R.string.fill_all_fields)
                return@setOnClickListener
            }

            setBusy(true, btnRegister, btnBack)
            lifecycleScope.launch {
                val success = repository.registerUser(email, password, name, birthDate)
                setBusy(false, btnRegister, btnBack)
                toast(if (success) R.string.registration_success else R.string.registration_failed)
                // Bei Erfolg besteht bereits eine Sitzung; der Login-Bildschirm
                // erkennt das in onResume und leitet weiter.
                if (success) finish()
            }
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun setBusy(busy: Boolean, vararg buttons: View) {
        buttons.forEach { it.isEnabled = !busy }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
