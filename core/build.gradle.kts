import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.kontrolcore.mobile"
version = "0.1.0"

// Java 17 bytecode, which is what an Android module can use; any newer JDK builds it.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    // Reads the conformance vectors that tools/make-vectors.js writes from the extension's own code.
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
