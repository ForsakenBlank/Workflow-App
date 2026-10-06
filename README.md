# Workflow-App

Home of **Atlas**, an all in one Android app for notes, tracking, planning and quality of life. The full plan lives in the "Atlas: Android App Plan" doc.

## What works so far (phase 1)

- **Home**: today card plus one tap tracker shortcuts, with an undo snackbar after each log
- **Notes**: search, #tags, pinning, colours, autosave, word count
- **Track**: counter, yes or no, and timer trackers with daily goals, streaks, a 14 day chart and full history
- **Explorer**: one folder tree for notes, trackers and folders, with breadcrumbs, colours, move, rename and a 30 day trash
- **Settings**: system, light or dark theme, wallpaper colours, pure black, and backup or restore to a JSON file
- Calendar is a placeholder until phase 2

First launch comes with the starter trackers from the plan (Cold shower, Gym, Study) and a welcome note.

## Getting the APK

Every push is built by GitHub Actions (the **Build APK** workflow).

- From `main`: open the repo's **Releases** page on your phone, pick **Atlas latest build** and tap `atlas.apk`.
- From any other branch: open the workflow run under **Actions** and download the `atlas-apk` artifact (it comes zipped).

Allow "Install unknown apps" for your browser or file manager the first time. All builds are signed with the same key in `keystore/`, so new builds install over the old one and keep your data.

## Building locally

Needs JDK 17 and the Android SDK (Android Studio sets both up).

```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## Stack

Kotlin, Jetpack Compose with Material 3, Room for the database, DataStore for settings, kotlinx.serialization for backups. Minimum Android 8.0.
