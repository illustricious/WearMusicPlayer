# Device check: Music v1.1

Date: 2026-11-19 (Asia/Jakarta). Xiaomi Watch 2 Pro, round 466 x 466 display, wireless ADB. Development-signed sideload APK, versionName 1.1 / versionCode 6.

## Observations during redesign testing

- APK compilation, packaging and signature verification pass. Installation with adb install -r succeeds without clearing app data.
- Cold MainActivity launches: 568, 359, 348 ms (median 359 ms) using am force-stop followed by am start -W. An earlier run measured 308 ms and the initial post-install launch 957 ms. These are a few launch samples, not a benchmark against v1.0. Small UI and option-persistence fixes followed these samples. The final installed APK then measured 537 ms on another cold launch.
- The library read 40 local audio tracks and selecting a track started Android MediaPlayer and a media session.
- The native Wear OS Media volume panel was observed on device. The custom numeric volume overlay has been removed.
- Now Playing was visually inspected, including album art and active playback. Two iterations increased spacing between metadata, controls, timestamp, and bottom navigation.
- Screen-off test: samples at 15 and 30 seconds reported Dozing, media session PLAYING, and AudioPlaybackConfiguration state:started. At 45 seconds the display was Awake and playback remained started. This establishes short playback continuity in dozing, not long-duration deep-idle or audible dropout verification.
- Observed total PSS: 19,128 to 27,347 KB during playback. One screen-off top sample showed process CPU 0.0%; this is an instantaneous sample, not sustained CPU usage.
- Rendering across initial navigation: 161 frames, 6 janky (3.73%), p50 5 ms, p95 14 ms. A separate small transition sample after reset: 33 frames, 6 janky (18.18%), p50 6 ms, p95 89 ms. Transition stalls remain; steady animation frame rate was not benchmarked.
- Repeat selection and a 15-minute timer label updated on device. Idle-service teardown exposed a repeat persistence issue; final code saves repeat in preferences and rejects starting a timer without a selected track.
- No application crash was observed in the logs checked. An older unrelated system crash existed in the device crash buffer.

## Limits

Full 15/30/60-minute timer expiration, automatic repeat at end of track, long-term battery drain, Bluetooth reconnection and hardware earbud commands are not yet measured in this revision. The brief device check is not an exhaustive regression suite. The final APK was rebuilt and reinstalled after the spacing, persistence, and timer guard fixes.
