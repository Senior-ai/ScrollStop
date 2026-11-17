# Security and privacy

ScrollStop uses Android's Accessibility API, the same API scam apps abuse to read banking
screens. That's why Play Protect is wary of it. This page sets out exactly what ScrollStop does
with that access, how the code is kept to that, and how to check that an APK is genuine.

## What the app can and cannot do

| | |
|---|---|
| **Network** | None. The app has no `INTERNET` permission, and the manifest strips it even if a library tries to add it. Nothing can leave the phone. |
| **Other permissions** | None. No contacts, SMS, calls, storage, camera, microphone or location. |
| **Screen content** | It never reads text. In every app it only learns *which app* is open. Inside Instagram and YouTube it also asks whether a view with a known Reels/Shorts player ID is visible, and gets a yes or no. |
| **Actions** | Only after you choose "No" on Reels/Shorts, to get you out of the feed, and in this order until you're out: press **Back**; tap the app's own **Home tab** (Instagram's, found by its view ID); go to the phone's **home screen**. It never types, never taps anything else, and does nothing on its own initiative. |
| **Drawing on screen** | The Reels/Shorts pause is drawn on top of Instagram/YouTube as an accessibility overlay, which only the enabled accessibility service can show (no "display over other apps" permission). It disappears when you choose, press Back, or leave the app. |
| **Quiz decks** | Imported by you from pasted text, a file you pick (Android's file picker, no storage permission), or Share. Kept in app-private storage with the rest of the data. |
| **Stored data** | Your settings, plus per-app counters that reset daily. Only on the phone; excluded from cloud backup and device transfer. |
| **Exposed to other apps** | The launcher screen, and the import screen as a Share target for text and text files (quiz decks). Shared content only fills a preview; nothing is saved until you tap Import. The service and the pause screens can't be started by other apps. |

You can confirm this in the code. All accessibility handling lives in
`app/src/main/java/com/senior/scrollstop/service/BlockerAccessibilityService.kt`.

## Guard rails in the build

- `./gradlew checkPermissions` (part of `./gradlew check`) fails the build if the release
  manifest gains any permission or exported component that isn't on a short allowlist.
- The Gradle wrapper download is pinned by SHA-256, and CI verifies the wrapper jar.
- Dependencies are pinned to exact versions.
- Release APKs are built and signed by GitHub Actions from the public source on `main`, never
  on a personal machine.

## Your signing key

An Android app's signing key is its identity. Android only installs an update over an
existing app if both are signed with the same key, so whoever holds the key controls updates.

ScrollStop's key is `scrollstop-release.jks` (alias `scrollstop`; fingerprint below). Keep it
and its password backed up somewhere safe, such as a password manager. **Never commit it.**
`.gitignore` already excludes `*.jks` and `keystore.properties`.

If the key is ever lost or leaked, make a new one as below. Everyone then has to uninstall
once, because Android won't update an app across keys.

```bash
keytool -genkeypair -v -keystore scrollstop-release.jks -alias scrollstop \
  -keyalg RSA -keysize 4096 -validity 10000
```

Local builds: create `keystore.properties` in the project root:

```properties
storeFile=scrollstop-release.jks
storePassword=…
keyAlias=scrollstop
keyPassword=…
```

GitHub releases: add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `SCROLLSTOP_KEYSTORE_BASE64` | output of `base64 -w0 scrollstop-release.jks` |
| `SCROLLSTOP_KEYSTORE_PASSWORD` | keystore password |
| `SCROLLSTOP_KEY_ALIAS` | `scrollstop` |
| `SCROLLSTOP_KEY_PASSWORD` | key password |

Then publish a release by raising `versionName` (and `versionCode`) in `app/build.gradle.kts`
and pushing to `main`. Actions notices the new version, signs the APK with this key and
publishes it as release `v<versionName>`. Without the secrets it still builds and tests, and
warns that it skipped the release.

Builds without a key fall back to the debug key so you can still test on your own phone.
Don't share those builds.

## Checking an APK before installing

Only install ScrollStop from this repository's **Releases** page (the download link in the
README points there). Every release is signed with the same certificate:

```
SHA-256: 6E:DF:1C:C5:8F:78:C3:39:65:73:83:69:3C:53:13:E3:0B:26:0B:B0:64:F9:5D:2E:58:C1:47:FD:A7:D8:E2:D3
```

Each release includes:

- `ScrollStop.apk.sha256`: compare it with `sha256sum ScrollStop.apk` (or `certutil -hashfile ScrollStop.apk SHA256` on Windows).
- `signing-certificate.txt`: the certificate fingerprint. It must be the same in every release.
  You can check an APK with `apksigner verify --print-certs ScrollStop.apk`.

If either one doesn't match, don't install it.

## Reporting a problem

Open a GitHub issue, or contact the repository owner privately for anything sensitive.
