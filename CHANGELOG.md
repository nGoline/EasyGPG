# Changelog

Notable changes to Easy GPG. Versions correspond to `versionName` in
`app/build.gradle.kts`; the `versionCode` Play uses is noted alongside.

Easy GPG is **alpha, unaudited software**. See the
[security notes](README.md#security) before trusting it with anything that matters.

## 0.4 — 2026-09-03 (versionCode 6)

The first release published through CI, and a long one: 0.3 shipped in July 2025.
Almost everything here is about keeping key material out of reach.

### Key protection

- Secret key rings are encrypted with a passphrase you choose when the key is generated,
  instead of a placeholder baked into the app. Key rings created by earlier versions are
  migrated the first time you decrypt.
- Private keys are additionally sealed with an Android Keystore key that requires biometric
  or device-credential authentication within the last five minutes, so they cannot be read
  without you being present.
- Removing or resetting the device lock screen destroys that key, and with it access to the
  stored private keys. This is deliberate.
- Private keys can be exported, after authenticating.

### Passphrase handling

- Choose how long an entered passphrase is remembered: until the screen turns off, one hour
  (the default), or one day. It is held in memory only, overwritten when it expires, and gone
  once the app process ends.
- Secret input is read into buffers the app owns and overwrites after use, rather than into
  `String`s that cannot be scrubbed.

### Privacy

- App-wide privacy mode keeps the app out of screenshots and the recents list.
- Overlay windows are blocked while the app is in the foreground.
- Text fields opt out of personalised keyboard learning, so passphrases and plaintext are not
  added to the keyboard's dictionary or prediction model.
- Nothing is shown until authentication succeeds.
- Optional setting to clear the encrypt and decrypt fields after each operation.

### Key management

- Delete your own keys from the Keys screen.
- Imported keys ask for confirmation before deletion.
- Fingerprints use the same short format everywhere they appear.

### Fixes

- The Encrypt message field is a proper multi-line text area.
- The overflow menu is readable in dark mode.

## 0.3 — 2025-07-07 (versionCode 4)

- Key rings are encrypted at rest.
- Optional obfuscation of PGP markers in encrypted output.
