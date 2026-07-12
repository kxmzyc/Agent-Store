# Agent-Store

Agent-Store is a teaching e-commerce application that combines a Vue 3 storefront, a Spring Boot business API, MySQL, and a FastAPI/LangChain shopping assistant.

## Layout

```text
Agent-Store/
|- frontend/       Vue 3 application
|- backend/        Spring Boot REST API
|- agent-service/  FastAPI and LangChain agent
|- docs/           Delivery, API, database, and defense documentation
|- scripts/        Smoke and concurrency acceptance scripts
|- docker-compose.yml
|- .env.example
`- README.md
```

## Start

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Set MYSQL_ROOT_PASSWORD, JWT_SECRET, and INTERNAL_SERVICE_SECRET in .env.
docker compose up -d --build
docker compose ps
```

Windows users can also run `./open-project.cmd` after configuring `.env`.

| Service | Address |
|---|---|
| Storefront | http://127.0.0.1/ |
| Backend Swagger | http://127.0.0.1:8081/swagger-ui/index.html |
| Agent API | http://127.0.0.1:8000 |
| MySQL | 127.0.0.1:3307 |

## Local Development

```powershell
cd frontend
npm run dev

cd ../backend
mvn spring-boot:run

cd ../agent-service
python start_agent_local.py
```

The local database schema is named `smart_mall`; it remains unchanged to preserve existing database configuration and data.

## Verification

```powershell
cd backend
mvn test

cd ../frontend
npm run build

cd ..
python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081
python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081
```

## Documentation

- [Architecture](docs/architecture.md)
- [API specification](docs/api-spec.md)
- [ER diagram](docs/er-diagram.md)
- [Database design](docs/database-design.md)
- [Bug list](docs/bug-list.md)
- [Acceptance report](docs/acceptance-report.md)
- [AI development record](docs/ai-development-record.md)
- [Defense materials](docs/defense/)

Generated logs, caches, PPT inspection files, and slide previews are excluded from version control. The final PPTX and PDF remain in `docs/defense/final/`.
