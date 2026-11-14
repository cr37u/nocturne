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
