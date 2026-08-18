package com.example.mobilese

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Die allgemeinen Einstellungen, erreichbar ueber das Zahnrad auf dem
 * Startbildschirm.
 *
 * Hier steht, was die App betrifft und nicht die eigenen Daten: der Zugriff auf
 * die Schrittzahl und das Konto. Abmelden und Konto loeschen standen vorher
 * unter dem Profilformular und sind hierher gewandert - unter der eigenen
 * Groesse und dem Gewicht sucht sie niemand, und ein zweiter Weg zum Loeschen
 * waere schlimmer als der falsche.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    /**
     * Dieselbe Abfrage wie auf dem Startbildschirm. Health Connect verlangt,
     * dass die Erlaubnis ueber diesen Vertrag erfragt wird; ein gewoehnlicher
     * Berechtigungsdialog reicht dafuer nicht.
     */
    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (!granted.containsAll(HealthSteps.PERMISSIONS)) {
                Toast.makeText(this, R.string.steps_permission_denied, Toast.LENGTH_LONG).show()
            }
            showHealthStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_settings)

        repository = AppRepository.get(this)
        setUpTopBar(R.string.settings_title)

        findViewById<TextView>(R.id.tvSettingsEmail).text = repository.getCurrentUser().orEmpty()
        findViewById<TextView>(R.id.tvSettingsVersion).text =
            getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        findViewById<View>(R.id.llSettingsAccount).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<View>(R.id.llStepsPrivacy).setOnClickListener {
            startActivity(Intent(this, HealthPrivacyActivity::class.java))
        }
        findViewById<View>(R.id.llHealthConnect).setOnClickListener { onHealthConnectTapped() }
        findViewById<View>(R.id.llLogout).setOnClickListener { logout() }
        findViewById<View>(R.id.llDeleteProfile).setOnClickListener { confirmDeletion() }
    }

    /**
     * Der Stand wird bei jedem Zurueckkommen neu geholt: die Erlaubnis kann in
     * der Zwischenzeit in Health Connect selbst zurueckgenommen worden sein,
     * und davon erfaehrt die App nicht.
     */
    override fun onResume() {
        super.onResume()
        showHealthStatus()
    }

    private fun showHealthStatus() {
        val row = findViewById<View>(R.id.llHealthConnect)
        val status = findViewById<TextView>(R.id.tvHealthConnectStatus)

        if (!HealthSteps.isAvailable(this)) {
            // Ohne Health Connect auf dem Geraet gibt es nichts zu erlauben.
            row.isEnabled = false
            row.alpha = DISABLED_ALPHA
            status.setText(R.string.steps_hint_unavailable)
            return
        }

        row.isEnabled = true
        row.alpha = 1f
        lifecycleScope.launch {
            status.setText(
                if (HealthSteps.isAllowed(this@SettingsActivity)) R.string.settings_health_connected
                else R.string.settings_health_not_connected
            )
        }
    }

    /**
     * Fehlt die Erlaubnis, wird sie hier erfragt. Steht sie schon, fuehrt der
     * Weg nach Health Connect - zuruecknehmen laesst sie sich nur dort, eine
     * App kann sich ihre eigene Erlaubnis nicht entziehen.
     */
    private fun onHealthConnectTapped() {
        lifecycleScope.launch {
            if (HealthSteps.isAllowed(this@SettingsActivity)) {
                openHealthConnectSettings()
            } else {
                requestHealthPermissions.launch(HealthSteps.PERMISSIONS)
            }
        }
    }

    private fun openHealthConnectSettings() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            // Auf manchen Geraeten hat Health Connect keinen eigenen Eintrag,
            // den sich von aussen oeffnen laesst.
            Toast.makeText(this, R.string.steps_hint_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            // logout() ist suspend, weil es zusaetzlich die Supabase-Sitzung
            // beendet und nicht nur den lokalen Sitzungsmarker loescht.
            repository.logout()
            openLogin()
        }
    }

    private fun confirmDeletion() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_profile_title)
            .setMessage(R.string.delete_profile_message)
            .setPositiveButton(R.string.delete_profile_confirm) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteUserProfile()
                    Toast.makeText(this@SettingsActivity, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                    openLogin()
                }
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    private fun openLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.5f
    }
}
