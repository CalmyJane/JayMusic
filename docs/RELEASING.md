# Releasing

Everything is done by the single pipeline `.github/workflows/ci.yml`. Every build runs the unit
tests and Android lint and builds a signed, minified APK before anything is published.

| Trigger | Result on the Releases page |
| --- | --- |
| Push to any branch | **Test build** `v0.1.0-test.<run>` (run number zero-padded, e.g. `test.0012`) (marked *Pre-release*). |
| Push a tag `v1.2.3` | **Final release** “JayMusic 1.2.3”, marked *Latest*, with generated release notes. |
| Push a tag `v1.2.3-beta.1` | Pre-release “Test build 1.2.3-beta.1”. |
| Actions › CI › *Run workflow* with a version | Final release of that version from the chosen branch. |
| Pull request | Checks only, nothing is published. |

## Test builds

- Name: `<appVersion>-test.<run number>`; `appVersion` is the base version in `gradle.properties`.
- Only the newest 5 test builds are kept (`KEEP_TEST_RELEASES` in the workflow); older ones and
  their tags are deleted automatically.
- Publishing a final release deletes **all** test builds.

## Final release

1. Update `CHANGELOG.md` (move *Unreleased* entries under the new version).
2. Publish, either:
   - push a tag: `git tag v1.2.0 && git push origin v1.2.0`, or
   - GitHub › Actions › **CI** › *Run workflow*, enter `1.2.0`, or
   - GitHub › Releases › *Draft a new release*, create tag `v1.2.0` on publish (the workflow
     then attaches the APK to that release).
3. Bump `appVersion` in `gradle.properties` to the next version (e.g. `1.2.1`) so further test
   builds are named after it.

## Version codes

The version code is the workflow run number, which only ever increases. Therefore every build,
test or final, installs over the previously installed one.

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
