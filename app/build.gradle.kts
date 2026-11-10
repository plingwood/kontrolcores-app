import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// Release signing comes from outside the repository: mobile/keystore.properties (git-ignored) or KC_* environment variables. With neither, the release build is unsigned.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.kontrolcore.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kontrolcore.mobile"
        // Android 11: the Keystore can hold a key that needs a fresh unlock every 30 seconds (setUserAuthenticationParameters), which is how the setup key stays behind the lock.
        minSdk = 30
        targetSdk = 35
        // Play wants a higher versionCode for every upload: pass -PversionCode=N (or edit here).
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
    }

    signingConfigs {
        val file = signing("storeFile", "KC_KEYSTORE")
        if (file != null) create("upload") {
            storeFile = rootProject.file(file).let { if (it.exists()) it else File(file) }
            storePassword = signing("storePassword", "KC_KEYSTORE_PASSWORD")
            keyAlias = signing("keyAlias", "KC_KEY_ALIAS")
            keyPassword = signing("keyPassword", "KC_KEY_PASSWORD") ?: signing("storePassword", "KC_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = false }
    testOptions { unitTests { isIncludeAndroidResources = true; all { t -> System.getenv("ROBOLECTRIC_DIR")?.let { d -> t.systemProperty("robolectric.offline", "true"); t.systemProperty("robolectric.dependency.dir", d) } } } }
    lint { abortOnError = true; warningsAsErrors = false }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    // QR scanning without Google Play services (the design keeps Google out as a third party): zxing's own camera screen.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
