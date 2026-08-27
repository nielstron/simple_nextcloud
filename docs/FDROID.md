# F-Droid submission

The upstream store listing is in `fastlane/metadata/android/en-US`. F-Droid reads
the title, descriptions, changelog, icon, feature graphic, and screenshots from
there when it builds the app.

The file `fdroid/de.nielstron.simplenextcloud.yml` is a ready-to-copy build
recipe for the F-Droid Data repository. It targets the published `v1.0.3` tag by
its full commit hash, verifies the reproducible upstream APK, and enables
tag-based automatic updates. The tagged source contains all listing text and
graphics in its Fastlane metadata.

## Submit

1. Create a GitLab account and fork
   <https://gitlab.com/fdroid/fdroiddata>.
2. Create a non-protected branch named `de.nielstron.simplenextcloud` in the
   fork.
3. Copy `fdroid/de.nielstron.simplenextcloud.yml` to
   `metadata/de.nielstron.simplenextcloud.yml` in that branch.
4. Let the fork's CI pipeline validate and build the app. Fix every reported
   lint or build error before submitting.
5. Open a merge request from the branch to `fdroid/fdroiddata:master`, using the
   **App inclusion** merge-request template.

The merge request should state that the app author is submitting the app and
agrees to its inclusion. It should also mention that the app connects only to a
user-configured Nextcloud server and contains no known anti-features.

For every later release, increment both `versionCode` and `versionName`, commit
the change, and publish a tag named `v<versionName>` on that exact commit.
