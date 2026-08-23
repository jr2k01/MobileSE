package com.example.mobilese.wear

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Traegt das laufende Workout - unabhaengig davon, ob jemand hinsieht.
 *
 * Vorher lag alles in der Activity: Uhr, Sensoren, Zwischenstaende. Wer
 * waehrend des Trainings die Handflaeche aufs Display legte, zum Zifferblatt
 * wischte oder eine andere App oeffnete, verlor das Workout, sobald das System
 * die Activity abraeumte - und zwar ohne Meldung. Hier laeuft es weiter, und
 * die Activity ist nur noch die Anzeige davor.
 *
 * Der Dienst wird **gestartet und gebunden**. Gestartet, damit er die Activity
 * ueberlebt; gebunden, damit die Activity ohne Umweg an die Zahlen kommt, die
 * sie jede Sekunde zeichnet.
 *
 * Zusaetzlich schreibt [WorkoutStore] den Stand weg. Ein Vordergrunddienst ist
 * kein Versprechen: bei knappem Speicher raeumt das System auch ihn ab. Dann
 * startet Android ihn ueber `START_STICKY` neu, und er nimmt den Faden dort
 * wieder auf.
 */
class WorkoutService : Service() {

    inner class LocalBinder : Binder() {
        val service: WorkoutService get() = this@WorkoutService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var heartRate: HeartRateReader
    private lateinit var steps: StepCounter

    private var stopwatch = Stopwatch()
    private var keeper: Job? = null
    private var isForeground = false

    /** Wohin die Antwort geht, wenn das Verschicken durch ist. */
    private var onFinished: ((String) -> Unit)? = null

    /**
     * Die Sportart des laufenden Workouts, oder null wenn keines laeuft.
     *
     * Zugleich die Antwort auf die Frage, ob es ueberhaupt etwas anzuzeigen
     * gibt: die Activity beendet sich, wenn hier nichts steht.
     */
    var sport: String? = null
        private set

    val isRunning: Boolean get() = stopwatch.isRunning

    fun elapsedSeconds(): Long = stopwatch.elapsedSeconds(SystemClock.elapsedRealtime())

    fun hasHeartRateSensor(): Boolean = heartRate.isAvailable()

    fun hasStepSensor(): Boolean = steps.isAvailable()

    fun latestHeartRate(): Int = heartRate.latest

    fun countedSteps(): Int = steps.steps

    override fun onCreate() {
        super.onCreate()
        WatchNotifications.createChannels(this)
        heartRate = HeartRateReader(this)
        steps = StepCounter(this)
    }

    /**
     * @param intent null, wenn das System den Dienst nach einem Prozesstod neu
     *        gestartet hat. Dann steht in der Absicht nichts mehr, und der
     *        Stand kommt aus der Ablage.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = when (intent?.action) {
            ACTION_START -> {
                begin(intent.getStringExtra(EXTRA_SPORT) ?: WatchSports.ALL.first())
                true
            }
            // Fortsetzen: laeuft schon eines, bleibt es dabei - sonst aus der
            // Ablage. Findet sich auch dort nichts, gibt es nichts zu tun.
            else -> sport != null || restore()
        }

        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        showNotification()
        startKeeping()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Die Activity ist weg - der Dienst bleibt.
     *
     * Nur die Rueckmeldung wird abgeraeumt: sie zeigt auf eine Activity, die
     * es nicht mehr gibt, und festzuhalten waere ein Leck.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        onFinished = null
        return true
    }

    override fun onRebind(intent: Intent?) = Unit

    private fun begin(sport: String) {
        this.sport = sport
        stopwatch = Stopwatch()
        stopwatch.start(SystemClock.elapsedRealtime())
        // Frische Zaehler: der Dienst kann aus einem vorigen Training noch
        // stehen, und dessen Puls hat mit diesem hier nichts zu tun.
        heartRate = HeartRateReader(this)
        steps = StepCounter(this)
        // Die Rueckmeldung zum vorigen Workout ist mit diesem hier erledigt.
        LastLogged.clear(this)
        attachSensors()
        save()
    }

    private fun restore(): Boolean {
        val saved = WorkoutStore.load(this) ?: return false

        sport = saved.sport
        stopwatch = Stopwatch(saved.accumulatedMillis, saved.runningSince)
        heartRate = HeartRateReader(this).apply { restore(saved.heartRate) }
        steps = StepCounter(this).apply { restore(saved.steps) }
        if (stopwatch.isRunning) attachSensors()
        return true
    }

    /**
     * Meldet die Sensoren an, die es gibt und die erlaubt sind.
     *
     * Oeffentlich, weil die Activity die Rechte erfragt: wird waehrend des
     * laufenden Trainings zugestimmt, soll ab da gemessen werden und nicht
     * erst beim naechsten Mal.
     */
    fun attachSensors() {
        if (heartRate.isAvailable() && isGranted(Manifest.permission.BODY_SENSORS)) {
            heartRate.start()
        }
        if (steps.isAvailable() && isGranted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            steps.start()
        }
        goForeground()
    }

    fun pause() {
        stopwatch.pause(SystemClock.elapsedRealtime())
        // In der Pause auch die Sensoren aus: der Puls im Stehen zoege den
        // Durchschnitt nach unten, und der Weg zum Getraenkeautomat zaehlte
        // als Trainingsschritte.
        heartRate.stop()
        steps.stop()
        save()
        showNotification()
    }

    fun resume() {
        stopwatch.start(SystemClock.elapsedRealtime())
        attachSensors()
        save()
        showNotification()
    }

    /**
     * Beendet das Workout und schickt es ans Telefon.
     *
     * Das Verschicken laeuft im Dienst und nicht in der Activity: wer nach dem
     * Stopp sofort das Handgelenk senkt, nimmt der Activity den Boden unter den
     * Fuessen weg, und mit ihr staerbe die Uebertragung. Der Dienst haelt sich
     * selbst am Leben, bis er fertig ist.
     *
     * @param onDone bekommt den Text fuer den Nutzer, falls die Activity dann
     *        noch da ist. Ist sie es nicht, sieht ihn niemand - dann ist auch
     *        nichts verloren.
     */
    fun finish(onDone: (String) -> Unit) {
        val sport = this.sport ?: return
        val now = SystemClock.elapsedRealtime()

        stopwatch.pause(now)
        heartRate.stop()
        steps.stop()
        keeper?.cancel()

        val minutes = stopwatch.elapsedMinutes(now)
        // Ab hier gibt es nichts mehr fortzusetzen. Erst die Ablage raeumen,
        // dann verschicken: bliebe sie stehen und der Prozess staerbe
        // dazwischen, boete die Uhr dasselbe Training ein zweites Mal an.
        this.sport = null
        WorkoutStore.clear(this)
        onFinished = onDone
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false

        scope.launch {
            val message = if (minutes < 1) {
                // Unter einer Minute wird nichts geschickt: die App zaehlt in
                // Minuten, und ein Workout von null Minuten waere in der
                // Rangliste ohnehin nichts wert.
                getString(R.string.too_short)
            } else {
                deliver(sport, minutes)
            }
            onFinished?.invoke(message)
            stopSelf()
        }
    }

    private suspend fun deliver(sport: String, minutes: Int): String {
        val sent = PhoneLink.send(
            this,
            sport,
            minutes,
            heartRate.average,
            heartRate.max,
            steps.steps
        )
        return when {
            !sent -> getString(R.string.not_sent)
            PhoneReach.isPhoneNearby(this) -> getString(R.string.sent)
            else -> getString(R.string.sent_later)
        }
    }

    /**
     * Sichert die Zwischenstaende in Abstaenden.
     *
     * Die Uhrzeit muss dafuer nicht laufend geschrieben werden - sie ergibt
     * sich aus dem Beginn der Runde. Was sich waehrenddessen aendert, sind
     * Puls und Schritte, und eine halbe Minute davon zu verlieren ist zu
     * verschmerzen. Jede Sekunde auf den Speicher zu schreiben waere es nicht.
     */
    private fun startKeeping() {
        if (keeper?.isActive == true) return
        keeper = scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (sport != null) save()
            }
        }
    }

    private fun save() {
        val sport = this.sport ?: return
        WorkoutStore.save(
            this,
            WorkoutStore.Session(
                sport = sport,
                accumulatedMillis = stopwatch.accumulated,
                runningSince = stopwatch.startedAt,
                heartRate = heartRate.state(),
                steps = steps.state()
            )
        )
    }

    private fun showNotification() {
        val sport = this.sport ?: return
        val notification = WatchNotifications.ongoing(this, sport, stopwatch.isRunning)

        if (isForeground) {
            // Schon im Vordergrund: dann ist das hier nur der neue Text.
            if (WatchNotifications.isAllowed(this)) {
                NotificationManagerCompat.from(this)
                    .notify(WatchNotifications.ID_WORKOUT, notification)
            }
            return
        }
        goForeground()
    }

    /**
     * Geht in den Vordergrund, sofern das System es zulaesst.
     *
     * Ab Android 14 muss ein Dienst seine Art nennen, und die Art `health`
     * setzt voraus, dass mindestens eines der beiden Sensorrechte erteilt ist.
     * Wer beide ablehnt, bekommt also keinen Vordergrunddienst - das Training
     * laeuft dann nur, solange die Activity lebt. Das ist die ehrlichere
     * Antwort: ohne Sensoren ist die Uhr ein Wecker, und ein Wecker braucht
     * keinen Dienst, der ihn ueberlebt.
     */
    private fun goForeground() {
        if (isForeground || sport == null) return
        if (!maySensorsRun()) return

        val notification = WatchNotifications.ongoing(this, sport ?: return, stopwatch.isRunning)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                WatchNotifications.ID_WORKOUT,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            // Aeltere Uhren kennen die Art `health` nicht. Ihr im Aufruf zu
            // nennen wuerde dort abgelehnt, weil das Manifest sie fuer das
            // System gar nicht enthaelt.
            startForeground(WatchNotifications.ID_WORKOUT, notification)
        }
        isForeground = true
    }

    private fun maySensorsRun(): Boolean =
        isGranted(Manifest.permission.BODY_SENSORS) ||
                isGranted(Manifest.permission.ACTIVITY_RECOGNITION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        heartRate.stop()
        steps.stop()
        scope.cancel()
    }

    companion object {
        private const val ACTION_START = "com.example.mobilese.wear.START"
        private const val ACTION_RESUME = "com.example.mobilese.wear.RESUME"
        private const val EXTRA_SPORT = "sport"

        private const val SAVE_INTERVAL_MS = 30_000L

        /** Beginnt ein neues Workout. */
        fun start(context: Context, sport: String) {
            context.startService(
                Intent(context, WorkoutService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SPORT, sport)
            )
        }

        /**
         * Holt ein laufendes Workout zurueck an die Oberflaeche - entweder das
         * des noch lebenden Dienstes oder das aus der Ablage.
         */
        fun resume(context: Context) {
            context.startService(
                Intent(context, WorkoutService::class.java).setAction(ACTION_RESUME)
            )
        }
    }
}
