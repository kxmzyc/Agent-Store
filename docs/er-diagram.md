# 数据库 ER 图

本文档是数据库关系的简版说明。完整字段解释、索引设计、3NF 分析和事务 SQL 见 [database-design.md](database-design.md)。

```mermaid
erDiagram
  USER ||--o{ CART : owns
  USER ||--o{ ORDER : places
  USER ||--o{ AFTER_SALE_REQUEST : requests
  USER ||--o{ PRODUCT_FAVORITE : collects
  USER ||--o{ PRODUCT_VIEW_HISTORY : views
  USER ||--o{ PRODUCT_VIEW_LOG : logs
  USER ||--o{ SHIPPING_ADDRESS : owns
  USER ||--o{ SEARCH_KEYWORD_LOG : searches
  USER ||--o{ ADMIN_OPERATION_LOG : operates
  USER ||--o{ AGENT_CONVERSATION : chats
  USER ||--o{ USER_PREFERENCE : has

  CATEGORY ||--o{ CATEGORY : contains
  CATEGORY ||--o{ PRODUCT : classifies

  PRODUCT ||--o{ CART : added_to
  PRODUCT ||--o{ PRODUCT_FAVORITE : collected_by
  PRODUCT ||--o{ PRODUCT_VIEW_HISTORY : viewed_by
  PRODUCT ||--o{ PRODUCT_VIEW_LOG : logged_by
  PRODUCT ||--o{ BANNER_SLOT : promoted_by
  PRODUCT ||--o{ PRODUCT_TAG_RELATION : tagged_by
  PRODUCT ||--o{ ORDER_ITEM : ordered_as_snapshot
  PRODUCT_TAG ||--o{ PRODUCT_TAG_RELATION : maps
  ORDER ||--o{ ORDER_ITEM : includes
  ORDER ||--o{ AFTER_SALE_REQUEST : has

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

  AFTER_SALE_REQUEST {
    bigint id PK
    bigint order_id FK
    bigint user_id FK
    tinyint type
    varchar reason
    tinyint status
    decimal refund_amount
    varchar handle_remark
    datetime created_at
    datetime handled_at
  }

  PRODUCT_FAVORITE {
    bigint id PK
    bigint user_id FK
    bigint product_id FK
    datetime created_at
  }

  PRODUCT_VIEW_HISTORY {
    bigint id PK
    bigint user_id FK
    bigint product_id FK
    int view_count
    datetime created_at
    datetime last_viewed_at
  }

  PRODUCT_VIEW_LOG {
    bigint id PK
    bigint user_id
    bigint product_id FK
    datetime viewed_at
  }

  SHIPPING_ADDRESS {
    bigint id PK
    bigint user_id
    varchar receiver_name
    varchar phone
    varchar province
    varchar city
    varchar district
    varchar detail_address
    tinyint is_default
    datetime created_at
  }

  BANNER_SLOT {
    bigint id PK
    bigint product_id FK
    int sort_order
    tinyint is_active
    datetime created_at
  }

  SEARCH_KEYWORD_LOG {
    bigint id PK
    varchar keyword
    bigint user_id
    tinyint is_blocked
    datetime searched_at
  }

  PRODUCT_TAG {
    bigint id PK
    varchar name UK
  }

  PRODUCT_TAG_RELATION {
    bigint product_id PK
    bigint tag_id PK
  }

  ADMIN_OPERATION_LOG {
    bigint id PK
    bigint admin_id
    varchar action
    varchar target_type
    bigint target_id
    varchar detail
    datetime created_at
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

  KNOWLEDGE_CHUNK {
    bigint id PK
    varchar source_type
    bigint source_id
    text content
    mediumtext embedding_json
    datetime updated_at
  }
```

## 关系说明

| 关系 | 基数 | 说明 |
|---|---|---|
| `user` -> `cart` | 1:N | 一个用户可以有多个购物车项 |
| `user` -> `order` | 1:N | 一个用户可以创建多个订单 |
| `user` -> `after_sale_request` | 1:N | 一个用户可以为自己的订单提交售后申请 |
| `category` -> `category` | 1:N | 分类自关联，支持一级、二级分类 |
| `category` -> `product` | 1:N | 一个分类下有多个商品 |
| `product` -> `cart` | 1:N | 一个商品可以被多个用户加入购物车 |
| `user` -> `product_favorite` | 1:N | 一个用户可以收藏多个商品 |
| `product` -> `product_favorite` | 1:N | 一个商品可以被多个用户收藏 |
| `user` -> `product_view_history` | 1:N | 一个用户可以产生多个商品浏览足迹 |
| `product` -> `product_view_history` | 1:N | 一个商品可以被多个用户浏览 |
| `user` -> `product_view_log` | 1:N | 登录用户浏览事件记录；匿名用户 `user_id` 为空 |
| `product` -> `product_view_log` | 1:N | 一个商品可以有多条浏览事件 |
| `user` -> `shipping_address` | 1:N | 一个用户可以维护多个收货地址 |
| `product` -> `banner_slot` | 1:N | 一个商品可以被配置到多个推荐位 |
| `product` -> `product_tag_relation` | 1:N | 一个商品可以绑定多个标签 |
| `product_tag` -> `product_tag_relation` | 1:N | 一个标签可以绑定多个商品 |
| `user` -> `search_keyword_log` | 1:N | 登录搜索记录关联用户，匿名搜索可为空 |
| `user` -> `admin_operation_log` | 1:N | 管理员写操作由 AOP 记录 |
| `order` -> `order_item` | 1:N | 一个订单包含多个订单明细 |
| `order` -> `after_sale_request` | 1:N | 一个订单可产生售后记录，当前业务限制未完成售后不重复提交 |
| `product` -> `order_item` | 1:N | 订单明细引用商品，并保存下单快照 |
| `user` -> `agent_conversation` | 1:N | 一个用户可以产生多条 Agent 对话消息 |
| `user` -> `user_preference` | 1:N | 一个用户可以有多个长期偏好标签 |

`knowledge_chunk` 不声明物理外键。`source_type=product` 或 `review` 时，`source_id` 按约定指向对应商品的 ID；`source_type=faq` 时为 `NULL`。这是 Agent 知识库对主业务数据的多态逻辑引用，避免跨微服务边界建立数据库外键；知识库重建脚本和 Agent 的 `ensure_tables()` 使用同一结构。

## 3NF 说明

- 主业务表围绕单一实体建模，非主键字段只依赖本表主键。
- `cart` 不保存商品名、价格，只保存 `product_id` 和数量，展示时关联 `product`。
- `product` 只保存 `category_id`，不保存分类名称。
- `order` 不保存用户名、手机号等可由 `user_id` 查询的信息。
- `after_sale_request` 单独记录售后申请状态、原因和处理说明，不把售后状态塞进订单明细，避免订单与售后两个生命周期混在一张表里。
- `order_item.product_name_snapshot` 和 `order_item.price_snapshot` 是下单时刻的历史事实快照，商品后续改名或改价后不能再由当前 `product` 表可靠推导，因此不违反 3NF。
- `product.version` 是并发控制字段，用于乐观锁扣库存，不是业务冗余字段。
- `product_favorite` 只保存用户、商品和收藏时间，商品展示字段仍从 `product` 查询，避免冗余。
- `product_view_history` 的 `view_count` 与 `last_viewed_at` 是用户浏览行为事实，不是可由其他业务表稳定推导的字段。
- Agent 记忆表是独立边界，`user_preference` 通过 `(user_id, preference_tag)` 唯一约束表达“一个用户一个偏好标签只有一条权重记录”。
- `knowledge_chunk` 保存 FAQ、商品和评价知识块及可选 embedding 快照；`source_id` 是多态知识源标识，不是可由单一实体表约束的普通外键。
