# Publishing KontrolCore Mobile on Google Play (KON-495)

Decided with the owner: **Google Play only, no APK downloads for customers** (the debug APK is for the owner's own phone). Nothing here has been run on a real phone yet; do a pass on one before any upload.
Play's rules change: check each "verify" line against the Play Console help before relying on it.

## 1. Make the upload key once (the owner; keep it and its password safe, and never commit them)
    keytool -genkeypair -v -keystore kontrolcore-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
Put the file outside the repository (or in `mobile/`, where `*.jks` is git-ignored) and make `mobile/keystore.properties` (also git-ignored):

    storeFile=/path/to/kontrolcore-upload.jks
    storePassword=...
    keyAlias=upload
    keyPassword=...

Use **Play App Signing** (the Console default for a new app): Google keeps the real signing key, this is only the upload key, and a lost upload key can be reset through Play support.

## 2. Build the bundle
    cd mobile
    ./gradlew :core:test :app:lintDebug
    ./gradlew :app:bundleRelease -PversionCode=2     # app/build/outputs/bundle/release/app-release.aab
Every upload needs a higher `versionCode` than the last (`-PversionCode=N`); `versionName` is in `app/build.gradle.kts`. Without `keystore.properties` or the `KC_KEYSTORE`,
`KC_KEYSTORE_PASSWORD`, `KC_KEY_ALIAS` (and optional `KC_KEY_PASSWORD`) environment variables the release build is **unsigned** and Play refuses it.
The application ID is `com.kontrolcore` (the code keeps the namespace `com.kontrolcore.mobile`, which is separate); **it can never change after the first upload**, so decide now that it is right.

## 3. Console set-up
- **Create app**: name KontrolCore (check it is free; Play titles are unique), default language, App (not game), **Free**. A free app cannot become paid later.
- **Closed testing first (verify):** a personal developer account created recently must run a closed test with a minimum number of testers (it was 12) for a minimum time (it was 14 days) before it may apply for production. An organisation account may be exempt. Plan two to three weeks. Testers join by a Google group or a list of emails and get a link.
- **Declarations** (App content):
  - *Privacy policy*: the website's `/privacy/` page (see 5, it needs the Mobile paragraph first).
  - *Data safety*: see 4.
  - *Foreground service*: the app runs one of type **special use** to keep the alert stream open. Subtype text (already in the manifest): "Keeps a connection to the person's own ntfy alert topic open so KontrolCore alerts arrive at once." Console asks for a short video and why a normal push or WorkManager job will not do: alerts come over a stream the person's own ntfy topic serves, and we run no push service of ours, so no Google push is available to wake the app (verify the form's current wording; special-use declarations are reviewed by hand).
  - *Permissions*: camera (scan the pairing QR code, nothing is stored), notifications, internet, boot completed (resume listening), biometric.
  - *Ads*: none. *Target audience*: 18 and over. *Content rating*: the questionnaire, no user-generated content, no violence. *App access*: needs a KontrolCore browser extension to pair with: give the reviewers a pairing code or a short note and a demo topic, otherwise review fails on "cannot sign in".
- **Store listing** needs: short description (80), full description (4000), icon 512 px (from `design/logo`, the same tile as the extension), a feature graphic 1024x500, at least 2 phone screenshots (the Robolectric ones in `mobile/app/build/screens` are a start; take real ones on a phone), and a support email.

## 4. Data safety answers (draft, to check against the final build)
The app has no server of ours and no analytics, ads or crash reporter. It holds on the phone: the topic name, the setup key (Keystore, encrypted, never shown again), and the last 300 alert messages (private app storage, not backed up).
- Data collected by *us*: **none**.
- Data sent off the phone: alerts are read from, and commands (a scenario name and a one-time code) are posted to, the **ntfy server the person chose** (the public ntfy.sh by default, or their own). That is a third party the person configures; declare it as data **shared** with a service provider if the form asks, purpose "app functionality", optional... verify Play's wording for user-directed transfers.
- Encrypted in transit: yes (https only, cleartext is off). Deletion: unpair in the app removes the key and the messages; there is no account.
- Security practices: the key never leaves the Keystore except as the one-time codes it makes; the app locks behind the phone's screen lock.

## 5. Documents that must say it before release
- `privacy.html` (source in this repo, synced to the website at the Team release, KON-471): add a paragraph for the Mobile app: no data to us; alerts and commands go to the person's ntfy server; what is kept on the phone. Not edited here because the legal pages are synced and published together (CLAUDE.md, "The legal pages live on the website").
- The extension's help and manual should say how to pair a phone (Alerts, Channels) and that the app is optional.
- A website product page (KON-496 area) and the listing text share one description; keep "ntfy is a third party" and never "no cloud".

## 6. Release steps
1. Internal testing track: upload the `.aab`, add the owner's email, install from the Play link on the phone, run the checklist below.
2. Closed testing (see 3), then apply for production in the Console, then a staged rollout (for example 20 percent for a few days).
3. Each update: raise `versionCode`, run `:core:test`, build, upload, add release notes.

### Phone checklist (nothing below has been done yet)
Pair by QR and by pasted link; refuse a link with a wrong version; pair with no screen lock (must refuse); inbox from a failing scenario; the Acknowledge and Snooze notification buttons; Run again and Stop with the one-time code; unlock timeout (60 s) then a command; Doze (leave overnight, then fail a scenario); battery "Unrestricted"; reboot (service restarts); airplane mode then back; Unpair; lost-phone drill (change the setup key in the extension, confirm the old phone's commands are refused).
