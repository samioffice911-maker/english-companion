#!/usr/bin/env bash
# Automatic smoke test on a virtual phone: install, open every screen, take screenshots, check for crashes.
set -u
PKG=com.mahfuz.englishcompanion
ACT=$PKG/.MainActivity
OUT=shots
mkdir -p $OUT

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb install -r app.apk || { echo "INSTALL FAILED"; exit 1; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
open() { adb shell am start -n $ACT "$@" > /dev/null; }

# First launch (no API key -> Settings)
open; shot 00-first-launch 8

# Screens with sample data
open --ez demo true --es screen HOME;  shot 01-home 4
adb shell input swipe 540 1800 540 600 400; shot 02-home-scrolled 2
adb shell input swipe 540 1800 540 600 400; shot 03-home-bottom 2
open --es screen TALK;      shot 04-talk
open --es screen PROGRESS;  shot 05-progress
adb shell input swipe 540 1800 540 600 400; shot 06-progress-scrolled 2
open --es screen WORDS;     shot 07-words
open --es screen SETTINGS;  shot 08-settings
adb shell input swipe 540 1800 540 400 400; shot 09-settings-2 2
adb shell input swipe 540 1800 540 400 400; shot 10-settings-3 2

# Companion running (virtual mic is silent)
open --es screen TALK --es test_action start; shot 11-talk-running 5
adb shell cmd statusbar expand-notifications; shot 12-notification-player 3
adb shell cmd statusbar collapse
open --es test_action pause; shot 13-paused 3
open --es test_action resume; shot 14-resumed 3
open --es screen HOME; shot 15-home-running 3
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; shot 16-lock-screen 3
adb shell wm dismiss-keyguard || true
adb shell dumpsys notification --noredact > $OUT/notif-running.txt
open --es test_action stop; shot 17-stopped 3
sleep 2
adb shell dumpsys notification --noredact > $OUT/notif-after-stop.txt
if grep -q "pkg=$PKG" $OUT/notif-after-stop.txt; then echo "BUG: notification still there after Stop"; echo "notification after stop" >> $OUT/FAILED; else echo "OK: Stop removed the notification"; fi

# Exit test: start, then Exit -> notification gone, app closed, process gone
open --es screen HOME --es test_action start; sleep 5
open --es test_action exit; sleep 3
shot 17b-after-exit 1
adb shell dumpsys notification --noredact > $OUT/notif-after-exit.txt
if grep -q "pkg=$PKG" $OUT/notif-after-exit.txt; then echo "BUG: notification still there after Exit"; echo "notification after exit" >> $OUT/FAILED; else echo "OK: Exit removed the notification"; fi
if adb shell pidof $PKG > /dev/null; then echo "BUG: app process still running after Exit"; echo "process after exit" >> $OUT/FAILED; else echo "OK: app fully closed after Exit"; fi
adb shell dumpsys media_session | grep -i englishcompanion > $OUT/media-after-exit.txt || true

# Rotate test (layout robustness)
open --es screen HOME
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; shot 18-landscape 4
adb shell settings put system user_rotation 0

adb shell dumpsys activity services $PKG > $OUT/services.txt 2>&1
adb logcat -d > $OUT/logcat.txt
adb logcat -d -s AndroidRuntime:E > $OUT/crashes.txt

if grep -q "FATAL EXCEPTION" $OUT/logcat.txt && grep -A3 "FATAL EXCEPTION" $OUT/logcat.txt | grep -q "$PKG"; then
  echo "=== APP CRASHED ==="
  grep -A40 "FATAL EXCEPTION" $OUT/logcat.txt | head -80
  touch $OUT/CRASHED
fi
echo "done"
