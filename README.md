<div align="center">
  <img src="docs/app-icon.svg" width="72" height="72" alt="Simple Nextcloud app icon">
</div>

<p align="center"><strong>Simple Nextcloud</strong></p>

A deliberately small Android Nextcloud client for minimal usability:

- Navigate the Nextcloud file system.
- Simple image viewer to quickly swipe through galleries.
- Upload and download files or folders.
- Move, copy, rename, or create files and folders.
- Share files with other users or public links.
- Register in Android as a share-target and as a file system to save files to or pick files from.

Most importantly for me, it distinguishes itself from the official Nextcloud client by _not storing all uploaded files in a duplicated, hidden cache_.

## Screenshots

<div align="center">
  <img src="docs/screenshots/login.png" width="200" alt="Simple Nextcloud login screen">
  <img src="docs/screenshots/file-actions.png" width="200" alt="File browser with file actions menu">
  <img src="docs/screenshots/sorting.png" width="200" alt="File browser sorting menu">
  <img src="docs/screenshots/sharing.png" width="200" alt="Folder sharing screen">
</div>

## Installation

Download and install the APK from the [Releases page](https://github.com/nielstron/simple_nextcloud/releases). You may have to click through a few scary screens that ask you whether you are installing malware (you are not).

## Build

```sh
./gradlew test assembleDebug
```

Open the project in Android Studio or install `app/build/outputs/apk/debug/app-debug.apk` on a device running Android 8 or newer.
