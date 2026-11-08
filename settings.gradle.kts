// KontrolCore Mobile (Android). Not part of the extension package (tools/package-release.js is an allowlist and never lists mobile/).
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
