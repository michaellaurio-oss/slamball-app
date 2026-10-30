@echo off
rem Build Slam Log and install it on the phone plugged in by USB.
rem Double-click this file, or run it from PowerShell / Command Prompt.
cd /d "%~dp0"
set JAVA_HOME=D:\Users\micha\Android\jdk-17
set ADB=D:\Users\micha\Android\sdk\platform-tools\adb.exe

echo.
echo === Building Slam Log (the first build takes a few minutes) ===
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the red error lines above and paste them to Claude.
  pause
  exit /b 1
)

echo.
echo === Looking for your phone ===
"%ADB%" devices
echo.
echo === Installing on the phone ===
"%ADB%" install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
  echo.
  echo INSTALL FAILED. Check the phone is plugged in, unlocked, and that you tapped Allow
  echo on "Allow USB debugging?". The built app is here if you'd rather copy it over:
  echo   %~dp0app\build\outputs\apk\debug\app-debug.apk
  pause
  exit /b 1
)

echo.
echo Done. Slam Log is in your phone's app drawer.
pause
