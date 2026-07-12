# Tier 3 Documentation Consistency Evidence

Executed on 2026-07-12. This tier changes documentation and comments only; service orchestration and Agent application logic were left unchanged.

## Knowledge Chunk Schema

The same table shape is now documented in all three authoritative locations:

- `docs/schema.sql`: production initialization DDL.
- `docs/er-diagram.md`: entity fields and the explanation of the polymorphic `source_type/source_id` logical reference.
- `docs/database-design.md`: boundary, field-level description, 3NF rationale, and index summary.

The Agent startup fallback remains in `agent-service/app/main.py`, and the knowledge-base builder keeps its matching `ensure_table()` DDL. No cross-service foreign key was added: `product`/`review` chunks use a logical source ID, while FAQ chunks use `NULL`.

Live read-only verification against the running MySQL container:

```text
SHOW COLUMNS FROM knowledge_chunk
id              bigint          PRI
source_type     varchar(20)     MUL
source_id       bigint
content         text
embedding_json  mediumtext
updated_at      datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
knowledge_chunk row count: 0
```

The current demo seed does not insert knowledge chunks; `build_knowledge_base.py` populates them from live products, reviews, and FAQ text. The table itself is present and ready, and the Agent fallback can create it on a fresh database.

## Stale Text Cleanup

- Updated the knowledge-base FAQ that incorrectly said refunds/after-sales were not implemented. It now describes the implemented request, approval, stock restoration, and completed-order point rollback flow.
- Updated the current architecture analysis and acceptance report so they no longer describe the backend Dockerfile as dependent on a prebuilt JAR, no longer list after-sales as missing, and record the current 30-test result.
- A repository scan found no remaining statement that the implemented knowledge-base tool or purchase-plan tool is “未实现” or “计划中”. Remaining “未实现” mentions describe intentionally out-of-scope items such as RAG/MCP or historical limitations.

## Runtime Health

The documentation-only changes did not require a rebuild. The existing stack remained healthy:

```text
agent-store-agent-service-1   Up (healthy)   0.0.0.0:8000->8000/tcp
agent-store-backend-1         Up (healthy)   0.0.0.0:8081->8080/tcp
agent-store-frontend-1        Up (healthy)   0.0.0.0:80->80/tcp
agent-store-mysql-1           Up (healthy)   0.0.0.0:3307->3306/tcp
```

Static validation also passed:

```text
build_knowledge_base.py syntax: OK
```

The optional admin coupon-template management UI was intentionally skipped; the existing coupon claiming and checkout flow remains unchanged and this is outside the required Tier 3 consistency work.
