# Smartwatch anbinden

CrewFit besteht aus zwei Apps: der auf dem Telefon und einer eigenen, kleinen
auf der Uhr (Modul `wear`). Auf der Uhr startet man ein Workout, waehrenddessen
laufen Puls und Schritte mit, mit "Pause" haelt es an, und mit "Stopp" ist es
beendet. Auf dem Telefon taucht es danach als **wartendes Workout** auf -
Sportart, Dauer, Puls und Schritte stehen schon da, Foto und Standort traegt man
dort nach. Erst dann geht es in die Crew - und die Uhr erfaehrt, dass es
angekommen ist.

Die Aufteilung ist Absicht: die Uhr weiss, was nur sie wissen kann, und das
Telefon hat Kamera, Tastatur und die Verbindung zur Datenbank.

**Ohne Uhr laeuft die App unveraendert.** Das `wear`-Modul ist ein eigenes APK
und wird gar nicht erst installiert.

**Auf welchen Uhren es laeuft.** Das Modul verlangte urspruenglich Android 11,
also Wear OS 3 und neuer - damit fielen die meisten Uhren heraus, die Leute
tatsaechlich tragen. Gebraucht wurde die Stufe nie: Lint findet keinen Aufruf
oberhalb von Android 8, und die drei Stellen, die etwas Neueres benutzen,
fragen ohnehin vorher die Version ab. Seit dem 28. August steht die Untergrenze
auf **Android 8**, derselben wie bei der Telefon-App. Geprueft am Uhr-Emulator:
Sportauswahl, Sensorabfrage, laufende Stoppuhr mit Puls, Pause, Fortsetzen und
Stopp - ohne Absturz.

---

## 1. Was wo liegt

| Datei | Seite | Aufgabe |
|---|---|---|
| `wear/.../SportChoiceActivity.kt` | Uhr | Sportart waehlen, Zurueck ins laufende Training, die drei Hinweiszeilen |
| `wear/.../WorkoutActivity.kt` | Uhr | Anzeige, "Pause" und "Stopp" |
| `wear/.../WorkoutService.kt` | Uhr | **traegt das Workout**, auch ohne Activity |
| `wear/.../WorkoutStore.kt` | Uhr | der Stand, falls der Prozess stirbt |
| `wear/.../Stopwatch.kt` | Uhr | Zeit mit Pausen, ohne Android testbar |
| `wear/.../HeartRateReader.kt` | Uhr | Pulssensor, Durchschnitt und Hoechstwert |
| `wear/.../StepCounter.kt` | Uhr | Schritte waehrend des Workouts |
| `wear/.../PhoneReach.kt` | Uhr | ist das Telefon in Reichweite? |
| `wear/.../PhoneLink.kt` | Uhr | legt das Workout fuer das Telefon ab |
| `wear/.../PhoneAckService.kt` | Uhr | nimmt die Bestaetigung des Telefons entgegen |
| `wear/.../LastLogged.kt` | Uhr | die letzte Bestaetigung, fuer die Zeile im Startbildschirm |
| `wear/.../WatchNotifications.kt` | Uhr | die beiden Meldungen und ihre Kanaele |
| `wear/.../Tasks.kt` | Uhr | Task der Play-Dienste als suspend-Funktion |
| `app/.../WatchWorkoutService.kt` | Telefon | nimmt das Workout entgegen |
| `app/.../WatchAck.kt` | Telefon | bestaetigt der Uhr den Eintrag |
| `app/.../PendingWorkouts.kt` | Telefon | Warteschlange der offenen Workouts |
| `app/.../WatchFacts.kt` | Telefon | die Zeile "32 min · 142 bpm · 3.240 steps" |
| `WatchProtocol.kt` | **beide** | Pfade und Feldnamen |

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

Der Rueckweg (`/crewfit/logged/...`, Abschnitt 8) laeuft genauso, nur
andersherum: dasselbe Verfahren, dieselben Regeln, die andere Richtung.

## 3. Beide Apps muessen dieselbe applicationId haben

`wear/build.gradle.kts` setzt `applicationId = "com.example.mobilese"` - denselben
Wert wie das Telefon-Modul, obwohl der `namespace` ein anderer ist. Die
Datenschicht stellt nur zwischen Apps mit gleicher Kennung und gleicher
Signatur zu. Mit einer eigenen Kennung fuer die Uhr kaeme nie etwas an, ohne
dass irgendwo ein Fehler auftauchte.

## 4. Pause und Fortsetzen

Die Zeit fuehrt eine eigene `Stopwatch` im `wear`-Modul. Sie bekommt die Zeit
von aussen gereicht (`SystemClock.elapsedRealtime`), statt sie selbst
abzulesen - nur so laesst sich das Anhalten und Fortsetzen pruefen, ohne
wirklich zu warten. Dieselbe Klasse gibt es im `app`-Modul fuer das gemeinsame
Training; wie bei `WatchProtocol` teilen die beiden APKs keinen Code.

Waehrend einer Pause werden **beide Sensoren abgemeldet**. Das ist nicht nur
eine Frage des Akkus: der Puls im Stehen zoege den Durchschnitt nach unten, und
der Weg zum Getraenkeautomat zaehlte als Trainingsschritte. Aus demselben Grund
steht in der Pause "Paused" statt eines Pulswerts - der letzte gemessene Wert
waere eine Behauptung ueber eine Messung, die gerade nicht stattfindet.

## 5. Schritte

`StepCounter` liest `Sensor.TYPE_STEP_COUNTER`. Der Sensor meldet nicht die
Schritte seit dem Anmelden, sondern die seit dem letzten Neustart des Geraets -
gebraucht wird also die Differenz. Jede Pause schliesst einen Abschnitt ab, und
beim Fortsetzen beginnt ein neuer; addiert werden nur die Abschnitte.

Der Sensor verlangt seit Android 10 die Erlaubnis `ACTIVITY_RECOGNITION`. Aus
dem Gangbild laesst sich ablesen, was jemand gerade tut, und das gilt als
schuetzenswert. Sie wird zusammen mit `BODY_SENSORS` in einem Zug erfragt: zwei
Dialoge nacheinander waeren auf einem Display in Muenzgroesse zwei zu viel.

Anders als beim Puls steht die Null hier fuer eine **richtige Messung** - beim
Yoga geht man keinen Schritt. "Nicht gemessen" ist deshalb `NO_STEPS = -1`, und
das Telefon macht daraus `null`.

Die Schritte werden auf dem Telefon **nur angezeigt**, nicht gespeichert: die
Tabelle `activities` hat keine Spalte dafuer, und in `step_days` haetten sie
nichts zu suchen - dort steht die Schrittzahl des Tages aus Health Connect, und
die Schritte des Workouts sind ein Teil davon. Sie zweimal zu zaehlen wuerde
Bonuspunkte verschenken, die es nicht gibt.

## 6. Ist das Telefon ueberhaupt da?

`PhoneReach` fragt vor dem Trainieren und nach dem Beenden nach den verbundenen
Geraeten und davon nach denen, die `isNearby` melden - nur zu diesen besteht
eine direkte Verbindung. Steht keines bereit, sagt die Uhr das: auf dem
Startbildschirm als Zeile "Phone out of reach", nach dem Stopp als "Saved -
shows up once your phone is back". Vorher stand dort immer "shows up on your
phone", was beim Laufen ohne Telefon in der Tasche schlicht falsch war.

**Gesperrt wird nichts.** Trainieren geht ohne Telefon genauso, das Workout
liegt dann eben und wartet - das ist ja der Sinn des `DataClient`.

Der genauere Weg waere der `CapabilityClient`: das Telefon meldet in
`res/values/wear.xml` eine Faehigkeit an, die Uhr fragt danach, und damit waere
auch ein gekoppeltes Telefon *ohne* CrewFit als abwesend erkannt. Der Weg wurde
gebaut und wieder verworfen: die Meldung erreicht die Uhr erst, nachdem die
Play-Dienste sie uebertragen haben. Auf dem Emulator-Paar kam die Anmeldung des
Telefons zwar an, blieb aber fuer `getCapability` unsichtbar - die Uhr nannte
ein danebenliegendes Telefon unerreichbar, waehrend das Workout gleichzeitig
ankam. Eine falsche Auskunft ist schlechter als eine ungenaue, und der Fall, den
der `CapabilityClient` zusaetzlich abdeckte, kommt kaum vor: die Uhr-App
installiert sich niemand ohne die auf dem Telefon.

## 7. Das Workout gehoert dem Dienst, nicht der Anzeige

Frueher lag alles in der Activity: Uhr, Sensoren, Zwischenstaende. Wer waehrend
des Laufens die Handflaeche aufs Display legte, zum Zifferblatt wischte oder
eine andere App oeffnete, verlor das Workout, sobald das System die Activity
abraeumte - ohne Meldung, ohne Rueckfrage, und ausgerechnet beim langen
Training, denn kurze ueberleben immer.

Jetzt traegt `WorkoutService` das Training. Er wird **gestartet und gebunden**:
gestartet, damit er die Activity ueberlebt, gebunden, damit die Activity ohne
Umweg an die Zahlen kommt, die sie jede Sekunde zeichnet. `WorkoutActivity` ist
nur noch die Anzeige davor und fragt im Sekundentakt ab - ein Rueckruf je
Pulsschlag waere ein Weckruf mehr, der nichts aendert, was man sehen koennte.

**Die Art `health`.** Ab Android 14 muss ein Vordergrunddienst sagen, wozu er
da ist, und `health` setzt voraus, dass mindestens eines der beiden
Sensorrechte erteilt ist. Wer beide ablehnt, bekommt deshalb keinen
Vordergrunddienst - das Training laeuft dann nur, solange die Activity lebt.
Das ist die ehrlichere Antwort als ein Dienst, der nichts misst: ohne Sensoren
ist die Uhr ein Wecker, und ein Wecker braucht keinen Dienst, der ihn
ueberlebt. Auf Uhren vor Android 14 wird die Art im Aufruf weggelassen - das
System kennt sie dort nicht und wiese den Dienst sonst ab. Im Manifest steht
seit dem 28. August `dataSync|health`: `health` gibt es erst ab Android 14, und
aeltere Systeme brauchen eine Art, die sie kennen.

**Und wenn auch der Dienst stirbt?** Dann greift `WorkoutStore`: Sportart,
Uhrstand, Puls und Schritte liegen in den SharedPreferences, in einzelnen
Werten statt als JSON, weil das wear-Modul kotlinx-serialization nicht
einbindet. Geschrieben wird bei jeder Aenderung und sonst alle 30 Sekunden -
die Uhrzeit muss nicht laufend gesichert werden, sie ergibt sich aus dem Beginn
der Runde. Android startet den Dienst ueber `START_STICKY` neu, er liest den
Stand und macht weiter. Ausprobiert mit `kill -9` auf den Prozess: das Training
lief danach weiter, als waere nichts gewesen.

Was ein Neustart der **Uhr** angeht, ist die Antwort eine andere: dabei setzen
sich sowohl `elapsedRealtime` als auch der Schrittzaehler zurueck, und ein
Workout aus der Zeit davor waere nicht mehr zu berechnen. `WorkoutStore` merkt
das daran, dass die Zeit hinter dem gesicherten Stand liegt, und verwirft es.

Auf dem Startbildschirm steht dann eine Zeile **"Back to Running · 3:21"**,
und die Sportarten darunter verschwinden, solange sie dasteht: ein zweites
Workout zu starten wuerde das erste stillschweigend wegwerfen.

## 8. Der Rueckweg: das Telefon bestaetigt

Bis hierher endete das Training auf der Uhr mit "abgegeben", und danach kam
nichts mehr. Ob daraus ein Eintrag in der Crew wurde oder ob es auf dem Telefon
noch auf Foto und Ort wartet, war von der Uhr aus nicht zu erkennen.

`WatchAck` auf dem Telefon legt deshalb einen Datensatz unter
`/crewfit/logged/<endedAt>` ab - **erst, wenn die Aktivitaet wirklich
gespeichert ist**. Eine Bestaetigung beim Oeffnen des Formulars waere leichter
zu haben und wertlos: wer abbricht, hat nichts eingetragen. Auf der Uhr nimmt
`PhoneAckService` sie entgegen, genauso von den Play-Diensten gestartet wie der
Dienst auf der Gegenseite, meldet sich einmal und raeumt den Datensatz weg.

Mitgeschickt werden Sportart, Dauer und Punkte. Die Punkte wie im Detail eines
Workouts gerechnet - ohne den Aufschlag fuer eine Serie und ohne den Faktor
fuer ein gemeinsames Training -, damit auf der Uhr dieselbe Zahl steht wie in
der App. Sind es null, stehen sie gar nicht da: ein Training unter zehn Minuten
ist in der Rangliste nichts wert, und "+0 points" liest sich wie ein Fehler
statt wie eine Regel.

Auf der Uhr bleibt **genau eine** Bestaetigung liegen (`LastLogged`), nicht
ein Verlauf: die Historie steht auf dem Telefon, hier geht es um die eine Frage
nach dem Training. Sie verschwindet, sobald das naechste Workout beginnt.

## 9. Auf einem Emulator ausprobieren

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

6. Einen **Schrittzaehler hat die Uhr im Emulator nicht** (`adb -s emulator-5556
   emu sensor status` listet ihn nicht auf). `StepCounter.isAvailable()` ist
   dort falsch, die Erlaubnis wird gar nicht erst erfragt, und das Workout geht
   ohne Schritte auf die Reise. Zu sehen ist die Zeile also nur auf einer
   richtigen Uhr.

## 10. Was auf dem Telefon passiert

`WatchWorkoutService` wird von den Play-Diensten gestartet, sobald ein Datensatz
ankommt - auch wenn die App geschlossen ist. Er laedt nichts hoch, sondern legt
das Workout in die Warteschlange (`PendingWorkouts`, in den SharedPreferences)
und zeigt eine Benachrichtigung. Auf dem Startbildschirm steht dann eine Karte
"Waiting from your watch"; beides fuehrt in das Formular, in dem Sportart und
Dauer schon eingetragen sind. Was die Uhr sonst noch gemessen hat, steht als
Zeile darueber - `WatchFacts` setzt sie aus den Angaben zusammen, die es gibt.
Fuer jede Kombination aus vorhandenem und fehlendem Puls und Schritten einen
eigenen Text zu pflegen waeren vier Texte je Stelle, und mit der naechsten
Messgroesse acht.

Der Puls von der Uhr hat Vorrang vor dem, was Health Connect fuer denselben
Zeitraum liefert: die Uhr hat waehrend des Workouts gemessen, alles andere ist
nachtraeglich zusammengesucht.

Ein Workout verschwindet erst dann aus der Warteschlange, wenn es wirklich
gespeichert ist. Bricht man das Formular ab oder scheitert der Upload, steht die
Karte beim naechsten Blick wieder da.

Wer die Uhr aus Versehen gestartet oder das Training abgebrochen hat, wird das
Workout ueber einen **langen Druck auf die Karte** wieder los. Es kommt eine
Rueckfrage, denn rueckgaengig geht das nicht: die Uhr hat den Datensatz nach dem
Uebertragen abgegeben.
