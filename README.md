# WearMusicPlayer

Music is an offline audio player for Wear OS. It plays audio stored on the watch and includes optional controls for Soundcore R50i NC (A3959) earbuds.

## Music v1.1

- New music-first home, a dedicated earbuds screen, and a black/lime visual theme.
- Round-screen Now Playing separates artwork, two-line title, artist, playback controls, an interior seek rail, and navigation.
- The crown opens the native Wear OS media volume panel instead of replacing the timestamp with a number.
- Playback options include repeat off/all/one and a 15/30/60-minute sleep timer with cancellation. Repeat is saved locally; the sleep timer applies to the current playback session and requires a selected track.
- Version name 1.1, version code 6.

## Earlier v1.0 behavior

- Tracks without embedded artwork use a record-like thumbnail.
- Search covers track titles, artists, and albums. The watch crown controls volume on Now Playing and scrolls the Library.
- Android media controls and compatible earbud controls can play, pause, and skip tracks through a media session.
- The app and its in-app controls are in English. Track metadata and any system dialogs use their original text.

## Playback and performance audit

| Finding | Change |
| --- | --- |
| Opening player screens started an idle service. | The screens bind while visible. Choosing a track starts the foreground service. Active playback can outlive the app screen or task. |
| Audio focus could resume playback after a manual pause. | A manual pause abandons focus and cancels automatic resume. Transient interruption resumes only when there is still a playback request. |
| The progress timers and custom text layout ran during pause. | Timers run only during visible playback. The Now Playing controls use Android views, and the time label changes once per displayed second. |
| Search could launch overlapping queries. | A single worker runs debounced and cancellable MediaStore searches. |
| Artwork could add decoding work while rendering. | A single background worker samples embedded covers to a maximum edge of 384 pixels and discards stale results. |
| Seeking repeatedly republished metadata and notification content. | The session refreshes metadata when tracks change and reports position after a completed seek. |

The player keeps a partial wake lock only while the platform MediaPlayer plays audio. Pausing clears audio focus. After 10 minutes paused, the service releases the decoder, clears the queue, and stops. Task removal stops an idle or paused session. Android force stop or process termination ends playback. The app does not persist or automatically restart its queue.

File opening and decoder setup still include a platform call on the main thread before asynchronous preparation begins. Startup latency, battery drain, Bluetooth behavior, and playback during actual screen-off and idle states require runtime measurements.

## Build and verification

Package: `com.illustricious.wearmusic`. Minimum Android API 30. Target API 35.

On Windows, set `JAVA_HOME` to JDK 17 or newer and `ANDROID_HOME` to an SDK with Android 35 and Build Tools 36.0.0. From this directory run:

```powershell
./build-minimal.ps1
```

The signed APK is created at `app/build/outputs/apk/debug/app-debug.apk`. Public release signing should use a dedicated release key. The sideload build uses the existing development certificate so it can update earlier APKs signed with that certificate. The private keystore is excluded from the source package.

This revision passes the bundled build and APK signature verification. Device observations and limits are recorded in DEVICE-TEST.md. This is a development-signed sideload build.

## License

GPL-3.0-or-later. The Soundcore protocol implementation uses [OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) as its protocol reference.
