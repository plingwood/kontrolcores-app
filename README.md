# KontrolCore Mobile (Android)

An Android companion to the free phone remote control (alerts and commands over an ntfy topic). Design and decisions: `design/kontrol-mobile.md`; epic KON-488.
Nothing here is part of the extension package (`tools/package-release.js` is an allowlist and never lists `mobile/`; `checkMobileVectors` checks it).

## Layout
- `core/` plain Kotlin on the JVM, no Android SDK needed. The protocol the app speaks to the extension: base32, one-time codes (RFC 6238 TOTP, SHA-1, 6 digits, 30 s),
  the phone-command grammar (`RUN Checkout #482913`), the pairing link, tickets (`ACK <name> ~<expiry>.<mac>`), reading alerts into a Needs-you list, the ntfy client and
  the command sender (never two commands in one 30-second step, never a code about to end). Later: the state card, the sealed messages.
- `app/` the Android app (KON-493 onward): pairing by QR (`kontrolcore://pair?...`) or pasted link, the setup key in the Android Keystore behind a fingerprint, face or screen lock
  (`Vault`, `Gate`), a foreground service holding one stream to the ntfy topic (`ListenService`), notifications with the extension's Acknowledge and Snooze buttons
  (`Notifier`, `ActionReceiver`; only a ticket POST to the paired topic is ever sent), the Needs-you inbox with failure detail and Share (`MainActivity`, `DetailActivity`),
  and Run, Stop, Acknowledge, Snooze and Status with a fresh one-time code (`Commands`, using `core`'s `CommandSender`). Android 11 or newer. No Google Play services.
- `tools/make-vectors.js` writes `core/src/test/resources/vectors.json` **from the extension's own code** (`totp.js`, `alert-rules.js`).

## The one rule
**The app agrees with the extension line by line.** The numbers the Kotlin tests check are made by the extension, never by the Kotlin side. Change the extension's
codes or command grammar and the check in the main suite fails until you regenerate the vectors and the Kotlin tests pass again:

    node mobile/tools/make-vectors.js      # regenerate from the extension
    cd mobile && ./gradlew :core:test      # the app's code against them

## Build and test
Needs a JDK (17 or newer) and network access to Maven Central and Google's Maven. `./gradlew :core:test` downloads Gradle and the Kotlin plugin the first time.
The `core` module compiles to Java 17 bytecode so the Android module can use it.

The app needs the Android SDK (platform 35, build-tools 35): set `ANDROID_HOME` or copy `local.properties.example` to `local.properties`.

    cd mobile
    ./gradlew :core:test              # the protocol, against the extension's vectors
    ./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew :app:lintDebug :app:assembleRelease

The release build is unsigned (the signing key and Google Play are the owner's, KON-495).

## Not done yet
Checked on a real phone (nothing here has run on a device: only compiled, linted and tested on the JVM), the widget and quick-settings tile, pause and resume, the
state card and sealed messages (deferred in the design), the Play listing, and an "own ntfy server" login. Notification buttons for Run again open the app (they need a real code).
