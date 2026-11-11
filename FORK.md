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

## Publishing releases

Releases are GitHub releases on this repository, one signed APK per release, and
users install and update them with [Obtainium](https://github.com/ImranR98/Obtainium).
The release signing key is not in this repository.

The `github` distribution flavor sets the permanent application ID
`net.hilson.signal`. Other flavors keep upstream's `org.thoughtcrime.securesms`
ID. A differently signed APK cannot update an official Signal installation, and
the distinct ID keeps the fork a separate app: it is registered on its own, and
moving data from the official app (for example with a local backup) is a
separate step. Do not change this ID after the first distributed release.

Every update must keep the application ID and the signing key, and have a higher
version code. The release signing certificate has SHA-256
`ba3b113748921b8f8635e2666590a62f9ab50b138e413a8180fdf145f03fc6f7`.

### Tags

Release tags are `hilson-v<versionName>`, for example `hilson-v8.32.2`. The
prefix keeps them apart from the upstream `v<version>` tags this fork carries.
Build from a clean checkout of the commit you will tag (HEAD at that commit):
the build records `git rev-parse HEAD` as the app's `GIT_HASH`, and the clean
tree makes the code match it.

Obtainium reads the version from the tag, so the tag must contain one `x.y.z`
version equal to the APK's `versionName`. There is no fork-only release path: a
rebuild of the same upstream version keeps the same `versionName` and version
code, and its tag already exists. Fork fixes ship with the next upstream version.

### Steps

1. Build the `githubProdRelease` variant, which disables Signal's website APK
   updater. Upstream configures no signing key for it, so the APKs are unsigned.

   ```sh
   mise exec -- ./gradlew :Signal-Android:assembleGithubProdRelease \
     --max-workers=2 \
     -Dorg.gradle.jvmargs=-Xmx4g \
     -Pkotlin.compiler.execution.strategy=in-process
   ```

2. Check, align and sign the arm64-v8a APK with the release key (a PKCS12
   keystore, alias `signal`, password in `SIGNAL_KEY_PASS`), with `$BT` set to a
   `build-tools` directory of version 35 or later (older `zipalign` lacks `-P`):

   ```sh
   IN=app/build/outputs/apk/githubProd/release/Signal-Android-github-prod-arm64-v8a-release-unsigned-<versionName>.apk
   OUT=net.hilson.signal-<versionName>-arm64-v8a.apk
   $BT/aapt2 dump badging "$IN" | head -1   # net.hilson.signal, versionCode, versionName
   $BT/zipalign -c -P 16 4 "$IN"            # if it fails: zipalign -P 16 -f 4 "$IN" aligned.apk, then IN=aligned.apk
   $BT/apksigner sign --ks <keystore>.p12 --ks-key-alias signal \
     --ks-pass env:SIGNAL_KEY_PASS --out "$OUT" "$IN"
   $BT/apksigner verify --print-certs "$OUT"
   ```

   The certificate SHA-256 must match the one above.

3. Install the signed APK on a device before publishing. Check a fresh install,
   a real video call, corner persistence after restarting the app, and an
   in-place update from the preceding fork release. Once a release is published,
   Obtainium users get it, and the debug instrumentation build is not a release
   qualification.

4. Tag the source commit and publish the release:

   ```sh
   git tag -a hilson-v<versionName> <commit> \
     -m "Signal fork <versionName> (versionCode <versionCode>), application ID net.hilson.signal"
   git push origin refs/tags/hilson-v<versionName>
   gh release create hilson-v<versionName> --verify-tag --latest \
     --title "Signal fork <versionName>" --notes-file notes.md "$OUT"
   ```

   Attach exactly one APK. In the notes, give the version, version code,
   application ID and ABI, the source tag and commit, the APK's SHA-256 and the
   signing certificate SHA-256. Say that the app installs beside official
   Signal, and that Signal-Android is licensed under the AGPLv3.

5. Download the asset into an empty directory
   (`gh release download hilson-v<versionName> --dir dl/`). Check that its
   SHA-256 matches `$OUT` and that `apksigner verify` shows the release
   certificate.

### Installing with Obtainium

Add the app with the source URL `https://github.com/abumalick/Signal-Android`
and set **Trim version string with RegEx** to `\d+\.\d+\.\d+` (leave the match
group empty). Without it, Obtainium can compare the whole tag (`hilson-v8.32.2`)
with the version Android reports (`8.32.2`) and show an update that does not
exist, for example when the app was installed outside Obtainium. Leave
**Reconcile version string with version detected from OS** on.

Retain the upstream license and source availability information with releases.
Keep the fork current with upstream fixes; this change does not replace ongoing
upstream maintenance.
