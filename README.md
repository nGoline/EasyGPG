# Easy GPG

An Android app that makes PGP/GPG encryption approachable. Encrypt and decrypt
messages, manage your keys, and use a hardware YubiKey — without touching a
command line.

> ⚠️ **Alpha software.** Easy GPG is under active development and has not been
> audited. Do not rely on it to protect data whose disclosure would put you at
> risk. See [Security](#security) below.

## Features

- 🔐 Encrypt and share messages with PGP public keys
- 🔓 Decrypt messages with your private keys
- 🗝️ Generate, import, and manage key rings on device
- 🛡️ Hardware-backed key storage via the Android Keystore
- 🔑 YubiKey support over USB and NFC (OpenPGP applet)
- 🌐 Localized in English and Portuguese (Brazil)

## Tech stack

- Kotlin, Android SDK 35+ (`compileSdk` 36)
- [Bouncy Castle](https://www.bouncycastle.org/) (`bcpg` / `bcprov`) for OpenPGP
- [Yubico YubiKit](https://github.com/Yubico/yubikit-android) for hardware keys
- AndroidX (Navigation, Lifecycle, Preference, Security-Crypto, Biometric)

## Building

Requires Android Studio (latest stable) and JDK 21.

```bash
git clone https://github.com/<your-username>/EasyGPG.git
cd EasyGPG
./gradlew assembleDebug
```

Or open the project in Android Studio and run it on a device/emulator. The SDK
location is read from `local.properties`, which Android Studio generates for you
(it is intentionally not committed).

## Contributing

Contributions are very welcome — this project is looking for help to keep
development alive. Please read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

## Security

Easy GPG handles cryptographic material, so please treat it with care:

- It is **alpha, unaudited** software.
- There are known limitations being worked on — for example, generated key
  rings currently use a placeholder passphrase (see `PGPKeyManager.kt`). This is
  tracked and should be fixed before any stable release.
- Found a vulnerability? Please **do not** open a public issue. Instead, report
  it privately to the maintainer (see [CONTRIBUTING.md](CONTRIBUTING.md)).

## License

Easy GPG is free software licensed under the
[GNU General Public License v3.0](LICENSE). You may redistribute and/or modify
it under those terms. It comes with **no warranty**.
