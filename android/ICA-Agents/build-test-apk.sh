#!/usr/bin/env bash
set -euo pipefail
command -v gradle >/dev/null || { echo 'Gradle 8.7 is required. Open the project in Android Studio or install Gradle.'; exit 1; }
gradle --no-daemon assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
