# ScrollStop

A small Android app in the spirit of *one sec*: when you open an app you've chosen to guard
(Instagram, TikTok, YouTube, …), ScrollStop shows a short breathing pause first and asks
whether you really want to go in. Often the answer is no.

## What it does

- **Breathing pause** before every guarded app (3–20 s, your choice).
- **Gets longer each time**: optionally +2 s for every repeat open of the same app that day (capped at 30 s).
- **Honest counter**: "You've tried to open Instagram 7 times today."
- **Time-boxed access**: if you do go in, you pick 1, 5 or 15 minutes.
- **Time's up**: when that time runs out while you're still in the app, ScrollStop steps in again.
- **Daily stats**: pauses today and how many times you walked away.

Everything stays on the phone. There is no network permission, account or tracking.

## How it works

| Piece | File |
|---|---|
| Detects which app is in the foreground (window events only; it never reads screen content) | `service/BlockerAccessibilityService.kt` |
| Pause, choice and time's-up screens (Jetpack Compose) | `ui/InterceptActivity.kt` |
| Setup screen: permission, stats, settings, app picker | `ui/MainActivity.kt` |
| Pure decision logic (unit tested) | `data/PauseRules.kt` |
| Persisted settings, allowances and daily counters | `data/BlockerStore.kt` |

## Build

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleRelease       # → app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it installs directly for personal use.
Use your own signing config before distributing it.

## Install and set up

1. Copy `app-release.apk` to your phone and open it (allow installs from that source when asked).
2. Open ScrollStop and tap **Open Accessibility settings** → *ScrollStop pause* → turn it on.
   - On Android 13+ the switch may be greyed out for sideloaded apps. Go to
     **Settings → Apps → ScrollStop → ⋮ → Allow restricted settings**, then try again.
3. Switch on the apps you want to guard.
4. Optional: some phones (Xiaomi, Samsung, Huawei…) kill background services aggressively.
   If the pause stops appearing, set ScrollStop's battery usage to **Unrestricted**.

## Ideas for later

- Daily hard limits ("max 3 opens of TikTok per day")
- Schedules (stronger pauses during work hours or after 23:00)
- Home-screen widget with today's stats
- Guarding specific websites in the browser
