# Nocturne

A local Android music player. It downloads audio from YouTube links (single videos or whole playlists) with
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), with no API key and no backend, and plays everything **offline**.
The UI is in Romanian.

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3), Manrope font (bundled) |
| Playback | AndroidX Media3 1.5: ExoPlayer + `MediaLibraryService` + `MediaController` |
| Extraction | NewPipeExtractor (JitPack) |
| Downloads | OkHttp range requests, Coroutines, WorkManager (foreground `dataSync`) |
| Storage | Room (tracks, playlists, downloads); files in `/Android/data/<pkg>/files/music` + `/thumbs` |
| DI / arch | Hilt, MVVM with `StateFlow` |
| SDK | minSdk 26, targetSdk 35 |

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35). Gradle 8.13 is fetched by the wrapper.

```bash
# point Gradle at your SDK (or open the project once in Android Studio)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk   (id: com.nocturne.player.debug)
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk (R8-minified)
./gradlew testDebugUnitTest    # unit tests + a live NewPipe smoke test (skipped when offline)
./gradlew lintDebug
```

On Windows use `gradlew.bat`. Install the APK with `adb install -r app/build/outputs/apk/release/app-release.apk`.

### APK signing

Without a keystore, `assembleRelease` signs with the **debug key**, so the APK still installs. To ship with your own key:

1. Create a keystore once:
   ```bash
   keytool -genkeypair -v -keystore nocturne-release.jks -alias nocturne \
           -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Create `keystore.properties` in the project root. It is git-ignored, and so are `*.jks` files.
   ```properties
   storeFile=nocturne-release.jks
   storePassword=********
   keyAlias=nocturne
   keyPassword=********
   ```
3. `./gradlew assembleRelease`, then check the signature with
   `$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`.

Keep the keystore safe. Updates must be signed with the same key.

### Updating NewPipeExtractor

YouTube changes often, and when extraction breaks the fix is usually a newer extractor. The version lives in **one line**
of `gradle/libs.versions.toml`:

```toml
newpipeExtractor = "v0.27.1"
```

Change it to the latest [release tag](https://github.com/TeamNewPipe/NewPipeExtractor/releases) and rebuild.
The version is injected into `BuildConfig.NEWPIPE_EXTRACTOR_VERSION` and shown in **Setări → Despre**.
Then run `./gradlew testDebugUnitTest`. `NewPipeLiveTest` resolves a real video and playlist, picks the m4a stream and
downloads a byte range, so it tells you whether the new version works.

## How it works

- **Add from link**: paste into the Library link bar, use *Share → Nocturne* from the YouTube app, or open a YouTube link with Nocturne.
  When the app gains focus, a new YouTube link in the clipboard is offered once via a snackbar.
  Supported: `youtube.com/watch?v=`, `youtu.be/`, `music.youtube.com`, `/shorts/`, `/live/` and `list=` playlists
  (all pages are fetched). Radio mixes (`list=RD…`) are treated as the single video.
- **Downloads**: best audio-only stream, m4a/AAC first (no FFmpeg needed), then webm/opus. Up to 4 tracks in parallel,
  each fetched with sequential 4 MB HTTP range requests. Downloads can resume, and each track gets 3 attempts.
  Tracks already in the library or in the queue are skipped. Each finished track stores its audio file, its thumbnail,
  and title/artist (channel)/duration in Room. An imported YouTube playlist becomes a local playlist marked *Din YouTube*;
  importing it again merges into the same local playlist.
- **Playback** lives in `PlaybackService` (Media3 `MediaLibraryService`, `foregroundServiceType="mediaPlayback"`), not in the Activity.
  - ExoPlayer: audio focus (pauses for calls, ducks for notifications), pauses on headphone unplug, `WAKE_MODE_LOCAL`.
    Auto-advance is gapless where the container allows it.
  - The notification is a MediaStyle mini-player (`NocturneNotificationProvider`): artwork, title, artist,
    shuffle · previous · play/pause · next · repeat, a seek bar (Android 10+), colorized with the gradient tone.
    Tapping it opens Now Playing. It is ongoing while playing and can be dismissed only when paused.
  - The lock screen and Quick Settings media carousel (Android 13+) come from the MediaSession, including the custom shuffle/repeat buttons.
  - Queue, index, position, shuffle and repeat are saved continuously and restored on the next launch. The service also
    implements `onPlaybackResumption`, so the shade's media card or a Bluetooth play button resumes playback after the process was killed.
  - Swiping the app away from recents keeps playback going. The service only stops when nothing is playing.
- **Battery**: on first launch the app asks for notification permission (Android 13+) and then explains and requests the
  battery-optimization exemption. *Setări* links to the system battery page and, on Xiaomi/Redmi/POCO, to Autostart.

### Project layout

```
app/src/main/java/com/nocturne/player/
├── NocturneApp.kt, MainActivity.kt
├── data/            Room entities/DAOs, repositories, prefs, storage paths
├── youtube/         link parsing, NewPipe OkHttp downloader, resolver, friendly errors
├── download/        DownloadRepository, DownloadWorker, download notification + actions
├── playback/        PlaybackService, notification provider, state store, PlayerConnection (MediaController)
└── ui/
    ├── theme/       NocturneTheme — Color.kt, Type.kt, Shape.kt
    ├── icons/       Lucide-style stroke icons
    ├── components/  MiniPlayer, TrackRow, DownloadRow, PlaylistCard, IconButton44, GradientButton, PillTabs, sliders, sheets
    └── screens/     Library/Playlists/Downloads (Home), Now Playing, Add music, Playlist detail, Settings
```

## Test checklist (on a real device)

Background playback
- [ ] Play a track → press **Home** → music keeps playing; the notification shows artwork, title, artist and 5 buttons.
- [ ] Lock the phone → the lock-screen card shows artwork, seek bar and shuffle / previous / play-pause / next / repeat.
- [ ] With the screen off, **wait for the track to end** → the next track starts automatically.
- [ ] Controls from the **notification**: previous (restarts if >3 s in), play/pause, next, shuffle (icon toggles), repeat (off → all → one).
- [ ] The same controls from the **lock screen**, plus seeking with the seek bar (Android 10+).
- [ ] Quick Settings media carousel (Android 13+) shows the session with shuffle/repeat.
- [ ] Swipe Nocturne away from **recents** while playing → playback continues. Pause → the notification can be dismissed.
- [ ] Bluetooth headset play/pause/next work. Unplugging wired headphones pauses. An incoming call pauses, then resumes after.
- [ ] Force-stop the app → press play on a Bluetooth device, or use the media card's resume → the last queue resumes at the saved position.
- [ ] Reopen the app → the mini-player shows the last track. Play continues from the saved position.
- [ ] Battery saver / Doze (`adb shell dumpsys deviceidle force-idle`) → playback continues.

Downloads
- [ ] Paste a playlist link → the card shows "PLAYLIST DETECTAT", the count, total duration and "N deja în bibliotecă".
- [ ] "Descarcă N melodii noi" → 4 rows download in parallel with percent, MB and speed. The global bar and the "Descărcări" notification update.
- [ ] Leave the app during downloads → they continue (foreground notification). "Pauză" / "Anulează" in the notification work.
- [ ] Airplane mode → the link shows "Fără internet…". Downloads mark "Eșuat" after 3 attempts, and "Reîncearcă" restarts them.
- [ ] Share a link from the YouTube app → Nocturne opens Add music with the link already resolved.
- [ ] Turn on airplane mode → every downloaded track plays.

Library
- [ ] Search, sort (Recent adăugate / Titlu / Durată), swipe-to-delete with confirmation, long-press menu.
- [ ] Create, rename and delete playlists. Imported playlists show "Din YouTube". Favorite tracks appear in *Favorite*.
- [ ] In Now Playing: the queue sheet reorders by drag, items can be removed, and swiping down closes the screen.

## Notes

- Only download content you have the right to download. Respect YouTube's Terms of Service and local copyright law.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is meant for sideloaded or personal builds. Google Play restricts it.

## License

Nocturne is free software released under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).
It bundles [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0) and the
[Manrope](https://github.com/googlefonts/manrope) font (SIL Open Font License 1.1).
