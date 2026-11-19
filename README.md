# Music for Wear OS

Music is an offline audio player for Wear OS. It plays audio stored on the watch and includes optional controls for Soundcore R50i NC (A3959) earbuds.

## Music v1.0

- Now Playing uses a subdued album cover behind the controls and a circular seek bar. Tracks without embedded artwork use a record-like background.
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

The PC checks cover APK packaging and signature, plus 16 playback and metadata smoke scenarios with Android test doubles. Android runtime, screen-off audio, rotary input, artwork rendering, and Bluetooth controls have yet to be verified on a Wear OS device or emulator.

## License

GPL-3.0-or-later. The Soundcore protocol implementation uses [OpenSCQ30](https://github.com/Oppzippy/OpenSCQ30) as its protocol reference.
