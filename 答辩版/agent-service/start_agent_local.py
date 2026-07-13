import os
from pathlib import Path

import uvicorn

BASE_DIR = Path(__file__).resolve().parent


def main() -> None:
    os.chdir(BASE_DIR)
    missing = [name for name in ("DATABASE_URL", "INTERNAL_SERVICE_SECRET") if not os.getenv(name)]
    if missing:
        raise SystemExit("Missing required environment variables: " + ", ".join(missing))
    # 后端在 docker-compose 中映射到宿主机 8081（容器内仍是 8080）
    os.environ.setdefault("BACKEND_BASE", "http://127.0.0.1:8081")
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, log_level="info")


if __name__ == "__main__":
    main()
