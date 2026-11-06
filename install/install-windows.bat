@echo off
setlocal
title Install Car Browser on the car screen
cd /d "%~dp0"

rem --- find adb (Google "SDK Platform-Tools") ---
set "ADB=adb"
if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
"%ADB%" version >nul 2>&1
if errorlevel 1 (
  echo Could not find adb.exe.
  echo.
  echo 1. Download "SDK Platform-Tools for Windows" from
  echo    https://developer.android.com/tools/releases/platform-tools
  echo 2. Unzip it INTO THIS FOLDER, so a "platform-tools" folder sits next to this file.
  echo 3. Double-click this file again.
  pause
  exit /b 1
)

rem --- find the APK ---
set "APK=%~dp0CarBrowser.apk"
if not exist "%APK%" set "APK=%~dp0..\apk\CarBrowser.apk"
if not exist "%APK%" (
  echo Could not find CarBrowser.apk. Put it in the same folder as this file.
  pause
  exit /b 1
)

rem --- car screen address ---
set "IP=%~1"
if "%IP%"=="" set /p "IP=Car screen IP address (Settings, About, Status, IP address): "
if "%IP%"=="" exit /b 1
echo %IP%| find ":" >nul || set "IP=%IP%:5555"

echo.
echo Connecting to %IP% ...
"%ADB%" connect %IP%
echo.
echo Installing Car Browser (this can take a minute) ...
"%ADB%" -s %IP% install -r "%APK%"
if errorlevel 1 (
  echo.
  echo Install failed. Read "Troubleshooting" in README.md.
  echo If the car screen asked "Allow USB debugging?", tick "Always allow", press OK and run this again.
  pause
  exit /b 1
)
echo.
echo Starting Car Browser ...
"%ADB%" -s %IP% shell am start -n io.github.ahmedps520.carbrowser/.MainActivity
echo.
echo Done! Car Browser should now be open on the car screen.
pause
