package com.example.mobilese

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Nimmt die Push-Nachrichten entgegen.
 *
 * Der Server schickt ausschliesslich Datennachrichten und keine
 * notification-Nutzlast. Das ist Absicht: eine notification-Nachricht wuerde
 * Android bei geschlossener App selbst anzeigen, ohne dass diese Klasse je
 * liefe - dann gaebe es weder den richtigen Kanal noch ein Ziel beim Antippen,
 * und die Texte kaemen vom Server statt aus den Sprachdateien der App.
 */
class CrewFitMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val content = PushMessages.from(message.data, AppStrings(this)) ?: return
        Notifications.createChannels(this)
        Notifications.show(this, content.channelId, content.title, content.text, content.id)
    }

    /**
     * Die Kennung dieses Geraets hat sich geaendert - etwa nach einer
     * Neuinstallation oder wenn Firebase sie erneuert. Ohne das Nachtragen
     * wuerden Nachrichten weiterhin an die alte Kennung gehen und nirgends
     * ankommen.
     */
    override fun onNewToken(token: String) {
        scope.launch {
            AppRepository.get(applicationContext).savePushToken(token)
        }
    }

    /** Holt die Texte aus den Ressourcen; die Zuordnung selbst steht in [PushMessages]. */
    private class AppStrings(private val context: Context) : PushMessages.Strings {
        override fun unknownMember() = context.getString(R.string.unknown_member)
        override fun activityTitle() = context.getString(R.string.push_activity_title)
        override fun activityText(name: String, sport: String, duration: String) =
            context.getString(R.string.push_activity_text, name, sport, duration)

        override fun rankingTitle() = context.getString(R.string.push_ranking_title)
        override fun overtakeText(name: String, rank: Int) =
            context.getString(R.string.push_overtake_text, name, rank)

        override fun leadText(name: String) = context.getString(R.string.push_lead_text, name)

        override fun unknownCrew() = context.getString(R.string.unknown_crew)
        override fun battleTitle() = context.getString(R.string.push_battle_title)

        /**
         * Die Art kommt als gespeicherter Name herein und wird hier
         * uebersetzt. contributionRes ist der Text mit der blossen Einheit -
         * aus 50 und DISTANCE wird "50 km".
         */
        override fun battleText(crew: String, type: String, goal: Int): String {
            val target = context.getString(ChallengeType.fromStored(type).contributionRes, goal)
            return context.getString(R.string.push_battle_text, crew, target)
        }
    }

    private companion object {
        init {
            Log.i("CrewFitPush", "Messaging service loaded")
        }
    }
}
