# KontrolCore Mobile (Android)

An Android companion to the free phone remote control of the KontrolCore browser extension (alerts and commands over an ntfy topic). It lived in the extension
repository until 2026-11-18. The design and the decisions are in that repository: `design/kontrol-mobile.md` (epic KON-488). Nothing here is part of the extension package.

## Layout
- `core/` plain Kotlin on the JVM, no Android SDK needed. The protocol the app speaks to the extension: base32, one-time codes (RFC 6238 TOTP, SHA-1, 6 digits, 30 s),
  the phone-command grammar (`RUN Checkout #482913`), the pairing link, tickets (`ACK <name> ~<expiry>.<mac>`), reading alerts into a Needs-you list, the ntfy client and
  the command sender (never two commands in one 30-second step, never a code about to end).
- `app/` the Android app: pairing by QR (`kontrolcore://pair?...`) or pasted link, the setup key in the Android Keystore behind a fingerprint, face or screen lock
  (`Vault`, `Gate`), a foreground service holding one stream to the ntfy topic (`ListenService`), notifications with the extension's Acknowledge and Snooze buttons
  (`Notifier`, `ActionReceiver`; only a ticket POST to the paired topic is ever sent), the Needs-you inbox with failure detail and Share (`MainActivity`, `DetailActivity`),
  and Run, Stop, Acknowledge, Snooze and Status with a fresh one-time code (`Commands`). Application ID `com.kontrolcore`, Android 11 or newer, no Google Play services.
- `store/` the Google Play listing text, the 9:16 phone screenshots and the icon and feature graphic. `PLAY.md` is the release checklist.

## The one rule
**The app agrees with the extension line by line.** `core/src/test/resources/vectors.json` is made by the extension's own code, never by the Kotlin side. Do not edit it.
When the extension's codes, tickets, pairing link or command grammar change, regenerate it in the **extension repository** and copy it here:

    node tools/mobile-vectors.js --app <path to this checkout>     # run in the extension repository
    ./gradlew :core:test                                           # here: the app's code against the new vectors

## Build and test
Needs a JDK 17 or newer and network access to Maven Central and Google's Maven (Android Studio brings both the JDK and the SDK). Set `ANDROID_HOME` or copy
`local.properties.example` to `local.properties`.

    ./gradlew :core:test              # the protocol, against the extension's vectors
    ./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew :app:lintDebug
    ./gradlew :app:bundleRelease -PversionCode=2   # the signed bundle for Google Play (see PLAY.md)

`app/src/test/.../ScreenshotTest.kt` draws the real layouts to PNGs on the JVM (Robolectric, `app/build/screens`). It needs the Robolectric Android jars on disk: set
`ROBOLECTRIC_DIR` to a folder holding `android-all-instrumented-14-robolectric-10818077-i7.jar` and the 15 one, or let it download them.

## Not done yet
Checked on a real phone (nothing here has run on a device: only compiled, linted and tested on the JVM), the widget and quick-settings tile, pause and resume, the
state card and sealed messages (deferred in the design), and the Play listing steps in `PLAY.md`. The Run again button on a notification opens the app (it needs a real code).
