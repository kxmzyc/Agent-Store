@echo off
setlocal

cd /d "%~dp0"

echo Starting Agent-Store services...
docker compose up -d
if errorlevel 1 (
  echo.
  echo Failed to start Docker services. Make sure Docker Desktop is running.
  pause
  exit /b 1
)

echo.
docker compose ps

echo.
echo Opening frontend: http://127.0.0.1/
start "" "http://127.0.0.1/"
