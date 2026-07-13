@echo off
setlocal
cd /d "%~dp0frontend"

set VITE_BACKEND_PROXY_TARGET=http://127.0.0.1:8081
set VITE_AGENT_PROXY_TARGET=http://127.0.0.1:8001
set VITE_CACHE_DIR=../work/vite-defense-cache

echo Live-edit site: http://127.0.0.1:5174/
echo Keep this window open while editing Vue files.
npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort --configLoader runner
