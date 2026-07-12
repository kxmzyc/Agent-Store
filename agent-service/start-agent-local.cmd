@echo off
if "%DATABASE_URL%"=="" (
  echo DATABASE_URL must be configured before starting the Agent service.
  exit /b 1
)
if "%INTERNAL_SERVICE_SECRET%"=="" (
  echo INTERNAL_SERVICE_SECRET must be configured before starting the Agent service.
  exit /b 1
)
if "%BACKEND_BASE%"=="" set BACKEND_BASE=http://127.0.0.1:8081
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
