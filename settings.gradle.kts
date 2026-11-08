// KontrolCore Mobile (Android). Not part of the extension package (tools/package-release.js is an allowlist and never lists mobile/).
// :core is plain Kotlin on the JVM (the protocol: one-time codes, the phone-command grammar, later the ntfy client and the sealed
// messages), tested here with no Android SDK. :app (the Android app) is added beside it when the toolchain is set up.
rootProject.name = "kontrolcore-mobile"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(":core")
