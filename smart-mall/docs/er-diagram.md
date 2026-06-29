# ER Diagram

```mermaid
erDiagram
  USER ||--o{ CART : owns
  USER ||--o{ ORDER : places
  CATEGORY ||--o{ CATEGORY : contains
  CATEGORY ||--o{ PRODUCT : classifies
  PRODUCT ||--o{ CART : added_to
  ORDER ||--o{ ORDER_ITEM : includes
  PRODUCT ||--o{ ORDER_ITEM : snapshotted
  USER ||--o{ AGENT_CONVERSATION : chats
  USER ||--o{ USER_PREFERENCE : remembers

  USER {
    bigint id PK
    varchar username UK
    varchar password_hash
    varchar phone UK
    varchar avatar_url
    varchar role
    tinyint status
    datetime created_at
    datetime updated_at
  }

  CATEGORY {
    bigint id PK
    varchar name
    bigint parent_id FK
    int sort_order
  }

  PRODUCT {
    bigint id PK
    bigint category_id FK
    varchar name
    text description
    decimal price
    int stock
    int sales_count
    varchar image_url
    tinyint status
    int version
    datetime created_at
  }

  CART {
    bigint id PK
    bigint user_id FK
    bigint product_id FK
    int quantity
    datetime created_at
  }

  ORDER {
    bigint id PK
    varchar order_no UK
    bigint user_id FK
    decimal total_amount
    varchar status
    varchar shipping_address
    datetime created_at
    datetime paid_at
  }

  ORDER_ITEM {
    bigint id PK
    bigint order_id FK
    bigint product_id FK
    varchar product_name_snapshot
    decimal price_snapshot
    int quantity
  }

  AGENT_CONVERSATION {
    bigint id PK
    varchar session_id
    bigint user_id
    varchar role
    text content
    datetime created_at
  }

  USER_PREFERENCE {
    bigint id PK
    bigint user_id
    varchar preference_tag
    decimal weight
    datetime updated_at
  }
```

## 3NF 说明

- 用户、分类、商品、购物车、订单主表均以主键描述单一实体，非主属性只依赖主键。
- `order_item.product_name_snapshot` 和 `price_snapshot` 是下单时刻的历史快照，用于保证商品改名或改价后历史订单仍可审计；这是业务事实记录，不是可由当前商品表可靠推导的冗余字段。
- `product.version` 服务于乐观锁并发控制，不表达业务派生数据。
- Agent 记忆表独立于业务表，`user_preference` 用 `(user_id, preference_tag)` 唯一约束表达长期偏好权重。
