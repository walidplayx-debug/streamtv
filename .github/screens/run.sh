#!/bin/bash
# Runs on the Android TV emulator: installs the debug app on the test playlist and photographs
# each screen as the remote walks through it. Pictures land in shots/.
set -x
APP=com.streamtv.debug
URL="https://api-tester-fcn5.onrender.com/get.php?username=testuser&password=testpass&type=m3u_plus"
mkdir -p shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Wake the test server (it sleeps when idle) before the app asks it for anything.
curl -s -o /dev/null --max-time 90 "$URL" || true
python3 .github/screens/prefs.py "$URL" "${LANGUAGE:-ar}" prefs.pb
adb shell am start -W -n $APP/com.streamtv.MainActivity; sleep 3; adb shell am force-stop $APP
adb push prefs.pb /data/local/tmp/prefs.pb
adb shell "run-as $APP mkdir -p files/datastore"
adb shell "run-as $APP cp /data/local/tmp/prefs.pb files/datastore/streamtv_settings.preferences_pb"
adb shell am start -W -n $APP/com.streamtv.MainActivity
sleep 40
n=0
shot() { n=$((n+1)); adb exec-out screencap -p > "shots/$(printf %02d $n)-$1.png"; }
key() { for k in "$@"; do adb shell input keyevent "$k"; sleep 1.2; done; sleep 2; }
shot start
# Steps from the workflow input: "name:KEY KEY ..;name:KEY ..". Keys are Android names (DPAD_UP, ENTER, BACK...).
IFS=';' read -ra STEPS <<< "$STEPS"
for step in "${STEPS[@]}"; do
  name="${step%%:*}"; keys="${step#*:}"
  key $keys
  shot "$name"
done
adb logcat -d -t 400 > shots/logcat.txt
