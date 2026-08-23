package com.example.mobilese.wear

import android.util.Log
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Macht aus einem Task der Play-Dienste eine suspend-Funktion.
 *
 * Von Hand statt ueber kotlinx-coroutines-play-services: die Bibliothek
 * braechte fuer genau diese zehn Zeilen eine weitere Abhaengigkeit mit.
 *
 * Eine eigene Datei, seit zwei Stellen sie brauchen - [PhoneLink] beim Ablegen
 * des Workouts und [PhoneReach] beim Nachsehen, ob das Telefon da ist.
 *
 * @return das Ergebnis, oder null wenn der Task fehlschlug. Ein Fehler ist
 *         hier nichts Aussergewoehnliches: die Uhr ist oft allein unterwegs.
 */
suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { finished ->
            if (finished.isSuccessful) {
                continuation.resume(finished.result)
            } else {
                Log.w("CrewFitWear", "Task failed: ${finished.exception?.message}")
                continuation.resume(null)
            }
        }
    }
