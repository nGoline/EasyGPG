#!/usr/bin/env bash
# Give the emulator a lock screen and authenticate against it.
#
# PGPKeyManager seals secret key rings with an Android Keystore key created with
# setUserAuthenticationRequired(true). Creating that key needs a secure lock screen; using it needs
# an authentication within SECRET_KEY_AUTH_VALIDITY_SECONDS (300), and a device-credential unlock
# counts. Run this immediately before the tests, never before a build — a cold Gradle build can
# outlast the window, and the tests then fail rather than skip, because isDeviceSecure stays true.
#
# This lives in a file rather than inline in the workflow because android-emulator-runner executes
# its `script:` one line per `sh -c`, so loops and variables do not survive across lines.
set -euo pipefail

PIN=1234

echo "Setting a lock screen PIN..."
adb shell input keyevent KEYCODE_WAKEUP
adb shell locksettings set-pin "$PIN"

echo "Locking, then unlocking with the PIN so the Keystore records an authentication..."
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
adb shell input keyevent KEYCODE_MENU
sleep 1
adb shell input text "$PIN"
adb shell input keyevent KEYCODE_ENTER
sleep 3

# A silent failure here means every authenticated test errors out with something that looks
# nothing like "the device is still locked", so determine the state and say so plainly.
state="unknown"
for _ in $(seq 1 10); do
  dump="$(adb shell dumpsys window 2>/dev/null || true)"
  case "$dump" in
    *mDreamingLockscreen=false*) state="unlocked"; break ;;
    *mDreamingLockscreen=true*)  state="locked" ;;
  esac
  sleep 2
done

case "$state" in
  unlocked)
    echo "Device unlocked; the authenticated Keystore tests can run."
    ;;
  locked)
    echo "::error::Device is still locked — the authenticated Keystore tests cannot run."
    adb shell dumpsys window | grep -iE "lockscreen|keyguard" | head -5 || true
    exit 1
    ;;
  *)
    # Do not fail on an unrecognised dumpsys format; the tests themselves will report it.
    echo "::warning::Could not determine the lock state from dumpsys; continuing."
    ;;
esac
