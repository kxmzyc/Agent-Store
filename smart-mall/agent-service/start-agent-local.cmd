@echo off
set DATABASE_URL=mysql+pymysql://root:smartmall_root@127.0.0.1:3307/smart_mall?charset=utf8mb4
set BACKEND_BASE=http://127.0.0.1:8080
set INTERNAL_SERVICE_SECRET=agent-internal-secret
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
