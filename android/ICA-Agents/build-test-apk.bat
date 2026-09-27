@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle was not found. Open this project in Android Studio or install Gradle 8.7.
  exit /b 1
)
gradle --no-daemon assembleDebug
if errorlevel 1 exit /b 1
echo.
echo APK created at:
echo app\build\outputs\apk\debug\app-debug.apk
endlocal
