@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "EVALS_DIR=%~dp0"
cd /d "%EVALS_DIR%.."

set "MODE=%~1"
if "%MODE%"=="" set "MODE=all"

if /I "%MODE%"=="help" goto :help
if /I "%MODE%"=="experiments" goto :experiments
if /I "%MODE%"=="baseline" goto :baseline

if /I "%MODE%"=="live" (
    if /I not "%STORYWEAVER_EVAL_LIVE%"=="true" (
        echo.
        echo [ERROR] Live evaluation is disabled.
        echo Set STORYWEAVER_EVAL_LIVE=true before running live evals.
        echo.
        exit /b 2
    )
)

if not exist "%EVALS_DIR%scripts\run-all.ps1" (
    echo [ERROR] Missing evals\scripts\run-all.ps1
    exit /b 3
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%EVALS_DIR%scripts\run-all.ps1" -Mode "%MODE%" -Profile "local"
set "EXIT_CODE=%ERRORLEVEL%"
goto :result

:baseline
powershell -NoProfile -ExecutionPolicy Bypass -File "%EVALS_DIR%scripts\run-all.ps1" -Mode "all" -Profile "local" -RagStrategy "BASELINE" -RagCandidatePool 10 -RagFinalK 2147483647
set "EXIT_CODE=%ERRORLEVEL%"
goto :result

:experiments
powershell -NoProfile -ExecutionPolicy Bypass -File "%EVALS_DIR%scripts\run-rag-matrix.ps1"
set "EXIT_CODE=%ERRORLEVEL%"
goto :result

:result
if "%EXIT_CODE%"=="0" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%EVALS_DIR%scripts\build-overview.ps1"
    if errorlevel 1 set "EXIT_CODE=%ERRORLEVEL%"
    powershell -NoProfile -ExecutionPolicy Bypass -File "%EVALS_DIR%scripts\prune-reports.ps1"
    if errorlevel 1 set "EXIT_CODE=%ERRORLEVEL%"
    if not defined STORYWEAVER_EVAL_NO_OPEN start "" "%EVALS_DIR%reports\latest\all-results.html"
)
echo.
if "%EXIT_CODE%"=="0" (
    echo [OK] StoryWeaver Agent Evaluation completed.
    echo Report: evals\reports\latest\summary.md
) else (
    echo [FAILED] Evaluation exited with code %EXIT_CODE%.
)
echo.
if not defined STORYWEAVER_EVAL_NO_PAUSE pause
exit /b %EXIT_CODE%

:help
echo StoryWeaver Agent Evaluation
echo.
echo Usage:
echo   run-evals.cmd
echo   run-evals.cmd all
echo   run-evals.cmd rag
echo   run-evals.cmd token
echo   run-evals.cmd consistency
echo   run-evals.cmd workflow
echo   run-evals.cmd mcp
echo   run-evals.cmd baseline
echo   run-evals.cmd experiments
echo   run-evals.cmd live
echo   run-evals.cmd help
echo.
exit /b 0
