# Releasing

Releases are built by `.github/workflows/release.yml` and published on the GitHub
**Releases** page with the APK and its SHA-256 checksum.

## Publish a release

1. Update `CHANGELOG.md` (move *Unreleased* entries under the new version) and merge to the main
   branch.
2. Either push a tag:
   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```
   or run **Actions › Release › Run workflow** and enter `1.2.0`.
3. The workflow runs the tests, builds a minified release APK, signs it and creates the release
   `v1.2.0` with `JayMusic-1.2.0.apk`.

The version name is taken from the tag; the version code is derived from it
(`major * 10000 + minor * 100 + patch`), so newer versions always install over older ones.
Versions with a suffix (`1.2.0-beta.1`) are published as pre-releases.

## Signing

Android only installs an update if it is signed with the same key as the installed app.

- **Default:** releases are signed with the development key in `signing/dev.keystore`. It is
  public (committed to the repository), which is fine for a personal app installed from your own
  releases, but anyone could sign an APK with it.
- **Recommended:** use a private key stored in GitHub secrets. Create it once:
  ```bash
  keytool -genkeypair -keystore release.keystore -alias jaymusic -keyalg RSA -keysize 4096 -validity 36500
  base64 -w0 release.keystore   # copy the output
  ```
  and add these repository secrets (Settings › Secrets and variables › Actions):

  | Secret | Value |
  | --- | --- |
  | `SIGNING_KEYSTORE_BASE64` | base64 output from above |
  | `SIGNING_KEYSTORE_PASSWORD` | keystore password |
  | `SIGNING_KEY_ALIAS` | `jaymusic` |
  | `SIGNING_KEY_PASSWORD` | key password |

  Keep a backup of `release.keystore`. Switching from the development key to a private key
  requires uninstalling the app once (this deletes downloads, ratings and playlists).
