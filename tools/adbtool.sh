#!/usr/bin/env bash
# adbtool.sh — ADB wrapper for WSL2 → Android debugging
# Usage: ./tools/adbtool.sh <command> [args...]
#
# Commands:
#   devices          - List connected devices
#   install          - Install debug APK
#   launch           - Launch the app
#   stop             - Force stop the app
#   restart          - Stop + launch
#   screenshot [out] - Take screenshot, copy to /tmp/phone.png
#   logcat [lines]   - Get last N lines of app logcat (default 100)
#   crash            - Get crash/error logs
#   seturl <url>     - Set bridge server URL in SharedPreferences
#   geturl           - Read current saved URL
#   clear            - Clear app data
#   tap <x> <y>      - Tap screen coordinates
#   text <string>    - Input text
#   key <keycode>    - Send keyevent
#   shell <cmd>      - Run arbitrary adb shell command

set -euo pipefail

ADB="/init /mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -Command"
PKG="com.claudescreensaver"
APK_PATH="C:\\Users\\Daniel\\Desktop\\claude-screensaver-v0.1.0-debug.apk"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

adb_cmd() {
    /init /mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -Command "adb $*" 2>&1
}

case "${1:-help}" in
    devices)
        adb_cmd "devices"
        ;;
    install)
        echo "Building APK..."
        cd "$PROJECT_DIR/android"
        JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21 \
        ANDROID_HOME=/home/semmy/android-sdk \
        ./gradlew assembleDebug 2>&1 | tail -3
        cp app/build/outputs/apk/debug/app-debug.apk "/mnt/c/Users/Daniel/Desktop/claude-screensaver-v0.1.0-debug.apk"
        echo "Installing on device..."
        adb_cmd "install -r '$APK_PATH'"
        ;;
    launch)
        adb_cmd "shell am start -n $PKG/.MainActivity"
        ;;
    stop)
        adb_cmd "shell am force-stop $PKG"
        ;;
    restart)
        adb_cmd "shell am force-stop $PKG"
        sleep 1
        adb_cmd "shell am start -n $PKG/.MainActivity"
        ;;
    screenshot)
        OUT="${2:-/tmp/phone.png}"
        adb_cmd "shell screencap -p /sdcard/screen.png"
        adb_cmd "pull /sdcard/screen.png C:\\Users\\Daniel\\Downloads\\screen.png"
        cp /mnt/c/Users/Daniel/Downloads/screen.png "$OUT"
        echo "Screenshot saved to $OUT"
        ;;
    logcat)
        LINES="${2:-100}"
        adb_cmd "logcat -d -t $LINES" | grep -i "$PKG\|AndroidRuntime\|FATAL\|Exception\|Error" || echo "No matching logs"
        ;;
    crash)
        adb_cmd "logcat -d -t 200" | grep -A5 "FATAL EXCEPTION\|AndroidRuntime\|$PKG" || echo "No crashes found"
        ;;
    seturl)
        URL="${2:?Usage: adbtool.sh seturl <url>}"
        adb_cmd "shell am force-stop $PKG"
        # Write prefs XML to sdcard, then copy via run-as
        PREFS_XML="<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?><map><string name=\"server_url\">$URL</string></map>"
        echo "$PREFS_XML" > /tmp/_prefs.xml
        cp /tmp/_prefs.xml /mnt/c/Users/Daniel/Downloads/_prefs.xml
        adb_cmd "push C:\\Users\\Daniel\\Downloads\\_prefs.xml /data/local/tmp/_prefs.xml"
        adb_cmd "shell run-as $PKG cp /data/local/tmp/_prefs.xml /data/data/$PKG/shared_prefs/claude_screensaver.xml"
        adb_cmd "shell rm /data/local/tmp/_prefs.xml"
        echo "URL set to: $URL"
        adb_cmd "shell am start -n $PKG/.MainActivity"
        ;;
    geturl)
        adb_cmd "shell run-as $PKG cat /data/data/$PKG/shared_prefs/claude_screensaver.xml"
        ;;
    clear)
        adb_cmd "shell pm clear $PKG"
        echo "App data cleared"
        ;;
    tap)
        X="${2:?Usage: adbtool.sh tap <x> <y>}"
        Y="${3:?Usage: adbtool.sh tap <x> <y>}"
        adb_cmd "shell input tap $X $Y"
        ;;
    text)
        shift
        adb_cmd "shell input text '$*'"
        ;;
    key)
        adb_cmd "shell input keyevent ${2:?Usage: adbtool.sh key <keycode>}"
        ;;
    shell)
        shift
        adb_cmd "shell $*"
        ;;
    help|*)
        echo "adbtool.sh — ADB wrapper for Claude ScreenSaver debugging"
        echo ""
        echo "Commands:"
        echo "  devices          List connected devices"
        echo "  install          Build + install debug APK"
        echo "  launch           Launch the app"
        echo "  stop             Force stop"
        echo "  restart          Stop + launch"
        echo "  screenshot [out] Screenshot to /tmp/phone.png"
        echo "  logcat [lines]   App logcat (default 100 lines)"
        echo "  crash            Show crash/error logs"
        echo "  seturl <url>     Set bridge server URL"
        echo "  geturl           Read saved URL"
        echo "  clear            Clear app data"
        echo "  tap <x> <y>     Tap coordinates"
        echo "  text <string>    Input text"
        echo "  key <keycode>    Send keyevent"
        echo "  shell <cmd>      Raw adb shell"
        ;;
esac
