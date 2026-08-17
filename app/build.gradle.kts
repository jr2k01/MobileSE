plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}