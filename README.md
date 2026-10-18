# ScrollStop

A small Android app in the spirit of *one sec*, focused on the worst doom-scroll traps:
**Instagram Reels** and **YouTube Shorts**. When you start one, ScrollStop shows a short breathing
pause and asks whether you really want to scroll. Say no and it backs you out of the feed,
and you keep using the rest of the app (DMs, search, subscriptions) as normal.

It can also guard whole apps (TikTok, Reddit, …) with the same pause.

## What it does

- **Reels & Shorts guard** (on by default): pauses only the short-video feed, not the whole app.
  Choosing "No" presses Back for you until you're out of the feed. The video behind the pause
  screen is paused while you decide.
- **Breathing pause** before every guarded app (3–20 s, your choice).
- **Gets longer each time**: optionally +2 s for every repeat open of the same app that day (capped at 30 s).
- **Honest counter**: "You've tried to open Instagram 7 times today."
- **Time-boxed access**: if you do go in, you pick 1, 5 or 15 minutes.
- **Time's up**: when that time runs out while you're still in the app, ScrollStop steps in again.
- **Daily stats**: pauses today and how many times you walked away.

Everything stays on the phone. There is no network permission, account or tracking.

### How Reels/Shorts detection works

Inside Instagram and YouTube, ScrollStop checks the screen's layout for the full-screen Reels or
Shorts player by its view ID (for example `clips_viewer_view_pager` and `reel_recycler`). It
doesn't read any text. If an app update renames those views, the pause stops appearing for that
feed. Add the new ID to `ShortFormFeatures.kt` and rebuild. You can find it with Android Studio's
Layout Inspector, or with `adb shell uiautomator dump`.

## How it works

| Piece | File |
|---|---|
| Detects the foreground app, and the Reels/Shorts player inside Instagram/YouTube | `service/BlockerAccessibilityService.kt` |
| Which view IDs mark the Reels and Shorts players | `data/ShortFormFeatures.kt` |
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

Without a signing key, release builds use the debug key, which is fine for your own phone.
To publish builds for others, set up your own key and use GitHub releases (see
[SECURITY.md](SECURITY.md)).

## Safety

ScrollStop has **no internet permission and no other permissions**. It never reads text on
screen, and its only action is pressing Back. The build fails if that ever changes. See
[SECURITY.md](SECURITY.md) for the full list and how to check a downloaded APK.

## Install and set up

1. Install the APK. Play Protect blocks any sideloaded app that asks for Accessibility,
   however safe it is ("This app can request access to sensitive data…"). Either:
   - **Recommended:** install from a computer with `adb install ScrollStop.apk`
     (turn on Developer options → USB debugging first), or
   - briefly turn off Play Store → profile → Play Protect → ⚙ → *Scan apps with Play Protect*,
     install, then turn it back on.
2. Open ScrollStop and tap **Open Accessibility settings** → *ScrollStop pause* → turn it on.
   - On Android 13+ the switch may be greyed out for sideloaded apps. Go to
     **Settings → Apps → ScrollStop → ⋮ → Allow restricted settings**, then try again.
3. Reels and Shorts are guarded straight away. Optionally switch on whole apps to guard too.
   - Updating from an older version? Turn the accessibility service off and on once, so
     Android picks up the new permission to look for the Reels/Shorts player.
4. Optional: some phones (Xiaomi, Samsung, Huawei…) kill background services aggressively.
   If the pause stops appearing, set ScrollStop's battery usage to **Unrestricted**.

## Ideas for later

- More feeds: Facebook Reels, Snapchat Spotlight, YouTube's Shorts shelf on the home screen
- Daily hard limits ("max 3 opens of TikTok per day")
- Schedules (stronger pauses during work hours or after 23:00)
- Home-screen widget with today's stats
- Guarding specific websites in the browser
