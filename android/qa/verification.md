# Android validation — 13 November 2026

- Debug APK, Android test APK, release AAB and Android lint compile successfully.
- Real Android 16.1 emulator, package com.nathandev.localagent.debug.
- Twelve isolated interoperability checks pass against the real Desktop NetworkService and SQLite Store: square QR decode; expired invitation, incompatible version, public endpoint and incorrect certificate rejected; HTTPS pairing; encrypted trust reload; decline without data transfer; bidirectional conversation import/export; second sync has zero changes and conflicts; local trust removal.
- Desktop result: two accepted merges; Android test message received; final merge added=0, updated=0, ignored=4, conflicts=0.
- Agent selector anchored below top capsule, six agents, no Settings/Tools footer. Top code shortcut and overflow removed.
- Light/dark screens inspected visually. English message “Hello, how are you?” sent by keyboard Enter and answered in English. Existing user chats preserved.
- Earlier layout checks covered 320, 360, 412 dp, landscape, font scaling and keyboard insets. The subsequent changes affect colors, branding and pairing screens; pairing content scrolls.
- Final English demo: 60 seconds, H.264 720x1280, AAC narration, recorded emulator interaction with still views of the pairing screen and logo. Playback on the emulator confirmed. Windows Microsoft Zira narrates the demonstration; screen recording does not itself capture Android speech audio.

Not validated on a physical camera or iOS simulator. Android ML inference is not implemented by this UI port. Google Play submission and YouTube publication must be checked separately from successful local builds.
