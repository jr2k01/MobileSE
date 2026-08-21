package com.example.mobilese

/**
 * Das laufende gemeinsame Training.
 *
 * Ein einzelnes Objekt fuer die ganze App, und das ist hier die Ausnahme wert:
 * Die Bluetooth-Verbindung muss zwei Bildschirme ueberdauern. Sie entsteht im
 * Partnerbildschirm, dort laeuft auch die Uhr - aber gebraucht wird sie noch
 * einmal im Workout-Formular, wenn die Standorte verglichen werden. Haenge sie
 * an einer Activity, waere sie beim Wechsel dorthin weg.
 *
 * Alles hier gilt fuer genau ein Training. [clear] raeumt es weg, sobald es
 * gespeichert oder verworfen wurde - was liegen bleibt, waere beim naechsten
 * Mal eine Verbindung, die es nicht mehr gibt.
 */
object JointSession {

    /** Die offene Verbindung, oder null wenn gerade keine besteht. */
    var link: PartnerLink? = null
        private set

    /**
     * Mit wem trainiert wird - einer oder mehrere.
     *
     * Zu dritt oder zu sechst laufen zu gehen ist derselbe Vorgang wie zu
     * zweit; nur die Liste ist laenger. Die doppelten Punkte bleiben doppelt,
     * unabhaengig von der Zahl - sonst waere eine grosse Gruppe ein
     * Punkteautomat.
     */
    val partners = mutableListOf<UserProfile>()

    /** Die Sportart, die der Startende gewaehlt hat. */
    var sport: String? = null

    /** Die gestoppte Dauer in Sekunden, sobald das Training beendet ist. */
    var seconds: Int? = null

    /**
     * Ob dieses Geraet das Training fuehrt.
     *
     * Der Startende waehlt die Sportart und bestimmt Beginn und Ende; der
     * andere folgt. So kann die Sportart gar nicht auseinandergehen - sie wird
     * nicht verglichen, sondern nur von einer Seite gesetzt.
     */
    var isLeader: Boolean = false

    /** Ob die Verbindung im Moment steht. Faellt sie, haelt die Uhr an. */
    var connected: Boolean = false

    fun begin(link: PartnerLink, isLeader: Boolean) {
        this.link = link
        this.isLeader = isLeader
        this.connected = true
        partners.clear()
        sport = null
        seconds = null
    }

    /** Nimmt jemanden in die Runde auf. Doppelte werden uebergangen. */
    fun add(member: UserProfile) {
        if (partners.none { it.id == member.id }) partners.add(member)
    }

    /** Ob ueberhaupt jemand dabei ist. */
    fun hasPartners(): Boolean = partners.isNotEmpty()

    /** Ob ein beendetes Training auf sein Formular wartet. */
    fun isFinished(): Boolean = hasPartners() && seconds != null

    /**
     * Beendet die Sitzung und schliesst die Verbindung.
     *
     * Muss nach dem Speichern **und** nach dem Verwerfen gerufen werden: eine
     * offene Verbindung zieht auf beiden Geraeten Akku, auch wenn niemand mehr
     * hinsieht.
     */
    fun clear() {
        link?.close()
        link = null
        partners.clear()
        sport = null
        seconds = null
        isLeader = false
        connected = false
    }
}
