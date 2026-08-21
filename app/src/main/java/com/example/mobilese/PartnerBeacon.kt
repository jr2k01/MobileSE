package com.example.mobilese

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

/**
 * Sucht ueber Bluetooth Low Energy nach Crew-Mitgliedern in Reichweite.
 *
 * Das Geraet tut zweierlei zugleich: Es **sendet** ein kleines Paket mit der
 * eigenen Kennung aus, und es **hoert** auf die Pakete der anderen. Erst
 * beides zusammen ergibt eine Kopplung - wer nur hoert, wird selbst nicht
 * gefunden.
 *
 * Bewusst mit den Bordmitteln des SDK (`android.bluetooth.le`) und nicht mit
 * der Nearby Connections API: die gehoert zu den Play-Diensten und waere eine
 * weitere Fremdbibliothek fuer etwas, das Android selbst kann.
 *
 * ## Was das Verfahren nicht kann
 *
 * Gefunden wird nur, wer **gleichzeitig** in diesem Bildschirm steht. Ein
 * Telefon, auf dem CrewFit geschlossen ist, sendet nichts und ist damit
 * unsichtbar. Das laesst sich nicht umgehen: eine App, die dauerhaft im
 * Hintergrund sendet, kostet Akku und braucht auf neueren Android-Fassungen
 * einen sichtbaren Vordergrunddienst.
 *
 * ## Senden kann nicht jedes Geraet
 *
 * Aeltere Telefone koennen empfangen, aber nicht senden
 * ([BluetoothAdapter.isMultipleAdvertisementSupported]). Der Bildschirm
 * arbeitet dann trotzdem weiter - nur muss die andere Seite ein Geraet haben,
 * das senden kann. Deshalb ist das kein Abbruch, sondern ein Hinweis.
 *
 * @param onFound Wird je gefundenem Mitglied hoechstens einmal gerufen, auf
 *        dem Thread des Bluetooth-Systems. Der Aufrufer muss selbst dafuer
 *        sorgen, dass er den Bildschirm nicht von dort anfasst.
 * @param onProblem Bekommt eine Textressource, die erklaert, was fehlt.
 */
class PartnerBeacon(
    private val context: Context,
    private val ownUserId: String,
    private val members: List<UserProfile>,
    private val onFound: (UserProfile) -> Unit,
    private val onProblem: (Int) -> Unit
) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Wen wir schon gemeldet haben - dasselbe Paket kommt im Sekundentakt. */
    private val seen = mutableSetOf<String>()

    private var running = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            // Kein Abbruch: Empfangen geht weiter, und wenn die Gegenseite
            // senden kann, kommt die Kopplung trotzdem zustande.
            Log.e(TAG, "Advertising failed: $errorCode")
            onProblem(R.string.partner_cannot_advertise)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val payload = result.scanRecord
                ?.getServiceData(ParcelUuid(CoLocation.SERVICE_UUID))
                ?: return

            val member = CoLocation.memberFor(payload, members, ownUserId) ?: return
            if (seen.add(member.id)) onFound(member)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            onProblem(R.string.partner_scan_failed)
        }
    }

    /**
     * Beginnt zu senden und zu hoeren.
     *
     * Die Berechtigungen muss der Aufrufer vorher besorgt haben; ohne sie
     * wirft das System eine SecurityException, die hier abgefangen wird -
     * eine App darf daran nicht sterben.
     */
    fun start() {
        if (running) return

        val adapter = this.adapter
        if (adapter == null) {
            onProblem(R.string.partner_no_bluetooth)
            return
        }
        if (!adapter.isEnabled) {
            onProblem(R.string.partner_bluetooth_off)
            return
        }

        val payload = CoLocation.payloadFor(ownUserId)
        if (payload == null) {
            onProblem(R.string.partner_scan_failed)
            return
        }

        if (!AppPermission.NEARBY.isGranted(context)) {
            onProblem(R.string.partner_permission_missing)
            return
        }

        running = true
        startScanning(adapter)
        startAdvertising(adapter, payload)
    }

    /**
     * Die Berechtigung ist in [start] geprueft. Der Fang steht trotzdem hier:
     * zwischen Pruefung und Aufruf kann sie entzogen worden sein, und eine
     * SecurityException wuerde die App mitnehmen.
     */
    private fun startScanning(adapter: BluetoothAdapter) {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(CoLocation.SERVICE_UUID))
            .build()

        // LOW_LATENCY: die Leute stehen nebeneinander und warten auf den
        // Bildschirm. Ein sparsamer Modus braucht mehrere Sekunden je Fund und
        // fuehlt sich an, als ginge es nicht.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            adapter.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to scan: ${e.message}")
            onProblem(R.string.partner_permission_missing)
        }
    }

    private fun startAdvertising(adapter: BluetoothAdapter, payload: ByteArray) {
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null || !adapter.isMultipleAdvertisementSupported) {
            onProblem(R.string.partner_cannot_advertise)
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        // Der Geraetename bleibt draussen: er waere der laengste Teil des
        // Pakets, und in 31 Byte ist dafuer kein Platz. Gebraucht wird er
        // ohnehin nicht - der Name kommt aus der Crew-Liste.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(CoLocation.SERVICE_UUID))
            .addServiceData(ParcelUuid(CoLocation.SERVICE_UUID), payload)
            .build()

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to advertise: ${e.message}")
            onProblem(R.string.partner_cannot_advertise)
        }
    }

    /**
     * Hoert auf. Muss beim Verlassen des Bildschirms gerufen werden - Senden
     * und Empfangen laufen sonst weiter und ziehen Akku, auch wenn niemand
     * mehr hinsieht.
     */
    fun stop() {
        if (!running) return
        running = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not stop cleanly: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "PartnerBeacon"
    }
}
