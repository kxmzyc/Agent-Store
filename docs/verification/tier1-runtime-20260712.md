# Tier 1 Runtime Acceptance Evidence

Executed on 2026-07-12 in the local Docker Desktop environment after restoring the existing F: Docker data disk. The LLM configuration came from the local ignored `.env`; no credentials are recorded in this document.

## Container Health

Command: `docker compose ps`

```text
agent-store-agent-service-1   Up (healthy)   0.0.0.0:8000->8000/tcp
agent-store-backend-1         Up (healthy)   0.0.0.0:8081->8080/tcp
agent-store-frontend-1        Up (healthy)   0.0.0.0:80->80/tcp
agent-store-mysql-1           Up (healthy)   0.0.0.0:3307->3306/tcp
```

The backend health probe calls its public product API. The frontend probe calls the Nginx home page. The Agent probe calls `/health`, and the MySQL probe uses `mysqladmin ping`.

## Concurrent Ordering

Command:

```powershell
python scripts\concurrent_order_test.py --rounds 3
```

The script created a new user and a new product for each round, then sent 100 simultaneous direct-order requests against stock 80. All three rounds passed:

```text
round=1 success=80 stock_not_enough=20 unexpected=0 stock=0 sales=80 version=80 orders=80 itemQty=80 passed=True
round=2 success=80 stock_not_enough=20 unexpected=0 stock=0 sales=80 version=80 orders=80 itemQty=80 passed=True
round=3 success=80 stock_not_enough=20 unexpected=0 stock=0 sales=80 version=80 orders=80 itemQty=80 passed=True
```

Full raw output: [tier1-concurrency-20260712.log](tier1-concurrency-20260712.log).

## Agent Smoke Test

Command:

```powershell
python scripts\agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081
```

Actual result:

```text
Agent smoke passed. user_id=2
product tools=['recommend_by_preference', 'search_products', 'compare_products']
purchase plan flow=passed
detail tools=['get_product_detail']
compare tools=['get_product_detail', 'get_product_detail', 'compare_products', 'search_knowledge_base']
order tools=['query_order_status']
memory tools=['recommend_by_preference', 'search_knowledge_base', 'get_product_detail', 'get_product_detail', 'get_product_detail']
cart tools=['add_to_cart']
```

This exercises live product data, purchase-plan generation, product detail and comparison, authenticated order lookup, long-term preference use, cart confirmation, and cart mutation. Full raw output: [tier1-agent-smoke-20260712.log](tier1-agent-smoke-20260712.log).
