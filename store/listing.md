# Google Play listing text (KontrolCore Mobile)

Play limits: short description 80 characters, full description 4000. Checked by `node -e` in the session that wrote this; recount if you edit.

## Short description
See what failed in KontrolCore and act on it from your phone in one tap.

## Full description
KontrolCore watches your websites for you: it runs your checks in the browser and tells you when something breaks. KontrolCore Mobile puts those alerts and a few simple controls on your phone, so you can see what needs you and deal with it without going back to your desk.

WHAT IT DOES
• A "Needs you" inbox: every scenario or suite that is failing, needs a look or has gone quiet, with the step that failed, its error text and the page address.
• Colour that means something: red is failing, amber needs a look, blue has gone quiet, grey is acknowledged. A scenario drops off the list when it recovers.
• Acknowledge and Snooze right from the notification.
• Run again, Stop, Acknowledge, Snooze and a status check from the app. Nothing to type: the app adds the one-time code to every command for you.
• Share a failure summary with your team from Android's share sheet.
• Light and dark themes.

HOW IT WORKS
KontrolCore Mobile is a companion to the KontrolCore browser extension (Chrome and Edge) on your computer. In the extension, open Alerts, Channels and choose Pair a phone, then scan the code with this app. That is the whole set-up. There is no account and no sign-up.

Alerts and commands travel over ntfy, a simple notification service. By default that is the public ntfy.sh server; you can use your own ntfy server instead. ntfy is a third party that you choose: KontrolCore does not run a server for this and has no access to your messages.

Your computer reads commands about once a minute, so an answer is not instant. The computer must be on and the browser running for commands to be carried out. A command that waited more than five minutes is refused and not run later. Starting a run from the phone needs KontrolCore Pro in the extension; seeing alerts, Acknowledge, Snooze and Stop are free.

PRIVATE BY DESIGN
• No account, no ads, no analytics and no tracking.
• The key that makes the one-time codes is kept in the Android Keystore, encrypted, and is never shown again. The app opens behind your fingerprint, face or screen lock, and the key can be used only for a minute after you unlock. A phone with no screen lock cannot be paired.
• Alerts and commands use https only. Alert text is kept on your phone, not backed up, and removed when you unpair.
• Lost your phone? Change the setup key in KontrolCore and the old phone can no longer send commands.

KontrolCore Mobile is free and does not use up one of your KontrolCore device seats. Android 11 or newer.
