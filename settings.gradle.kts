// KontrolCore Mobile (Android), its own repository (moved out of the KontrolCore extension repository 2026-11-18).
// :core is plain Kotlin on the JVM (the protocol: one-time codes, the phone-command grammar, pairing, tickets, the inbox, the ntfy client), tested
// with no Android SDK. :app is the Android app and needs the SDK (ANDROID_HOME or local.properties sdk.dir).
rootProject.name = "kontrolcore-mobile"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":core")
include(":app")
