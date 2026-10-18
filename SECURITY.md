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
| **Actions** | Its only action is pressing **Back**, to leave Reels/Shorts after you choose "No". It never taps, types or performs gestures. |
| **Stored data** | Your settings, plus per-app counters that reset daily. Only on the phone; excluded from cloud backup and device transfer. |
| **Exposed to other apps** | Only the launcher screen. The service and the pause screen can't be started by other apps. |

You can confirm this in the code. All accessibility handling lives in
`app/src/main/java/com/senior/scrollstop/service/BlockerAccessibilityService.kt`.

## Guard rails in the build

- `./gradlew checkPermissions` (part of `./gradlew check`) fails the build if the release
  manifest gains any permission or exported component that isn't on a short allowlist.
- The Gradle wrapper download is pinned by SHA-256, and CI verifies the wrapper jar.
- Dependencies are pinned to exact versions.
- Release APKs are built by GitHub Actions from the tagged source, not on a personal machine.

## Your signing key

An Android app's signing key is its identity. Android only installs an update over an
existing app if both are signed with the same key, so whoever holds the key controls updates.

Create one once, on your own computer, and back it up somewhere safe (e.g. a password manager).
**Never commit it.** `.gitignore` already excludes `*.jks` and `keystore.properties`.

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

Then publish a release by pushing a tag: `git tag v1.1 && git push origin v1.1`.

Builds without a key fall back to the debug key so you can still test on your own phone.
Don't share those builds.

## Checking an APK before installing

Only install ScrollStop from this repository's **Releases** page. Each release includes:

- `ScrollStop.apk.sha256`: compare it with `sha256sum ScrollStop.apk` (or `certutil -hashfile ScrollStop.apk SHA256` on Windows).
- `signing-certificate.txt`: the certificate fingerprint. It must be the same in every release.
  You can check an APK with `apksigner verify --print-certs ScrollStop.apk`.

If either one doesn't match, don't install it.

## Reporting a problem

Open a GitHub issue, or contact the repository owner privately for anything sensitive.
