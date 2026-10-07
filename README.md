# Workflow-App

Home of **Atlas**, an all in one Android app for notes, tracking, planning and quality of life. The full plan lives in the "Atlas: Android App Plan" doc.

## What is in it (0.3.0)

- **Home**: greeting with your name, now and next (classes and events), upcoming countdowns, today's agenda, quick links to every section, one tap tracker shortcuts with undo, hold to select and remove shortcuts, pinned notes and a quick add button
- **Notes**: search, #tags, pinning, colours, autosave, list, grid or compact layouts, sorting, hold to select, share or copy
- **Calendar**: month, week and agenda views, events with colours, locations and repeats (yearly ones too), plus tasks, repeating tasks, classes, birthdays and tracker logs on each day
- **Timetable**: weekly grid, subjects with colours, teachers and rooms, week A and B, term dates, now and next
- **Tasks**: quick add that understands "tomorrow", "friday" and "!!", due dates, priorities, repeats and subjects, swipe to finish or delete
- **Countdowns**: birthdays, anniversaries, holidays and plain countdowns or count ups, with ages and milestones
- **Sheets**: simple spreadsheets with formulas like `=SUM(A1:A5)`, `=AVERAGE`, `=IF` and cell maths
- **Reminders**: a morning nudge for birthdays and tasks due today, and a heads up before timed events, each switchable
- **Search**: one search across notes, tasks, events, countdowns, trackers and sheets
- **Track**: counter, yes or no, timer, number and rating trackers with goals, streaks, charts and history
- **Focus**: pomodoro timer that can log finished sessions to a tracker
- **Tools**: dice, coin, random numbers and picker, odds converter, streak odds, percentages, tip splitter, unit converter, date maths, tally counter and stopwatch
- **Explorer**: one folder tree for notes, trackers, sheets and folders, with list or grid view and a trash
- **Settings**: about 90 options across 15 categories with search, 17 built in themes, a theme creator with a full colour picker, page and tab transitions, five app icons, backups and starter packs

A fresh install starts empty and offers starter packs (study, fitness, wellbeing, habits, work, money) instead of built in shortcuts.

## Getting the APK

Every push is built by GitHub Actions (the **Build APK** workflow).

- From `main`: open the repo's **Releases** page on your phone, pick **Atlas latest build** and tap `atlas.apk`.
- From any other branch: open the workflow run under **Actions** and download the `atlas-apk` artifact (it comes zipped).

Allow "Install unknown apps" for your browser or file manager the first time. All builds are signed with the same key in `keystore/`, so new builds install over the old one and keep your data.

## Building locally

Needs JDK 17 and the Android SDK (Android Studio sets both up).

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## Stack

Kotlin, Jetpack Compose with Material 3, Room for the database, DataStore for settings, kotlinx.serialization for backups. Minimum Android 8.0.
