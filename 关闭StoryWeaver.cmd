@echo off
setlocal
cd /d "%~dp0"

echo Stopping StoryWeaver...
set "exitCode=0"

where docker.exe >nul 2>&1
if errorlevel 1 (
  echo Docker was not found. Make sure Docker Desktop is installed.
  set "exitCode=1"
  goto :finish
)

echo.
echo ==^> Stopping frontend
docker compose -f "%~dp0frontend\compose.frontend.yaml" stop
if errorlevel 1 set "exitCode=1"

echo.
echo ==^> Stopping backend services
docker compose -f "%~dp0backend\compose.yaml" stop
if errorlevel 1 set "exitCode=1"

:finish
echo.
if "%exitCode%"=="0" (
  echo StoryWeaver has stopped. Containers, networks, and data volumes were preserved.
) else (
  echo StoryWeaver did not stop cleanly. Review the messages above.
)

pause
exit /b %exitCode%
