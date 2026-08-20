package com.example.mobilese

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
            if (granted.none { it in HealthAccess.ALL }) {
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

        showThemeChoice()
        findViewById<View>(R.id.llTheme).setOnClickListener { askForTheme() }

        findViewById<View>(R.id.llNotifications).setOnClickListener { onNotificationsTapped() }

        buildPermissionRows()

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
     * Fragt beim ersten Mal nach der Erlaubnis; steht sie schon oder wurde sie
     * abgelehnt, fuehrt der Weg in die Systemeinstellungen. Eine App darf nur
     * einmal fragen - danach entscheidet das System, und ein zweiter Antipper
     * wuerde sonst wirkungslos verpuffen.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.push_permission_denied, Toast.LENGTH_LONG).show()
            }
            showNotificationStatus()
        }

    private fun showNotificationStatus() {
        findViewById<TextView>(R.id.tvNotificationsStatus).setText(
            if (Notifications.isAllowed(this)) R.string.settings_notifications_on
            else R.string.settings_notifications_off
        )
    }

    private fun onNotificationsTapped() {
        if (Notifications.isAllowed(this)) {
            openAppNotificationSettings()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Vor Android 13 gibt es die Berechtigung nicht; abgeschaltet
            // werden kann sie trotzdem, dann aber nur in den Systemeinstellungen.
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.push_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Die Berechtigung, auf die gerade getippt wurde.
     *
     * Der Vertrag fuer die Abfrage muss vor onCreate feststehen und kann
     * deshalb nicht wissen, welche Zeile ihn ausgeloest hat. Gemerkt wird sie
     * hier - immer nur eine, weil immer nur eine Zeile antippbar ist.
     */
    private var pendingPermission: AppPermission? = null

    /** Die eingehaengten Zeilen, um beim Zurueckkommen den Stand aufzufrischen. */
    private val permissionRows = mutableMapOf<AppPermission, View>()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val asked = pendingPermission
            pendingPermission = null
            showPermissionStatus()

            if (asked == null || asked.isGranted(this)) return@registerForActivityResult

            // Zweimal abgelehnt heisst "nicht mehr fragen": ab da zeigt Android
            // keinen Dialog mehr, und ein weiterer Antipper bliebe wirkungslos.
            // Dann bleibt nur der Weg in die Systemeinstellungen.
            val mayAskAgain = asked.manifestNames.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            if (mayAskAgain) {
                Toast.makeText(this, asked.purposeRes, Toast.LENGTH_LONG).show()
            } else {
                openAppSettings()
            }
        }

    /**
     * Baut die Berechtigungsliste aus [AppPermission].
     *
     * Aus dem Enum und nicht aus dem Layout: eine neue Berechtigung braucht
     * dann nur einen Eintrag dort, und die Einstellungen zeigen sie mit.
     */
    private fun buildPermissionRows() {
        val container = findViewById<LinearLayout>(R.id.llPermissions)
        val inflater = LayoutInflater.from(this)

        AppPermission.entries.forEach { permission ->
            val row = inflater.inflate(R.layout.part_permission_row, container, false)
            row.findViewById<TextView>(R.id.tvPermissionTitle).setText(permission.labelRes)
            row.findViewById<TextView>(R.id.tvPermissionPurpose).setText(permission.purposeRes)
            row.setOnClickListener { onPermissionTapped(permission) }
            container.addView(row)
            permissionRows[permission] = row
        }
        showPermissionStatus()
    }

    private fun showPermissionStatus() {
        permissionRows.forEach { (permission, row) ->
            row.findViewById<TextView>(R.id.tvPermissionStatus).setText(
                if (permission.isGranted(this)) R.string.permission_status_granted
                else R.string.permission_status_denied
            )
        }
    }

    /**
     * Steht die Erlaubnis schon, fuehrt der Weg in die Systemeinstellungen:
     * eine App kann sich ihre eigene Erlaubnis nicht entziehen, zuruecknehmen
     * laesst sie sich nur dort. Fehlt sie, wird gefragt.
     */
    private fun onPermissionTapped(permission: AppPermission) {
        if (permission.isGranted(this)) {
            openAppSettings()
            return
        }
        pendingPermission = permission
        requestPermission.launch(permission.manifestNames)
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.permission_status_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun showThemeChoice() {
        findViewById<TextView>(R.id.tvThemeValue).setText(repository.getThemeMode().labelRes)
    }

    /**
     * Die Auswahl des Erscheinungsbilds.
     *
     * Eine Liste mit drei Punkten und keine Umschalttaste: neben hell und
     * dunkel gibt es die Einstellung des Geraets, und die ist die
     * Voreinstellung. Mit einer Taste liesse sie sich nicht mehr zuruecknehmen,
     * sobald man einmal umgeschaltet hat.
     *
     * Das Umsetzen erledigt AppCompat: es baut die sichtbaren Bildschirme neu
     * auf, sobald der Modus gesetzt ist. Deshalb wird hier nichts von Hand neu
     * gezeichnet - der Dialog wird nur vorher geschlossen, damit er den Neuaufbau
     * nicht ueberlebt.
     */
    private fun askForTheme() {
        val modes = ThemeMode.entries
        val labels = modes.map { getString(it.labelRes) }.toTypedArray()
        val current = modes.indexOf(repository.getThemeMode())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val chosen = modes[which]
                repository.setThemeMode(chosen)
                showThemeChoice()
                ThemeMode.apply(chosen)
            }
            .setNegativeButton(R.string.cancel_btn, null)
            .show()
    }

    /**
     * Der Stand wird bei jedem Zurueckkommen neu geholt: die Erlaubnis kann in
     * der Zwischenzeit in Health Connect selbst zurueckgenommen worden sein,
     * und davon erfaehrt die App nicht.
     */
    override fun onResume() {
        super.onResume()
        showHealthStatus()
        showNotificationStatus()
        showPermissionStatus()
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
                if (HealthAccess.anyGranted(this@SettingsActivity)) R.string.settings_health_connected
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
            if (HealthAccess.anyGranted(this@SettingsActivity)) {
                openHealthConnectSettings()
            } else {
                requestHealthPermissions.launch(HealthAccess.ALL)
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
            // Erst abmelden, dann die Sitzung beenden: das Loeschen der Zeile
            // braucht noch die gueltige Anmeldung. Andersherum blieben die
            // Benachrichtigungen der alten Crew auf diesem Geraet.
            PushTokens.unregister(this@SettingsActivity)
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
