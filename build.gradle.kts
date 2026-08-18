// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Nur bereitgestellt, angewendet wird es im app-Modul und auch dort nur,
    // wenn eine google-services.json vorliegt - siehe app/build.gradle.kts.
    alias(libs.plugins.google.services) apply false
}