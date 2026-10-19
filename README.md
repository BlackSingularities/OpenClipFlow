# OpenClipFlow

OpenClipFlow is an Android-first, landscape-oriented editing-studio prototype built with Kotlin and Jetpack Compose. It includes a dark tablet-friendly editor, searchable media bin, folder navigation, multitrack timeline clips with independent timeline instances, drag-to-reposition, playhead snapping, split, trim, duplicate, multi-select, copy/paste, per-clip effect toggles, inspector controls, persistent URI imports, local project autosave, JSON project open/save-as, simulated playback transport, and an export-preset dialog.

## Build

- Android Studio with JDK 17 and Android SDK platform 35 is recommended.
- Open this directory in Android Studio and allow Gradle sync, or run `gradle assembleDebug` if Gradle is installed.
- Minimum Android version: Android 8.0 (API 26). The interface is optimized for landscape/tablet use.

GitHub Actions builds a release APK on pushes and uploads it to workflow artifacts. Push a version tag such as `v0.1.0` to attach the APK to a GitHub Release.

## Current implementation boundaries

This repository remains an **editor interaction prototype**, not a production media-rendering engine. The timeline, project persistence and edit history are implemented for the current in-memory model, but imported sources are not yet decoded into real preview frames, and playback is a timeline transport simulation. Clip trimming and splitting edit timeline metadata only; there is no frame-accurate media engine. Project JSON stores URI strings, which can become inaccessible if Android's document provider revokes access. Export presets do not encode video. Advanced effect slider controls are UI-only. Camera metadata is not extracted. NAS appears in the media library but does not establish an SMB connection. These features need a real codec/rendering, metadata, and network-storage implementation.

Automatic installation of APK updates is subject to Android package signatures, installation permissions, and user consent. The release workflow publishes APKs, but an in-app updater and signed update verification must be implemented before claiming unattended self-updates.

## Publish as your public GitHub repository

To publish under the authenticated account, create a **public empty repository** named `OpenClipFlow` on GitHub if it does not already exist, then run in this directory:

```bash
git init -b main
git add .
git commit -m "Build OpenClipFlow Android editor foundation"
git remote add origin https://github.com/YOUR_ACCOUNT/OpenClipFlow.git
git push -u origin main
```

Create releases by pushing a tag, for example `git tag v0.1.0 && git push origin v0.1.0`.