plugins {
    alias(libs.plugins.android.application)
}

/*
 * Die App fuer die Uhr.
 *
 * Eigenes Modul mit eigenem APK: Wear OS ist ein eigenes Geraet, kein zweiter
 * Bildschirm des Telefons. Beide tragen dieselbe applicationId - das verlangt
 * die Datenverbindung zwischen Uhr und Telefon, und nur so findet die eine App
 * die andere.
 */
android {
    namespace = "com.example.mobilese.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mobilese"
        // Wear OS 3 setzt Android 11 voraus. Aeltere Uhren laufen auf einer
        // ganz anderen Grundlage und werden von Google selbst nicht mehr
        // bedient.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.lifecycle.runtime.ktx)

    // Die Verbindung zum Telefon. Dafuer gibt es unter Android keine andere
    // Schnittstelle; der Datenaustausch zwischen Uhr und Telefon laeuft
    // ausschliesslich hierueber.
    implementation(libs.play.services.wearable)
}
