# Using JayMusic

## Playing music

- **Library › Songs**: all songs with search (title, artist, album), sort options and the
  **On phone** filter (downloaded songs only). Tap a song to play the list from there; **Play** /
  **Shuffle** play the whole (filtered) list.
- **Library › Folders**: browse the NAS folder structure.
- **Mini player** (bottom): tap it to open the full screen player.
- **Full screen player**: very large previous / play / next buttons, shuffle and repeat toggles
  (filled = on), seek bar and the song's rating.
- **Big lock screen player**: when the screen turns off during playback, JayMusic shows its own
  full screen player on the lock screen with huge previous / next / play-pause / shuffle buttons
  and the rating. *Unlock* (or unlocking the phone normally) closes it. It needs the permission
  *Display over other apps* once (Settings › Lock screen › Big lock screen player; Android only
  lets apps open a screen in the background with it). It can be switched off there.
- **System media controls**: the notification and Android's own lock screen controls also offer
  previous, play/pause, next, shuffle and repeat. Their size is decided by Android and the phone
  manufacturer and cannot be changed by apps — hence the big player above.

Long-press a song (or tap ⋮) for: rate, play next, add to queue, add to playlist.

## Ratings

Tap 1–5 stars; tap the current rating again to clear it. Ratings are stored on the phone and
decide which songs are kept when storage runs out (see below).

## Playlists

Create playlists in the **Playlists** tab or from any song's menu (**Add to playlist › New
playlist…**). In a playlist, a song's menu also offers *Move up*, *Move down* and *Remove*.

## Storage and downloads

| Situation | Behaviour |
| --- | --- |
| A song is played while online | It is streamed and stored on the phone while playing. |
| Songs coming up in the queue | The next *n* songs (Settings › *Download ahead*) are downloaded in advance. |
| Background sync | Fills remaining space with the best rated songs, then most played. |
| Storage limit reached | The worst rated songs are deleted first. Unrated songs count as 2.5 stars, so songs you rated 1–2 stars go before unrated ones, and 3–5 star songs are kept longest. Among equal ratings, the least recently played go first. |
| Background sync and a full phone | A song is only downloaded if a *worse* rated song can be deleted for it, so the phone never churns. |
| Excluded folder | Never stored (existing copies are deleted). Still streamable when online. |

The limit is set in **Settings › Storage**. JayMusic also always leaves 500 MB free on the phone.

## Offline and slow connections

JayMusic never waits for the NAS:

- Starting a list while offline plays only the songs that are on the phone (a note says how
  many were left out).
- If the connection drops mid-queue, songs that are not on the phone are skipped.
- If a song does not start within 8 seconds (slow server or mobile network), the next downloaded
  song plays instead.
- The connection is re-established automatically when the network changes; the home address is
  preferred whenever it is reachable.
