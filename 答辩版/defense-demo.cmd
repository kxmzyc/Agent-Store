@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\defense_preflight.ps1" -Open
if errorlevel 1 (
  echo.
  echo Defense preflight failed. Keep this window open and check the message above.
  pause
  exit /b 1
)

echo.
echo Defense environment is ready.
pause
