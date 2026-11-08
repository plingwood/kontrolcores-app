# KontrolCore Mobile (Android)

An Android companion to the free phone remote control (alerts and commands over an ntfy topic). Design and decisions: `design/kontrol-mobile.md`; epic KON-488.
Nothing here is part of the extension package (`tools/package-release.js` is an allowlist and never lists `mobile/`; `checkMobileVectors` checks it).

## Layout
- `core/` plain Kotlin on the JVM, no Android SDK needed. The protocol the app speaks to the extension: base32, one-time codes (RFC 6238 TOTP, SHA-1, 6 digits, 30 s),
  the phone-command grammar (`RUN Checkout #482913`). Later: the ntfy client, the state card, the sealed messages.
- `app/` (not yet) the Android app: pairing by QR, Keystore and biometric lock, notifications with actions, the inbox, the widget.
- `tools/make-vectors.js` writes `core/src/test/resources/vectors.json` **from the extension's own code** (`totp.js`, `alert-rules.js`).

## The one rule
**The app agrees with the extension line by line.** The numbers the Kotlin tests check are made by the extension, never by the Kotlin side. Change the extension's
codes or command grammar and the check in the main suite fails until you regenerate the vectors and the Kotlin tests pass again:

    node mobile/tools/make-vectors.js      # regenerate from the extension
    cd mobile && ./gradlew :core:test      # the app's code against them

## Build and test
Needs a JDK (17 or newer) and network access to Maven Central. `./gradlew :core:test` downloads Gradle and the Kotlin plugin the first time.
The `core` module compiles to Java 17 bytecode so an Android module can use it.

## Not done yet
The Android app module (needs the Android SDK), the ntfy client, QR pairing, sealed messages, and the decisions in KON-489 that they wait on.
