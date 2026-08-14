<div align="center">
  <img src="docs/app-icon.svg" width="72" height="72" alt="Simple Nextcloud app icon">
</div>

<p align="center"><strong>Simple Nextcloud</strong></p>

A deliberately small Android Nextcloud client for minimal usability:

- Navigate the Nextcloud file system.
- Upload and download files or folders.
- Move, copy, rename, or create files and folders.
- Share files with other users or public links.
- Register in Android as a share-target and as a file system to save files to or pick files from.

## Build

```sh
./gradlew test assembleDebug
```

Open the project in Android Studio or install `app/build/outputs/apk/debug/app-debug.apk` on a device running Android 8 or newer.
