plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * Firebase nur, wenn es eingerichtet ist.
 *
 * Das google-services-Plugin bricht den Build ab, sobald die
 * google-services.json fehlt. Fest angewendet liesse sich das Projekt also
 * ohne ein eigenes Firebase-Projekt gar nicht mehr bauen - auf einem frisch
 * geklonten Rechner, im Kurs, ueberall.
 *
 * Deshalb wird es nur angewendet, wenn die Datei tatsaechlich daliegt. Ohne
 * sie baut und laeuft die App wie bisher, nur ohne Push; PushTokens faengt
 * genau diesen Fall ab.
 */
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.mobilese"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.mobilese"
        // Angehoben von 24 auf 26: Health Connect setzt Android 8.0 voraus.
        // Aeltere Geraete waeren sonst gar nicht mehr baubar, weil die
        // Bibliothek ihre eigene Untergrenze mitbringt.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    // Fuer BuildConfig.VERSION_NAME, das die Einstellungen unter "About"
    // anzeigen. Seit AGP 8 wird BuildConfig nur noch auf Verlangen erzeugt.
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/*
 * Abhaengigkeiten bewusst knapp gehalten.
 *
 * Entfernt wurden:
 *  - Coil: Bilder werden von ImageLoader.kt geladen, rund 200 Zeilen ueber
 *    HttpURLConnection, BitmapFactory und LruCache aus dem Android-SDK.
 *  - supabase-realtime: war eingebunden, aber kein Bildschirm hoerte auf
 *    Live-Updates. Das Modul hielt nur eine WebSocket-Verbindung offen.
 *  - ktor-client-core und ktor-client-serialization: kommen bereits
 *    transitiv ueber Supabase herein. Direkt gebraucht wird nur die
 *    Android-Engine.
 *
 * Geblieben sind Supabase als Backend-Anbindung und ZXing fuer die QR-Codes -
 * eine Kamera-Erkennung von Hand zu schreiben waere nicht sinnvoll.
 */
dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.constraintlayout)
    // Zum Herunterziehen und Aktualisieren. Es gibt dafuer nichts im
    // Framework - die Geste lebt seit jeher in der Support-Bibliothek.
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Backend
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    // QR-Codes
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)

    // Schrittzahl aus Health Connect, der Gesundheitsdatenbank von Android.
    // Ohne diese Bibliothek ist Health Connect nicht ansprechbar: der Zugriff
    // laeuft ueber einen Systemdienst, nicht ueber eine offene Schnittstelle.
    implementation(libs.health.connect.client)

    // Push-Benachrichtigungen. Die Abhaengigkeit selbst laesst sich immer
    // uebersetzen; erst zur Laufzeit braucht sie eine Konfiguration.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Die Verbindung zur Uhr. Sie ist der einzige Weg, auf dem Uhr und Telefon
    // unter Android miteinander sprechen - eine eigene Loesung ueber Bluetooth
    // gibt es dafuer nicht.
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
