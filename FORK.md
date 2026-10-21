# Fork and distribution notes

## Behavior

The video-call self-view remembers its last selected corner across calls and app
restarts. The preference is local to the installation, applies to all contacts,
and adds no settings toggle. An absent or unknown value uses the existing
bottom-right default. Screen resizing continues to use the existing corner
layout logic.

The implementation is in `MoveableLocalVideoRenderer.kt` and
`MiscellaneousValues.kt`. `LocalVideoPositionTest` covers dragging, layout
resizing, focus changes, new compositions, all four saved corners, storage cache
reloads, and unknown stored values.

## Build and test

Use the Java version in `.tool-versions` and the SDK components specified in
`reproducible-builds/Dockerfile`. The Gradle application module is named
`:Signal-Android`, although its directory is `app`.

Build the debug app and instrumentation APK together:

```sh
mise install java
mise exec -- ./gradlew \
  :Signal-Android:assemblePlayProdDebug \
  :Signal-Android:assemblePlayProdDebugAndroidTest \
  --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx4g \
  -Pkotlin.compiler.execution.strategy=in-process
```

Including an AndroidTest task enables the upstream `.test_run` application ID
suffix. These APKs are for isolated testing, not distribution releases.
The instrumentation class is
`org.thoughtcrime.securesms.components.webrtc.v2.LocalVideoPositionTest`.
Its UI tests require an awake, unlocked device. The test screen renders a minimal
layout rather than a camera feed; it does not establish a live call.

## Publishing APKs to a custom F-Droid repository

This repository supplies the source. It does not configure a release signing
key or an APK publishing workflow: the release key and the repository index live
outside this repository. Publishing the source alone does not make an
installable F-Droid release.

The `github` distribution flavor sets the permanent application ID
`net.hilson.signal`. Other flavors keep upstream's `org.thoughtcrime.securesms`
ID. A differently signed APK cannot update an official Signal installation, and
the distinct ID keeps the fork a separate app: it is registered on its own, and
moving data from the official app (for example with a local backup) is a
separate step. Do not change this ID after the first distributed release.

Before each distributed release:

1. Use the permanent release signing key and keep its credentials outside Git.
   APK updates must retain the application ID and signing identity, with
   increasing Android version codes.
2. Use the `githubProdRelease` variant for repository distribution. It disables
   Signal's website APK updater. Build with
   `mise exec -- ./gradlew :Signal-Android:assembleGithubProdRelease`, then sign
   and verify the release APK through the release pipeline. This variant still
   requires release validation; the debug instrumentation build is not a release
   qualification.
3. Publish signed APKs as versioned release assets with their source commit/tag
   and SHA-256 checksums. The F-Droid repository can ingest these APKs, generate
   its signed index, and serve them over HTTPS. Its index signing key is separate
   from the app signing key.
4. Verify a fresh install, a real video call, corner persistence after restarting
   the app, and an in-place update from the preceding fork release before
   distributing an update.

Retain the upstream license and source availability information with releases.
Keep the fork current with upstream fixes; this change does not replace ongoing
upstream maintenance.
