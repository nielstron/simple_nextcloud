# Good Nextcloud

A deliberately small Android Nextcloud client for three workflows:

- Navigate folders and search the current folder.
- Upload through Android's system document picker and download through the system save dialog.
- Share with another Nextcloud user or create a public link.

Folder listings are kept briefly in a bounded, memory-only cache so back navigation and repeat visits are immediate. As soon as a folder opens, the app prefetches metadata for up to two likely next folders based on recent and frequent visits. Opening the active target adopts its in-flight request; navigating elsewhere cancels it. The app never caches or prefetches file contents.

The app uses Nextcloud WebDAV for files and the OCS Share API for sharing. It only accepts HTTPS server addresses and stores the username and app password encrypted with Android Keystore.

## Build

```sh
./gradlew test assembleDebug
```

Open the project in Android Studio or install `app/build/outputs/apk/debug/app-debug.apk` on a device running Android 8 or newer.

## Sign in

Create a dedicated app password in **Nextcloud → Personal settings → Security**, then enter the server base URL, username and app password. The server may be installed at the domain root or below a path.
