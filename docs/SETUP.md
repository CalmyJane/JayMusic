# Setting up JayMusic with a Synology NAS

JayMusic talks to the **File Station** API that is part of every DSM installation (DSM 6.2 and
DSM 7.x). Audio Station does not need to be installed; your songs are read straight from the
shared folders.

## 1. Prepare the NAS

1. Put your music in a shared folder, e.g. `music` (its NAS path is then `/music`).
   Any layout works; `Artist/Album/01 - Title.flac` gives the best results before tags are read.
2. Recommended: create a separate DSM user for the phone (Control Panel › User & Group) with
   **read-only** permission on the music folder(s) and access to the **File Station**
   application (Control Panel › User & Group › user › Applications).
3. Supported formats: MP3, FLAC, M4A/AAC/ALAC, OGG/Opus, WAV, MKA, AMR.

## 2. Addresses

JayMusic can use two addresses and picks the best one automatically:

| Field | Example | Used |
| --- | --- | --- |
| Home address | `http://192.168.1.10:5000` | Tried first. Fast, no internet traffic. |
| Internet address | `https://nas.example.com:5001` | When away from home. |

Fill in at least one. Plain host names without `http://` are treated as `http://`.

**QuickConnect IDs are not supported** (QuickConnect is a proprietary relay). Use DDNS
(Control Panel › External Access › DDNS, e.g. `yourname.synology.me`) with port forwarding of
port 5001, or a VPN such as Tailscale and the NAS's VPN address.

## 3. HTTPS certificates

- **Let's Encrypt certificate** (Control Panel › Security › Certificate › Add): works out of the
  box. Recommended for the internet address.
- **Self-signed certificate**: Android does not trust it by default. Export it from DSM and
  install it on the phone (Settings › Security › Encryption & credentials › Install a
  certificate › CA certificate). JayMusic trusts user-installed certificates.
- On the home network plain `http://…:5000` is fine.

## 4. 2-factor authentication

If your account uses 2-factor authentication, JayMusic asks for the code once. The NAS then
issues a device token, so the phone is remembered and no further codes are needed.

## 5. First sync

Tap **Sync now** in Settings. JayMusic

1. scans the music folders (several folders in parallel),
2. shows the library immediately,
3. downloads songs in the background, best rated first, until the storage limit is reached.

A background sync also runs every 12 hours (by default only on Wi‑Fi) to pick up new songs.

## Troubleshooting

| Problem | Solution |
| --- | --- |
| “Wrong user name or password” | Check the credentials; DSM blocks the IP after several failed attempts (Control Panel › Security › Protection › Allow/Block list). |
| “File Station is not available” | Give the user permission for the File Station application. |
| “No such file or directory” during sync | Check the music folder path; it starts with the shared folder name, e.g. `/music`, not `/volume1/music`. |
| Offline although the NAS is up | Open the address in the phone's browser. For HTTPS check the certificate (see above). |
| Songs show odd titles | Titles come from file names until a song is played or downloaded once; then its tags are used. |
