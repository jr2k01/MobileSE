# Smartwatch anbinden

CrewFit besteht aus zwei Apps: der auf dem Telefon und einer eigenen, kleinen
auf der Uhr (Modul `wear`). Auf der Uhr startet man ein Workout, waehrenddessen
laeuft der Puls mit, und mit "Stopp" ist es beendet. Auf dem Telefon taucht es
danach als **wartendes Workout** auf - Sportart, Dauer und Puls stehen schon da,
Foto und Standort traegt man dort nach. Erst dann geht es in die Crew.

Die Aufteilung ist Absicht: die Uhr weiss, was nur sie wissen kann, und das
Telefon hat Kamera, Tastatur und die Verbindung zur Datenbank.

**Ohne Uhr laeuft die App unveraendert.** Das `wear`-Modul ist ein eigenes APK
und wird gar nicht erst installiert.

---

## 1. Was wo liegt

| Datei | Seite | Aufgabe |
|---|---|---|
| `wear/.../SportChoiceActivity.kt` | Uhr | Sportart waehlen |
| `wear/.../WorkoutActivity.kt` | Uhr | Stoppuhr, Puls, "Stopp" |
| `wear/.../HeartRateReader.kt` | Uhr | Pulssensor, Durchschnitt und Hoechstwert |
| `wear/.../PhoneLink.kt` | Uhr | legt das Workout fuer das Telefon ab |
| `app/.../WatchWorkoutService.kt` | Telefon | nimmt es entgegen |
| `app/.../PendingWorkouts.kt` | Telefon | Warteschlange der offenen Workouts |
| `WatchProtocol.kt` | **beide** | Pfad und Feldnamen |

`WatchProtocol.kt` gibt es zweimal - einmal im `wear`-, einmal im `app`-Modul.
Die beiden APKs teilen keinen Code. Wer dort etwas aendert, muss es auf der
anderen Seite nachziehen, sonst kommt der Datensatz an und niemand versteht ihn.

## 2. Warum DataClient und nicht MessageClient

Eine Nachricht (`MessageClient`) kommt nur an, solange das Telefon gerade in
Reichweite ist - sonst ist sie weg. Beim Laufen hat man das Telefon aber oft
nicht dabei, und genau dafuer ist die Uhr da. Ein Datensatz (`DataClient`)
bleibt liegen und wird uebertragen, sobald sich beide wiedersehen.

Jedes Workout bekommt einen eigenen Pfad mit seinem Endzeitpunkt darin
(`/crewfit/workout/1787252096000`). Zwei Workouts unter demselben Pfad waeren
fuer die Datenschicht dasselbe Element, und das zweite ueberschriebe das erste,
bevor das Telefon es gesehen hat. Das Telefon loescht den Datensatz, nachdem es
ihn in die Warteschlange uebernommen hat.

## 3. Beide Apps muessen dieselbe applicationId haben

`wear/build.gradle.kts` setzt `applicationId = "com.example.mobilese"` - denselben
Wert wie das Telefon-Modul, obwohl der `namespace` ein anderer ist. Die
Datenschicht stellt nur zwischen Apps mit gleicher Kennung und gleicher
Signatur zu. Mit einer eigenen Kennung fuer die Uhr kaeme nie etwas an, ohne
dass irgendwo ein Fehler auftauchte.

## 4. Auf einem Emulator ausprobieren

1. Im Geraete-Manager von Android Studio eine Wear-OS-Uhr anlegen und starten.
2. Die Uhr-App darauf installieren:

   ```
   gradlew :wear:installDebug
   ```

3. Uhr und Telefon **koppeln**. Ohne Kopplung gibt es keine Datenschicht
   zwischen beiden, und das Workout bleibt auf der Uhr liegen. In Android
   Studio: Geraete-Manager → beim Telefon-Emulator das Uhr-Symbol
   ("Pair Devices"). Dafuer muss auf dem Telefon-Emulator die App **Wear OS**
   aus dem Play Store installiert sein, wofuer eine Google-Anmeldung noetig ist.
4. Auf einer frisch angelegten Uhr blockiert der Einrichtungsassistent das
   Starten von Apps ("user setup not complete"). Entweder durchklicken oder:

   ```
   adb -s emulator-5556 shell settings put secure user_setup_complete 1
   ```

5. Der Emulator hat einen Pulssensor, der von Hand gesetzt werden kann:

   ```
   adb -s emulator-5556 emu sensor set heart-rate 140
   ```

## 5. Was auf dem Telefon passiert

`WatchWorkoutService` wird von den Play-Diensten gestartet, sobald ein Datensatz
ankommt - auch wenn die App geschlossen ist. Er laedt nichts hoch, sondern legt
das Workout in die Warteschlange (`PendingWorkouts`, in den SharedPreferences)
und zeigt eine Benachrichtigung. Auf dem Startbildschirm steht dann eine Karte
"Waiting from your watch"; beides fuehrt in das Formular, in dem Sportart, Dauer
und Puls schon eingetragen sind.

Der Puls von der Uhr hat Vorrang vor dem, was Health Connect fuer denselben
Zeitraum liefert: die Uhr hat waehrend des Workouts gemessen, alles andere ist
nachtraeglich zusammengesucht.

Ein Workout verschwindet erst dann aus der Warteschlange, wenn es wirklich
gespeichert ist. Bricht man das Formular ab oder scheitert der Upload, steht die
Karte beim naechsten Blick wieder da.
