# OpenClipFlow

OpenClipFlow is an Android-first, landscape-oriented editing-studio foundation built with Kotlin and Jetpack Compose. The project includes a dark tablet-friendly editor UI, media-bin browser, multitrack timeline, clip selection, drag scrubbing, split/duplicate/delete/copy/paste actions, per-clip effect toggles, inspector metadata, document-picker import, and an export-settings dialog.

## Build

- Android Studio with JDK 17 and Android SDK platform 35 is recommended.
- Open this directory in Android Studio and allow Gradle sync, or run `gradle assembleDebug` if Gradle is installed.
- Minimum Android version: Android 8.0 (API 26). The interface is optimized for landscape/tablet use.

GitHub Actions builds a release APK on pushes and uploads it to workflow artifacts. Push a version tag such as `v0.1.0` to attach the APK to a GitHub Release.

## Current implementation boundaries

This repository is a working **editor UI and interaction prototype**, not yet a production media-rendering engine. Timeline items currently use the in-memory sample project; the document picker imports asset metadata/URIs into the media bin. Preview frames, real multi-clip sequencing, frame-accurate cutting, persistent project serialization, undo/redo history, actual 4K transcoding, and SMB/NFS mounting still require media-engine, storage, and network-service implementations. The 4K selector is an export-settings demonstration; it does not encode video. Camera EXIF values shown in the inspector are representative UI fields, not extracted metadata. NAS appears as a project-library location but does not establish a connection.

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