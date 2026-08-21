# Externe Bibliotheken

Aus dem aufgeloesten Abhaengigkeitsbaum erzeugt (`gradlew :app:dependencies`),
nicht von Hand geschrieben. Stand: siehe Git-Verlauf dieser Datei.

Als **extern** gilt hier alles, was nicht Teil des Android-SDK ist - also auch
AndroidX und Material. Nativ ist nur, was auf dem Geraet schon liegt
(`android.*`, `java.*`); alles Folgende wird mit dem APK ausgeliefert.

| | Anzahl |
| --- | --- |
| Selbst eingetragen (`app`) | 17 |
| Selbst eingetragen (`wear`) | 5 |
| Artefakte im Telefon-APK | 174 |
| Artefakte im Uhr-APK | 53 |
| Verschiedene Artefakte insgesamt | 175 |

`kotlin-stdlib` und `firebase-bom` sind in "selbst eingetragen" nicht
mitgezaehlt: die Standardbibliothek fuegt das Kotlin-Plugin von sich aus
hinzu, und das Firebase-BOM ist keine Bibliothek, sondern eine Liste
zusammenpassender Versionen ohne eigenen Code.

---

## 1. Die selbst eingetragenen Bibliotheken

| Bibliothek | Version | Modul | Wofuer | Zieht mit |
| --- | --- | --- | --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 | app + wear | Kotlin-Standardbibliothek | 1 |
| `androidx.activity:activity-ktx` | 1.8.0 | app | Activity Result API | 35 |
| `androidx.appcompat:appcompat` | 1.6.1 | app + wear | AppCompatActivity, Theming | 46 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | app + wear | lifecycleScope | 20 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | app + wear | Layouts | 48 |
| `androidx.swiperefreshlayout:swiperefreshlayout` | 1.1.0 | app | Pull-to-Refresh | 24 |
| `androidx.core:core-ktx` | 1.13.0 | app + wear | Kotlin-Erweiterungen | 24 |
| `com.google.android.material:material` | 1.12.0 | app | Material Design 3 | 61 |
| `io.github.jan-tennert.supabase:postgrest-kt` | 3.1.0 | app | Datenbankzugriff | 87 |
| `io.github.jan-tennert.supabase:auth-kt` | 3.1.0 | app | Anmeldung | 84 |
| `io.github.jan-tennert.supabase:storage-kt` | 3.1.0 | app | Dateien | 86 |
| `io.ktor:ktor-client-android` | 3.0.3 | app | HTTP-Motor | 33 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | app | JSON | 6 |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | app | QR-Code lesen | 1 |
| `com.google.zxing:core` | 3.5.3 | app | QR-Code erzeugen | 0 |
| `androidx.health.connect:connect-client` | 1.1.0 | app | Health Connect | 41 |
| `com.google.firebase:firebase-bom` | 33.7.0 | app | Versionsabgleich | 0 |
| `com.google.firebase:firebase-messaging` | 24.1.0 | app | Push-Benachrichtigungen | 66 |
| `com.google.android.gms:play-services-wearable` | 18.2.0 | app + wear | Verbindung zur Uhr | 40 |

"Zieht mit" = wie viele weitere Artefakte allein durch diesen Eintrag im APK
landen. Die Zahlen ueberschneiden sich stark - viele Bibliotheken teilen sich
dieselben Unterbauten.

---

## 2. Was jede Bibliothek mitbringt

### `org.jetbrains.kotlin:kotlin-stdlib` 2.2.10

Kotlin-Standardbibliothek. Nicht selbst eingetragen - das Kotlin-Plugin fuegt sie jedem Modul hinzu. Ohne sie laeuft kein Kotlin-Code.

1 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.activity:activity-ktx` 1.8.0

Activity Result API. Kamera, Bildwaehler und Berechtigungen. Loest das veraltete onActivityResult ab.

35 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.savedstate:savedstate-ktx` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.appcompat:appcompat` 1.6.1

AppCompatActivity, Theming. Grundlage aller Bildschirme; traegt das Material-Thema bis minSdk 26 zurueck.

46 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.appcompat:appcompat-resources` | 1.6.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 |
| `androidx.customview:customview` | 1.1.0 |
| `androidx.drawerlayout:drawerlayout` | 1.1.1 |
| `androidx.emoji2:emoji2` | 1.2.0 |
| `androidx.emoji2:emoji2-views-helper` | 1.2.0 |
| `androidx.fragment:fragment` | 1.3.6 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.viewpager:viewpager` | 1.0.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.lifecycle:lifecycle-runtime-ktx` 2.8.7

lifecycleScope. Bindet Netzarbeit an die Lebensdauer des Bildschirms.

20 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.constraintlayout:constraintlayout` 2.1.4

Layouts. Alle Bildschirme. Ein Layout bedient Telefon und Tablet.

48 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.appcompat:appcompat` | 1.6.1 |
| `androidx.appcompat:appcompat-resources` | 1.6.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.constraintlayout:constraintlayout-core` | 1.0.4 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 |
| `androidx.customview:customview` | 1.1.0 |
| `androidx.drawerlayout:drawerlayout` | 1.1.1 |
| `androidx.emoji2:emoji2` | 1.2.0 |
| `androidx.emoji2:emoji2-views-helper` | 1.2.0 |
| `androidx.fragment:fragment` | 1.3.6 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.viewpager:viewpager` | 1.0.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.swiperefreshlayout:swiperefreshlayout` 1.1.0

Pull-to-Refresh. Die Geste gibt es nicht im Framework.

24 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `androidx.core:core-ktx` 1.13.0

Kotlin-Erweiterungen. Kuerzere Schreibweise fuer Framework-Aufrufe.

24 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `com.google.android.material:material` 1.12.0

Material Design 3. Knoepfe, Textfelder, Dialoge, Navigationsleiste, Farbrollen.

61 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.appcompat:appcompat` | 1.6.1 |
| `androidx.appcompat:appcompat-resources` | 1.6.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.cardview:cardview` | 1.0.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 |
| `androidx.constraintlayout:constraintlayout-core` | 1.0.4 |
| `androidx.coordinatorlayout:coordinatorlayout` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 |
| `androidx.customview:customview` | 1.1.0 |
| `androidx.documentfile:documentfile` | 1.0.0 |
| `androidx.drawerlayout:drawerlayout` | 1.1.1 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 |
| `androidx.emoji2:emoji2` | 1.2.0 |
| `androidx.emoji2:emoji2-views-helper` | 1.2.0 |
| `androidx.fragment:fragment` | 1.3.6 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 |
| `androidx.print:print` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.recyclerview:recyclerview` | 1.1.0 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.transition:transition` | 1.5.0 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.viewpager2:viewpager2` | 1.0.0 |
| `androidx.viewpager:viewpager` | 1.0.0 |
| `com.google.errorprone:error_prone_annotations` | 2.26.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-bom` | 1.8.22 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `io.github.jan-tennert.supabase:postgrest-kt` 3.1.0

Datenbankzugriff. Alle Tabellen. Der offizielle Kotlin-Client fuer PostgREST.

87 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.browser:browser` | 1.8.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `co.touchlab:kermit` | 2.0.5 |
| `co.touchlab:kermit-android-debug` | 2.0.5 |
| `co.touchlab:kermit-core` | 2.0.5 |
| `co.touchlab:kermit-core-android-debug` | 2.0.5 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `com.russhwolf:multiplatform-settings` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg-android-debug` | 1.3.0 |
| `com.squareup.okio:okio` | 3.10.2 |
| `com.squareup.okio:okio-jvm` | 3.10.2 |
| `io.github.jan-tennert.supabase:auth-kt` | 3.1.0 |
| `io.github.jan-tennert.supabase:auth-kt-android-debug` | 3.1.0 |
| `io.github.jan-tennert.supabase:postgrest-kt-android-debug` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt-android-debug` | 3.1.0 |
| `io.ktor:ktor-client-content-negotiation` | 3.0.3 |
| `io.ktor:ktor-client-content-negotiation-jvm` | 3.0.3 |
| `io.ktor:ktor-client-core` | 3.0.3 |
| `io.ktor:ktor-client-core-jvm` | 3.0.3 |
| `io.ktor:ktor-events` | 3.0.3 |
| `io.ktor:ktor-events-jvm` | 3.0.3 |
| `io.ktor:ktor-http` | 3.0.3 |
| `io.ktor:ktor-http-jvm` | 3.0.3 |
| `io.ktor:ktor-io` | 3.0.3 |
| `io.ktor:ktor-io-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization` | 3.0.3 |
| `io.ktor:ktor-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-jvm` | 3.0.3 |
| `io.ktor:ktor-sse` | 3.0.3 |
| `io.ktor:ktor-sse-jvm` | 3.0.3 |
| `io.ktor:ktor-utils` | 3.0.3 |
| `io.ktor:ktor-utils-jvm` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-websockets` | 3.0.3 |
| `io.ktor:ktor-websockets-jvm` | 3.0.3 |
| `org.jetbrains.kotlin:kotlin-reflect` | 2.1.10 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:atomicfu` | 0.27.0 |
| `org.jetbrains.kotlinx:atomicfu-jvm` | 0.27.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.7.3 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.kotlincrypto:secure-random` | 0.3.2 |
| `org.kotlincrypto:secure-random-jvm` | 0.3.2 |
| `org.slf4j:slf4j-api` | 2.0.16 |

### `io.github.jan-tennert.supabase:auth-kt` 3.1.0

Anmeldung. Registrierung, Login, Passwort zuruecksetzen, Sitzungsverwaltung.

84 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.browser:browser` | 1.8.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `co.touchlab:kermit` | 2.0.5 |
| `co.touchlab:kermit-android-debug` | 2.0.5 |
| `co.touchlab:kermit-core` | 2.0.5 |
| `co.touchlab:kermit-core-android-debug` | 2.0.5 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `com.russhwolf:multiplatform-settings` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg-android-debug` | 1.3.0 |
| `com.squareup.okio:okio` | 3.10.2 |
| `com.squareup.okio:okio-jvm` | 3.10.2 |
| `io.github.jan-tennert.supabase:auth-kt-android-debug` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt-android-debug` | 3.1.0 |
| `io.ktor:ktor-client-content-negotiation` | 3.0.3 |
| `io.ktor:ktor-client-content-negotiation-jvm` | 3.0.3 |
| `io.ktor:ktor-client-core` | 3.0.3 |
| `io.ktor:ktor-client-core-jvm` | 3.0.3 |
| `io.ktor:ktor-events` | 3.0.3 |
| `io.ktor:ktor-events-jvm` | 3.0.3 |
| `io.ktor:ktor-http` | 3.0.3 |
| `io.ktor:ktor-http-jvm` | 3.0.3 |
| `io.ktor:ktor-io` | 3.0.3 |
| `io.ktor:ktor-io-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization` | 3.0.3 |
| `io.ktor:ktor-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-jvm` | 3.0.3 |
| `io.ktor:ktor-sse` | 3.0.3 |
| `io.ktor:ktor-sse-jvm` | 3.0.3 |
| `io.ktor:ktor-utils` | 3.0.3 |
| `io.ktor:ktor-utils-jvm` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-websockets` | 3.0.3 |
| `io.ktor:ktor-websockets-jvm` | 3.0.3 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:atomicfu` | 0.27.0 |
| `org.jetbrains.kotlinx:atomicfu-jvm` | 0.27.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.7.3 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.kotlincrypto:secure-random` | 0.3.2 |
| `org.kotlincrypto:secure-random-jvm` | 0.3.2 |
| `org.slf4j:slf4j-api` | 2.0.16 |

### `io.github.jan-tennert.supabase:storage-kt` 3.1.0

Dateien. Profil-, Crew- und Workout-Bilder, Sprachnotizen.

86 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.browser:browser` | 1.8.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-process` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `co.touchlab:kermit` | 2.0.5 |
| `co.touchlab:kermit-android-debug` | 2.0.5 |
| `co.touchlab:kermit-core` | 2.0.5 |
| `co.touchlab:kermit-core-android-debug` | 2.0.5 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `com.russhwolf:multiplatform-settings` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-coroutines-android-debug` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg` | 1.3.0 |
| `com.russhwolf:multiplatform-settings-no-arg-android-debug` | 1.3.0 |
| `com.squareup.okio:okio` | 3.10.2 |
| `com.squareup.okio:okio-jvm` | 3.10.2 |
| `io.github.jan-tennert.supabase:auth-kt` | 3.1.0 |
| `io.github.jan-tennert.supabase:auth-kt-android-debug` | 3.1.0 |
| `io.github.jan-tennert.supabase:storage-kt-android-debug` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt` | 3.1.0 |
| `io.github.jan-tennert.supabase:supabase-kt-android-debug` | 3.1.0 |
| `io.ktor:ktor-client-content-negotiation` | 3.0.3 |
| `io.ktor:ktor-client-content-negotiation-jvm` | 3.0.3 |
| `io.ktor:ktor-client-core` | 3.0.3 |
| `io.ktor:ktor-client-core-jvm` | 3.0.3 |
| `io.ktor:ktor-events` | 3.0.3 |
| `io.ktor:ktor-events-jvm` | 3.0.3 |
| `io.ktor:ktor-http` | 3.0.3 |
| `io.ktor:ktor-http-jvm` | 3.0.3 |
| `io.ktor:ktor-io` | 3.0.3 |
| `io.ktor:ktor-io-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization` | 3.0.3 |
| `io.ktor:ktor-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-json-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization-kotlinx-jvm` | 3.0.3 |
| `io.ktor:ktor-sse` | 3.0.3 |
| `io.ktor:ktor-sse-jvm` | 3.0.3 |
| `io.ktor:ktor-utils` | 3.0.3 |
| `io.ktor:ktor-utils-jvm` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-websockets` | 3.0.3 |
| `io.ktor:ktor-websockets-jvm` | 3.0.3 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:atomicfu` | 0.27.0 |
| `org.jetbrains.kotlinx:atomicfu-jvm` | 0.27.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-datetime-jvm` | 0.6.1 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.7.3 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.kotlincrypto:secure-random` | 0.3.2 |
| `org.kotlincrypto:secure-random-jvm` | 0.3.2 |
| `org.slf4j:slf4j-api` | 2.0.16 |

### `io.ktor:ktor-client-android` 3.0.3

HTTP-Motor. Liegt unter supabase-kt. Ausdruecklich eingetragen, damit kein zweiter Motor mitkommt.

33 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `io.ktor:ktor-client-android-jvm` | 3.0.3 |
| `io.ktor:ktor-client-core` | 3.0.3 |
| `io.ktor:ktor-client-core-jvm` | 3.0.3 |
| `io.ktor:ktor-events` | 3.0.3 |
| `io.ktor:ktor-events-jvm` | 3.0.3 |
| `io.ktor:ktor-http` | 3.0.3 |
| `io.ktor:ktor-http-jvm` | 3.0.3 |
| `io.ktor:ktor-io` | 3.0.3 |
| `io.ktor:ktor-io-jvm` | 3.0.3 |
| `io.ktor:ktor-serialization` | 3.0.3 |
| `io.ktor:ktor-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-sse` | 3.0.3 |
| `io.ktor:ktor-sse-jvm` | 3.0.3 |
| `io.ktor:ktor-utils` | 3.0.3 |
| `io.ktor:ktor-utils-jvm` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization` | 3.0.3 |
| `io.ktor:ktor-websocket-serialization-jvm` | 3.0.3 |
| `io.ktor:ktor-websockets` | 3.0.3 |
| `io.ktor:ktor-websockets-jvm` | 3.0.3 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm` | 0.5.4 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.slf4j:slf4j-api` | 2.0.16 |

### `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.7.3

JSON. Die Datenmodelle; ausserdem Uebergabe eines Workouts per Intent.

6 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.7.3 |
| `org.jetbrains:annotations` | 23.0.0 |

### `com.journeyapps:zxing-android-embedded` 4.3.0

QR-Code lesen. Crew per QR beitreten. Android hat keinen QR-Leser.

1 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `com.google.zxing:core` | 3.5.3 |

### `com.google.zxing:core` 3.5.3

QR-Code erzeugen. Der QR-Code der Crew. Android hat keinen QR-Erzeuger.

Bringt nichts weiter mit.

### `androidx.health.connect:connect-client` 1.1.0

Health Connect. Schrittzahl und Puls. Unvermeidlich - der Zugriff laeuft ueber einen Systemdienst.

41 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.health.connect:connect-client-external-protobuf` | 1.1.0 |
| `androidx.health.connect:connect-client-proto` | 1.1.0 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `com.google.code.findbugs:jsr305` | 3.0.2 |
| `com.google.errorprone:error_prone_annotations` | 2.26.0 |
| `com.google.guava:failureaccess` | 1.0.1 |
| `com.google.guava:guava` | 31.1-android |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `com.google.j2objc:j2objc-annotations` | 1.3 |
| `org.checkerframework:checker-qual` | 3.12.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-guava` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.jspecify:jspecify` | 1.0.0 |

### `com.google.firebase:firebase-bom` 33.7.0

Versionsabgleich. Keine Bibliothek, nur eine Liste zusammenpassender Firebase-Versionen. Enthaelt keinen Code.

Bringt nichts weiter mit.

### `com.google.firebase:firebase-messaging` 24.1.0

Push-Benachrichtigungen. Unvermeidlich fuer Push: eine Meldung an eine geschlossene App laeuft ueber den System-Kanal.

66 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.customview:customview` | 1.1.0 |
| `androidx.documentfile:documentfile` | 1.0.0 |
| `androidx.fragment:fragment` | 1.3.6 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 |
| `androidx.print:print` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.viewpager:viewpager` | 1.0.0 |
| `com.google.android.datatransport:transport-api` | 3.1.0 |
| `com.google.android.datatransport:transport-backend-cct` | 3.1.9 |
| `com.google.android.datatransport:transport-runtime` | 3.1.9 |
| `com.google.android.gms:play-services-base` | 18.5.0 |
| `com.google.android.gms:play-services-basement` | 18.4.0 |
| `com.google.android.gms:play-services-cloud-messaging` | 17.2.0 |
| `com.google.android.gms:play-services-stats` | 17.0.2 |
| `com.google.android.gms:play-services-tasks` | 18.2.0 |
| `com.google.errorprone:error_prone_annotations` | 2.26.0 |
| `com.google.firebase:firebase-annotations` | 16.2.0 |
| `com.google.firebase:firebase-common` | 21.0.0 |
| `com.google.firebase:firebase-common-ktx` | 21.0.0 |
| `com.google.firebase:firebase-components` | 18.0.0 |
| `com.google.firebase:firebase-datatransport` | 18.2.0 |
| `com.google.firebase:firebase-encoders` | 17.0.0 |
| `com.google.firebase:firebase-encoders-json` | 18.0.0 |
| `com.google.firebase:firebase-encoders-proto` | 16.0.0 |
| `com.google.firebase:firebase-iid-interop` | 17.1.0 |
| `com.google.firebase:firebase-installations` | 18.0.0 |
| `com.google.firebase:firebase-installations-interop` | 17.1.1 |
| `com.google.firebase:firebase-measurement-connector` | 19.0.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `javax.inject:javax.inject` | 1 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7` | 1.8.22 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.8.22 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

### `com.google.android.gms:play-services-wearable` 18.2.0

Verbindung zur Uhr. Unvermeidlich: Wear OS bietet keine andere Schnittstelle.

40 weitere Artefakte:

| Artefakt | Version |
| --- | --- |
| `androidx.activity:activity` | 1.8.0 |
| `androidx.annotation:annotation` | 1.8.1 |
| `androidx.annotation:annotation-experimental` | 1.4.0 |
| `androidx.annotation:annotation-jvm` | 1.8.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.collection:collection` | 1.1.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.13.0 |
| `androidx.core:core-ktx` | 1.13.0 |
| `androidx.customview:customview` | 1.1.0 |
| `androidx.fragment:fragment` | 1.3.6 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.8.7 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.7 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.7 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.7 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |
| `androidx.savedstate:savedstate` | 1.2.1 |
| `androidx.startup:startup-runtime` | 1.2.0 |
| `androidx.tracing:tracing` | 1.0.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.viewpager:viewpager` | 1.0.0 |
| `com.google.android.gms:play-services-base` | 18.5.0 |
| `com.google.android.gms:play-services-basement` | 18.4.0 |
| `com.google.android.gms:play-services-tasks` | 18.2.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.1 |
| `org.jetbrains:annotations` | 23.0.0 |

---

## 3. Das Uhr-Modul

Die Uhr traegt fuenf Bibliotheken ein, alle auch im Telefon-Modul. Ihr APK
bleibt mit 53 Artefakten deutlich kleiner - Supabase, Firebase, ZXing und
Health Connect fehlen dort, die Uhr spricht nur mit dem Telefon.

| Bibliothek | Version | Zieht mit |
| --- | --- | --- |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 | 1 |
| `androidx.core:core-ktx` | 1.10.1 | 25 |
| `androidx.appcompat:appcompat` | 1.6.1 | 45 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | 47 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.2 | 20 |
| `com.google.android.gms:play-services-wearable` | 18.2.0 | 39 |

