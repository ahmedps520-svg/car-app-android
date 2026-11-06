#!/bin/sh
# Installs Car Browser on the car screen over Wi-Fi with adb.
# Usage:  sh install-mac-linux.sh 192.168.43.123
set -u
cd "$(dirname "$0")" || exit 1

ADB=adb
[ -x ./adb ] && ADB=./adb
[ -x ./platform-tools/adb ] && ADB=./platform-tools/adb
if ! "$ADB" version >/dev/null 2>&1; then
  echo "Could not find adb."
  echo "1. Download 'SDK Platform-Tools' for your computer from"
  echo "   https://developer.android.com/tools/releases/platform-tools"
  echo "2. Unzip it into this folder, so a 'platform-tools' folder sits next to this file."
  echo "3. Run this script again."
  exit 1
fi

APK=./CarBrowser.apk
[ -f "$APK" ] || APK=../apk/CarBrowser.apk
if [ ! -f "$APK" ]; then
  echo "Could not find CarBrowser.apk. Put it next to this script."
  exit 1
fi

IP="${1:-}"
if [ -z "$IP" ]; then
  printf "Car screen IP address (Settings > About > Status > IP address): "
  read -r IP
fi
[ -z "$IP" ] && exit 1
case "$IP" in *:*) ;; *) IP="$IP:5555" ;; esac

echo "Connecting to $IP ..."
"$ADB" connect "$IP"
echo "Installing Car Browser (this can take a minute) ..."
if ! "$ADB" -s "$IP" install -r "$APK"; then
  echo
  echo "Install failed. Read 'Troubleshooting' in README.md."
  echo "If the car screen asked 'Allow USB debugging?', tick 'Always allow', press OK and run this again."
  exit 1
fi
echo "Starting Car Browser ..."
"$ADB" -s "$IP" shell am start -n io.github.ahmedps520.carbrowser/.MainActivity
echo "Done! Car Browser should now be open on the car screen."
