# Using JayMusic

## Playing music

- **Library**: the bar at the top switches between songs (♪) and folders (folder icon), and has
  the sort menu, the *on phone* filter (downloaded songs only) and the search (magnifier). Tap a
  song to play the list from there.
- **Player bar** (bottom): song and time (tap to open the full screen player), a seek slider
  that works while playing or paused, and shuffle / previous / play / next / repeat. The lock
  button opens the big lock screen player: press the power button afterwards and it is the first
  thing you see when you turn the screen on again.
- **Full screen player**: very large previous / play / next buttons, shuffle and repeat toggles
  (filled = on), seek bar and the song's rating.
- **Big lock screen player**: when you turn the screen on while music plays and the phone is
  locked, JayMusic opens its own full screen player with huge previous / next / play-pause /
  shuffle buttons and the rating. *Unlock* (or unlocking the phone normally) closes it.
  Set it up in Settings › Lock screen › Big lock screen player: the page lists the permissions
  Android requires (notifications and full screen notifications, like an alarm clock app; *display
  over other apps* is optional and makes it appear a little faster) and has a *Preview* button.
- **System media controls**: the notification and Android's own lock screen controls also offer
  previous, play/pause, next, shuffle and repeat. Their size is decided by Android and the phone
  manufacturer and cannot be changed by apps — hence the big player above.

**Shuffle** is a switch: it never changes the song that is playing, it only decides whether the
*next* song is random. With shuffle on, *previous* goes back to the song you actually heard
before (also several steps back), and *next* continues with random songs. *Play* starts a list
from the top, or from a random song when shuffle is on.

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
| "Never download" folder or song | Never stored (existing copies are deleted). Still streamable when online. Set it with the cloud icon in the folder view or in a song's menu; tap again to allow downloads. |

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
