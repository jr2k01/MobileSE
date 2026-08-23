package com.example.mobilese.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Die beiden Benachrichtigungen der Uhr.
 *
 * Zwei Kanaele, weil Android sie getrennt abschaltbar macht und weil sie
 * verschiedene Dinge sind: die eine begleitet ein laufendes Training und soll
 * still sein, die andere meldet ein Ergebnis und darf sich bemerkbar machen.
 *
 * Ohne Bibliothek: Kanaele und Benachrichtigungen gehoeren zum SDK,
 * NotificationCompat kommt aus androidx.core, das ohnehin eingebunden ist.
 */
object WatchNotifications {

    /**
     * Das laufende Workout.
     *
     * Niedrige Wichtigkeit: die Meldung ist die Pflicht eines
     * Vordergrunddienstes und der Weg zurueck ins Training - kein Anlass, bei
     * jedem Blick aufs Handgelenk zu vibrieren.
     */
    const val CHANNEL_WORKOUT = "running_workout"

    /** Die Rueckmeldung vom Telefon, dass das Workout in der Crew steht. */
    const val CHANNEL_LOGGED = "logged_workout"

    /**
     * Feste Kennung: es gibt nie zwei laufende Workouts, und der
     * Vordergrunddienst braucht dieselbe Nummer zum Aktualisieren.
     */
    const val ID_WORKOUT = 1

    /** Ebenso fest: eine neue Rueckmeldung ersetzt die vorige. */
    const val ID_LOGGED = 2

    /**
     * Legt die Kanaele an. Mehrfach aufzurufen schadet nicht - ein bereits
     * vorhandener Kanal wird nicht ueberschrieben.
     */
    fun createChannels(context: Context) {
        // Kein Versionsvorbehalt noetig: Kanaele gibt es ab Android 8, und
        // Wear OS 3 setzt Android 11 voraus.
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.channel_workout),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOGGED,
                context.getString(R.string.channel_logged),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /**
     * Die Meldung, die das laufende Workout begleitet.
     *
     * Ohne Zeitangabe im Text: die muesste im Sekundentakt neu gesetzt werden,
     * und eine Uhr, die dafuer jede Sekunde aufwacht, haelt den Tag nicht durch.
     * Die Zeit steht im Training selbst, und genau dorthin fuehrt ein Tippen.
     */
    fun ongoing(context: Context, sport: String, running: Boolean): Notification {
        val back = PendingIntent.getActivity(
            context,
            0,
            WorkoutActivity.resumeIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle(sport)
            .setContentText(
                context.getString(if (running) R.string.notification_running else R.string.paused)
            )
            .setContentIntent(back)
            // Nicht wegwischbar: solange sie steht, laeuft ein Training - und
            // ohne sie faende der Nutzer nicht mehr zurueck.
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    /** Meldet, dass das Telefon das Workout in die Crew eingetragen hat. */
    fun showLogged(context: Context, title: String, text: String) {
        if (!isAllowed(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_LOGGED)
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ID_LOGGED, notification)
    }

    /**
     * Ob gemeldet werden darf.
     *
     * Ab Android 13 ist das eine Erlaubnis wie jede andere. Fehlt sie, laeuft
     * der Vordergrunddienst zwar weiter, seine Meldung ist aber unsichtbar -
     * deshalb wird sie zusammen mit den Sensorrechten erfragt.
     */
    fun isAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
}
