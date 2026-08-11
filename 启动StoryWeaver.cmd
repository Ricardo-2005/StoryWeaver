@echo off
setlocal
cd /d "%~dp0"

echo Starting StoryWeaver...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-StoryWeaver.ps1"
set "exitCode=%ERRORLEVEL%"

if not "%exitCode%"=="0" (
  echo.
  echo Startup failed. See docs\GETTING_STARTED.md for troubleshooting.
  pause
)

exit /b %exitCode%
