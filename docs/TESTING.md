# On-device test checklist

Install a release build (`./gradlew assembleRelease`) on a real phone and go through these checks.

## Background playback

- [ ] Play a track and press **Home**. Music keeps playing. The notification shows artwork, title, artist and 5 buttons.
- [ ] Lock the phone. The lock-screen card shows artwork, a seek bar and shuffle / previous / play-pause / next / repeat.
- [ ] With the screen off, **wait for the track to end**. The next track starts automatically.
- [ ] From the **notification**: previous (restarts if more than 3 s in), play/pause, next, shuffle (the icon toggles), repeat (off → all → one).
- [ ] The same controls from the **lock screen**, plus dragging the seek bar (Android 10+).
- [ ] The Quick Settings media carousel (Android 13+) shows the session with shuffle and repeat.
- [ ] Swipe Nocturne away from **recents** while playing. Playback stops by default; with *Redare după închiderea aplicației* on, it continues.
- [ ] Pause, then dismiss the notification. It can only be dismissed while paused.
- [ ] Bluetooth play/pause/next work. Unplugging wired headphones pauses. An incoming call pauses playback.
- [ ] Force-stop the app, then press play on a Bluetooth device or the media card. The last queue resumes at the saved position.
- [ ] Reopen the app. The mini-player shows the last track, and play continues from the saved position.
- [ ] Doze (`adb shell dumpsys deviceidle force-idle`) and battery saver. Playback continues.

## Downloads

- [ ] Paste a playlist link. The card shows "PLAYLIST DETECTAT", the track count, total duration and "N deja în bibliotecă".
- [ ] Paste a Mix link (`list=RD…`). It imports the first 50 tracks as a *Din YouTube* playlist.
- [ ] Tap "Descarcă N melodii noi". Four rows download in parallel with percent, size and speed, and the "Descărcări" notification updates.
- [ ] Leave the app during downloads. They continue, and the notification's "Pauză" and "Anulează" work.
- [ ] Turn on airplane mode. The link shows "Fără internet…". Downloads become "Eșuat" after 3 attempts, and "Reîncearcă" restarts them.
- [ ] Share a link from the YouTube app. Nocturne opens *Adaugă muzică* with the link already resolved.
- [ ] With airplane mode on, every downloaded track plays.

## Library

- [ ] Search and sort (Recent adăugate / Titlu / Durată). Swipe to delete asks for confirmation. Long-press opens the menu.
- [ ] Create, rename and delete playlists. Favorited tracks appear in *Favorite*.
- [ ] In Now Playing, drag to reorder the queue, remove items, and swipe down to close.

## Version 1.1 — languages, metadata, widgets

### Update over the old install (migration)
- [ ] Install 1.1 **over** 1.0 (no uninstall). Library, favorites, playlists ("Din YouTube" too), queue position and the audio files are all still there.
- [ ] Search still works on old songs (their search keys are rebuilt during the database upgrade).

### Change the language live
- [ ] Start a song, open **Setări → Limbă**, pick **English**. The screen switches immediately, stays on Settings at the same scroll position, and **playback continues without a gap**.
- [ ] While a playlist is downloading, switch to **Русский**. Downloads keep going; the download notification and its channel name (system notification settings) are in Russian.
- [ ] Every screen, dialog, error message, the share-intent screen, the media notification (shuffle / repeat labels) and the widgets are translated.
- [ ] Song titles, artists and playlist names are **unchanged** in every language.
- [ ] Plurals: RO "1 melodie / 2 melodii / 20 de melodii", RU "1 песня / 2 песни / 5 песен", EN "1 song / 2 songs". Speeds: "4,2 MB/s" (RO), "4.2 MB/s" (EN), "4,2 МБ/с" (RU).
- [ ] **Limba sistemului** follows the phone language; for an unsupported phone language the app falls back to Română.
- [ ] Android 13+: Settings → Apps → Nocturne → **App languages** lists RO / EN / RU and changing it there updates the app, notification and widgets.

### Edit a track
- [ ] Long-press a song → **Editează detaliile**, change title and artist, save. The new values appear at once in the list, Now Playing, mini-player, **media notification**, **lock screen** and **widget**, and playback doesn't stop.
- [ ] The same from the Now Playing **…** menu.
- [ ] Re-add the same YouTube link: it is detected as already in the library (by video id) and the edited title is kept.
- [ ] Search "oras" finds "oraș", "ceata" finds "Ceață", "горы" finds "Горы".

### Widgets
- [ ] The widget appears in the launcher's widget picker right after install, with a preview.
- [ ] Add it at **2×2**, **4×2** and **4×4** (resize one widget or add three). Each layout matches the design and the app language.
- [ ] Swipe the app away from recents (or force-stop it), then press **play / next / previous / shuffle / repeat** on the widget: they work and the widget updates.
- [ ] Let a track end with the app closed: the next track auto-plays and the widget shows it.
- [ ] Tap the artwork → Now Playing opens. On 4×4, copy a YouTube link and tap **Adaugă link** → *Adaugă muzică* opens with the link.
- [ ] The progress bar moves only while playing; paused, the widget doesn't update (no battery drain).
