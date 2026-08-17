package com.example.mobilese

/**
 * Das Tagesziel fuer die Schrittzahl.
 *
 * Zehntausend Schritte sind der gaengige Richtwert - keine medizinische Groesse,
 * aber eine Zahl, die jeder kennt und einordnen kann. Sie steht hier an einer
 * Stelle, damit Anzeige und die spaetere Gutschrift von Bonuspunkten
 * unmoeglich auseinanderlaufen koennen.
 *
 * Reine Logik ohne Android-Bezug, also ohne Emulator testbar.
 */
object StepGoal {

    const val DAILY_STEPS = 10_000

    /**
     * Der Fuellstand des Rings in Prozent.
     *
     * Bei mehr als dem Ziel bleibt es bei 100: der Ring kann nicht ueberlaufen,
     * und "voll" ist die Aussage, um die es geht. Negative Werte kann es nicht
     * geben, sie werden trotzdem abgefangen - eine Schrittzahl kommt von
     * ausserhalb der App.
     *
     * Begrenzt wird vor der Multiplikation, nicht danach. Andersherum lief
     * steps * 100 bei sehr grossen Werten ueber, wurde negativ und der Ring
     * stand bei einer absurd hohen Schrittzahl auf null statt voll.
     */
    fun progressPercent(steps: Long): Int {
        val capped = steps.coerceIn(0L, DAILY_STEPS.toLong())
        return ((capped * 100) / DAILY_STEPS).toInt()
    }

    /** Ob das Tagesziel erreicht ist. Grundlage der spaeteren Bonuspunkte. */
    fun isReached(steps: Long): Boolean = steps >= DAILY_STEPS
}
