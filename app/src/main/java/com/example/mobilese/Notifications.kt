package com.example.mobilese

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Anzeigen der Benachrichtigungen, die per Push hereinkommen.
 *
 * Drei Kanaele, weil Android sie getrennt abschaltbar macht: wen die Workouts
 * der anderen nerven, soll trotzdem erfahren koennen, dass er ueberholt wurde -
 * und umgekehrt. Ein gemeinsamer Kanal waere nur eine Zeile weniger Code und
 * naehme dem Nutzer diese Wahl.
 *
 * Ohne Bibliothek: Kanaele und Benachrichtigungen gehoeren zum SDK,
 * NotificationCompat kommt aus androidx.core, das ohnehin eingebunden ist.
 */
object Notifications {

    const val CHANNEL_ACTIVITIES = "crew_activities"
    const val CHANNEL_RANKING = "crew_ranking"

    /**
     * Workouts, die auf der Uhr beendet wurden und noch Foto und Ort brauchen.
     *
     * Ein eigener Kanal, weil das keine Nachricht ueber andere ist, sondern
     * eine offene Aufgabe des Nutzers selbst - die will man auch dann noch
     * bekommen, wenn einem der Rest zu viel geworden ist.
     */
    const val CHANNEL_WATCH = "watch_workouts"

    /**
     * Legt die Kanaele an. Mehrfach aufzurufen schadet nicht - ein bereits
     * vorhandener Kanal wird nicht ueberschrieben, weshalb spaetere Aenderungen
     * an Name oder Wichtigkeit nur neue Installationen erreichen.
     */
    fun createChannels(context: Context) {
        // Kein Versionsvorbehalt noetig: Kanaele gibt es ab Android 8, und die
        // App setzt ohnehin mindestens 8 voraus.
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTIVITIES,
                context.getString(R.string.channel_activities),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_activities_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RANKING,
                context.getString(R.string.channel_ranking),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_ranking_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                context.getString(R.string.channel_watch),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_watch_desc) }
        )
    }

    /**
     * Zeigt eine Benachrichtigung an.
     *
     * Das Antippen fuehrt in die App: bei einem Rangwechsel auf die Rangliste,
     * bei einem Workout von der Uhr in dessen Formular, sonst auf den
     * Startbildschirm. Ohne Ziel waere die Meldung eine Sackgasse - man liest,
     * dass jemand vorbeigezogen ist, und muss die App selbst suchen.
     *
     * @param id gleiche Zahl ersetzt eine bestehende Meldung, statt eine zweite
     *           danebenzulegen.
     * @param extras was der geoeffnete Bildschirm mitbekommen soll - etwa
     *               welches Workout von der Uhr gemeint ist.
     */
    @SuppressLint("MissingPermission") // durch isAllowed() gleich darunter geprueft
    fun show(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        id: Int,
        extras: Bundle? = null
    ) {
        if (!isAllowed(context)) return

        val target = when (channelId) {
            CHANNEL_RANKING -> LeaderboardActivity::class.java
            // Direkt in den Eintragen-Bildschirm: die Meldung sagt, dass etwas
            // zu tun ist, also soll das Antippen genau dorthin fuehren und
            // nicht auf den Startbildschirm, wo man es noch einmal suchen muss.
            CHANNEL_WATCH -> WorkoutTrackingActivity::class.java
            else -> MainHubActivity::class.java
        }

        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras?.let { putExtras(it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            channelId.hashCode(),
            intent,
            // UPDATE_CURRENT ersetzt die Zusatzdaten eines schon bestehenden
            // Eintrags - noetig, weil alle Meldungen eines Kanals sich einen
            // teilen: sonst fuehrte die zweite Meldung von der Uhr noch zum
            // ersten Workout.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * Ob gezeigt werden darf. Ab Android 13 ist das eine Berechtigung, davor
     * gab es sie nicht - dort ist die Antwort immer ja.
     */
    fun isAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
