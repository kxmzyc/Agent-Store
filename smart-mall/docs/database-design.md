# 智能商城数据库设计文档

## 1. 设计目标

本数据库支撑“淘宝 + AI 助手”迷你商城项目，核心目标是完整覆盖用户、商品、订单、搜索、AI 助手五大模块，并满足课程验收中对 MySQL、三范式、事务、并发扣库存、Agent 记忆机制的要求。

当前项目数据库名为 `smart_mall`，使用 MySQL 8.0，所有业务表使用 `InnoDB` 引擎和 `utf8mb4` 字符集。后端 Spring Boot 使用 Spring Data JPA 访问主业务表，Agent 服务使用 SQLAlchemy 访问两张专属记忆表。

## 2. 数据库边界

| 边界 | 表 | 写入方 | 说明 |
|---|---|---|---|
| 主业务数据 | `user`、`category`、`product`、`cart`、`order`、`order_item`、`product_favorite`、`product_view_history` | Spring Boot 后端 | 用户、商品、购物车、订单、收藏和浏览足迹等电商核心数据，Agent 不直接写这些表 |
| Agent 记忆数据 | `agent_conversation`、`user_preference` | FastAPI Agent 服务 | 存储对话历史和用户长期偏好，是 Agent 专属数据 |

Agent 查询商品和订单时通过后端 REST API 调用，不能直接读写订单、商品、用户业务表。这样可以保证权限边界清晰，避免 Agent 绕过主系统鉴权和业务校验。

## 3. ER 图

![hatGPT Image 2026年7月1日 18_48_3](C:\Users\kxm\Downloads\ChatGPT Image 2026年7月1日 18_48_34.png)

## 4. 表清单

| 表名 | 中文名 | 所属模块 | 主要用途 |
|---|---|---|---|
| `user` | 用户表 | 用户模块 | 存储账号、密码哈希、手机号、角色、状态 |
| `category` | 商品分类表 | 商品模块 | 支持一级、二级商品分类 |
| `product` | 商品表 | 商品模块、搜索模块、订单模块 | 存储商品基础信息、价格、库存、销量、乐观锁版本号 |
| `cart` | 购物车表 | 订单模块 | 存储用户待结算商品 |
| `order` | 订单主表 | 订单模块 | 存储订单号、用户、总金额、状态、地址、支付时间 |
| `order_item` | 订单明细表 | 订单模块 | 存储订单商品行和下单时的商品名、价格快照 |
| `product_favorite` | 商品收藏表 | 用户体验增强 | 存储用户收藏商品，支撑心愿单和个人中心 |
| `product_view_history` | 商品浏览足迹表 | 用户体验增强 | 存储用户最近浏览商品、浏览次数和最近浏览时间 |
| `agent_conversation` | Agent 对话表 | AI 助手模块 | 持久化保存会话消息，作为短期记忆恢复兜底 |
| `user_preference` | 用户偏好表 | AI 助手模块 | 存储长期偏好标签和权重 |

## 5. 表结构详情

### 5.1 `user` 用户表

用途：存储商城用户账号，是认证、购物车、订单和 Agent 记忆的用户主体。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 用户主键 |
| `username` | `VARCHAR(32)` | NOT NULL，UNIQUE | 登录用户名 |
| `password_hash` | `VARCHAR(100)` | NOT NULL | BCrypt 密码哈希，禁止存明文或 MD5 |
| `phone` | `VARCHAR(20)` | UNIQUE | 手机号，可为空；填写后必须唯一 |
| `avatar_url` | `VARCHAR(255)` | 可空 | 用户头像地址 |
| `role` | `VARCHAR(16)` | NOT NULL，默认 `USER` | 角色：`USER` 普通用户，`ADMIN` 管理员 |
| `status` | `TINYINT` | NOT NULL，默认 `1` | 账号状态：`1` 正常，`0` 禁用 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | NOT NULL，自动更新时间 | 更新时间 |

关键约束：

- `uk_user_username`：保证用户名唯一。
- `uk_user_phone`：保证手机号唯一。

设计说明：

- 密码只保存 BCrypt 哈希，不保存明文密码，符合登录安全要求。
- `role` 使用简单两档权限，满足实训项目中的普通用户和管理员区分，不引入复杂 RBAC。
- `status` 用于禁用账号，不删除用户，避免历史订单失去用户引用。

### 5.2 `category` 商品分类表

用途：存储商品分类，支持二级分类和前端分类侧边栏展示。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 分类主键 |
| `name` | `VARCHAR(50)` | NOT NULL | 分类名称 |
| `parent_id` | `BIGINT` | FK，可空 | 父分类 ID；为空表示一级分类 |
| `sort_order` | `INT` | 默认 `0` | 分类展示排序 |

关键约束：

- `fk_category_parent`：`parent_id` 引用 `category(id)`。
- `idx_category_parent`：加速按父分类查子分类。

设计说明：

- 使用自关联实现二级分类，不拆分一级分类表和二级分类表，结构更稳定。
- 当前业务按 `sort_order ASC, id ASC` 排序，和 `CategoryRepository.findAllByOrderBySortOrderAscIdAsc()` 一致。

### 5.3 `product` 商品表

用途：存储商品基础信息，是商品浏览、搜索、购物车、下单扣库存、AI 导购推荐的核心表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 商品主键 |
| `category_id` | `BIGINT` | NOT NULL，FK | 所属分类 |
| `name` | `VARCHAR(128)` | NOT NULL | 商品名称 |
| `description` | `TEXT` | 可空 | 商品描述，参与搜索 |
| `price` | `DECIMAL(10,2)` | NOT NULL | 商品当前售价 |
| `stock` | `INT` | NOT NULL，默认 `0` | 当前库存 |
| `sales_count` | `INT` | NOT NULL，默认 `0` | 销量，用于排序和运营展示 |
| `image_url` | `VARCHAR(255)` | 可空 | 商品图片 URL |
| `status` | `TINYINT` | NOT NULL，默认 `1` | 商品状态：`1` 上架，`0` 下架 |
| `version` | `INT` | NOT NULL，默认 `0` | 乐观锁版本号，用于并发扣库存 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 商品创建时间 |

关键约束和索引：

- `fk_product_category`：`category_id` 引用 `category(id)`。
- `idx_product_category_status`：加速分类列表查询。
- `idx_product_status_sales`：加速首页、搜索结果按销量排序。
- `idx_product_status_stock`：加速后台低库存统计。
- `ft_name_desc`：`name`、`description` 全文索引。当前后端代码使用 `LIKE` 兜底搜索，该索引用于 MySQL ngram 搜索扩展和答辩说明。

设计说明：

- `stock` 是当前库存，下单时被事务扣减。
- `version` 是并发控制字段，不是业务派生数据；下单 SQL 使用 `stock >= ? AND version = ?` 防止超卖和旧版本重复扣减。
- 删除商品采用逻辑下架，即更新 `status = 0`，避免历史订单明细中的 `product_id` 外键失效。

### 5.4 `cart` 购物车表

用途：存储用户加入购物车但尚未结算的商品。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 购物车项主键 |
| `user_id` | `BIGINT` | NOT NULL，FK | 用户 ID |
| `product_id` | `BIGINT` | NOT NULL，FK | 商品 ID |
| `quantity` | `INT` | NOT NULL，默认 `1` | 购买数量 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 加入购物车时间 |

关键约束和索引：

- `uk_cart_user_product`：同一用户同一商品只能有一条购物车记录。
- `fk_cart_user`：`user_id` 引用 `user(id)`。
- `fk_cart_product`：`product_id` 引用 `product(id)`。
- `idx_cart_user_created`：加速按用户查看购物车并按加入时间排序。

设计说明：

- 同一用户重复加入同一商品时，后端合并数量，而不是插入多条记录。
- 购物车不保存商品名称和价格，展示时实时关联 `product`，避免产生可由商品表推导的冗余字段。

### 5.5 `order` 订单主表

用途：存储订单级信息，包括业务订单号、用户、总金额、状态和收货地址。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 订单主键 |
| `order_no` | `VARCHAR(32)` | NOT NULL，UNIQUE | 业务订单号，不暴露自增 ID |
| `user_id` | `BIGINT` | NOT NULL，FK | 下单用户 |
| `total_amount` | `DECIMAL(10,2)` | NOT NULL | 订单总金额 |
| `status` | `VARCHAR(20)` | NOT NULL，默认 `PENDING_PAYMENT` | 订单状态 |
| `shipping_address` | `VARCHAR(255)` | NOT NULL | 收货地址 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 下单时间 |
| `paid_at` | `DATETIME` | 可空 | 模拟支付成功时间 |

关键约束和索引：

- `uk_order_no`：业务订单号唯一。
- `fk_order_user`：`user_id` 引用 `user(id)`。
- `idx_order_user_created`：加速用户订单列表查询。
- `idx_order_status_created`：加速后台按状态查看订单。

状态机：

```text
PENDING_PAYMENT --支付--> PAID --发货--> SHIPPED --确认收货--> COMPLETED
PENDING_PAYMENT --取消--> CANCELLED
```

设计说明：

- `total_amount` 是下单时根据订单明细汇总后保存的金额，作为订单结算事实保留，不随商品价格变化而改变。
- 当前项目没有真实支付流水表，因为实训范围只要求模拟支付状态流转。
- 取消订单只允许从 `PENDING_PAYMENT` 取消；取消后后端会根据 `order_item` 回补库存。

### 5.6 `order_item` 订单明细表

用途：存储订单中的商品行项目，并保存下单时刻的商品名称和价格快照。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 订单明细主键 |
| `order_id` | `BIGINT` | NOT NULL，FK | 所属订单 |
| `product_id` | `BIGINT` | NOT NULL，FK | 商品 ID |
| `product_name_snapshot` | `VARCHAR(128)` | NOT NULL | 下单时商品名称快照 |
| `price_snapshot` | `DECIMAL(10,2)` | NOT NULL | 下单时商品单价快照 |
| `quantity` | `INT` | NOT NULL | 购买数量 |

关键约束和索引：

- `fk_order_item_order`：`order_id` 引用 `order(id)`。
- `fk_order_item_product`：`product_id` 引用 `product(id)`。
- `idx_order_item_order`：加速查询订单详情。
- `idx_order_item_product`：支持按商品分析订单明细。

设计说明：

- `product_name_snapshot` 和 `price_snapshot` 是有意保存的历史事实，不是设计失误。商品后续改名或改价后，历史订单展示仍必须反映下单时的名称和价格。
- 不把商品图片、分类等展示字段复制到订单明细，避免不必要冗余。

### 5.7 `product_favorite` 商品收藏表

用途：存储用户收藏商品，支撑商品详情页收藏状态、收藏列表和个人中心近期收藏。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 收藏记录主键 |
| `user_id` | `BIGINT` | NOT NULL，FK | 用户 ID |
| `product_id` | `BIGINT` | NOT NULL，FK | 商品 ID |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 收藏时间 |

关键约束和索引：

- `uk_favorite_user_product`：同一用户同一商品只能收藏一次。
- `idx_favorite_user_created`：加速用户收藏列表按时间倒序展示。
- `fk_favorite_user`、`fk_favorite_product`：保证收藏记录引用有效用户和商品。

设计说明：

- 不复制商品名、价格、图片等展示字段，展示时关联 `product`，符合 3NF。
- 商品下架后收藏记录仍可保留，前端列表默认只展示在售商品，避免破坏用户行为记录。

### 5.8 `product_view_history` 商品浏览足迹表

用途：记录登录用户访问商品详情页的行为，支撑个人中心最近浏览和用户行为闭环展示。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 足迹记录主键 |
| `user_id` | `BIGINT` | NOT NULL，FK | 用户 ID |
| `product_id` | `BIGINT` | NOT NULL，FK | 商品 ID |
| `view_count` | `INT` | NOT NULL，默认 `1` | 同一用户浏览该商品次数 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 首次浏览时间 |
| `last_viewed_at` | `DATETIME` | NOT NULL，默认当前时间 | 最近浏览时间 |

关键约束和索引：

- `uk_view_history_user_product`：同一用户同一商品只保留一条足迹记录，重复浏览累加次数并更新最近时间。
- `idx_view_history_user_last`：加速用户最近浏览列表。
- `fk_view_history_user`、`fk_view_history_product`：保证足迹引用有效用户和商品。

设计说明：

- `view_count` 和 `last_viewed_at` 是用户行为事实，不是从商品或订单表可稳定推导的冗余字段。
- 浏览足迹由后端在登录态访问商品详情时自动写入，未登录游客不记录。

### 5.9 `agent_conversation` Agent 对话表

用途：存储 AI 助手用户对话记录，作为短期记忆恢复兜底。Agent 服务内存中保留最近若干轮对话，服务重启后可从此表恢复最近消息。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 消息主键 |
| `session_id` | `VARCHAR(64)` | NOT NULL | 会话 ID |
| `user_id` | `BIGINT` | NOT NULL | 用户 ID |
| `role` | `VARCHAR(10)` | NOT NULL | 消息角色：`user` 或 `assistant` |
| `content` | `TEXT` | NOT NULL | 消息内容 |
| `created_at` | `DATETIME` | NOT NULL，默认当前时间 | 消息创建时间 |

关键索引：

- `idx_agent_conversation_session`：按 `session_id` 恢复会话。
- `idx_agent_conversation_user_session`：按用户和会话恢复最近消息，符合当前 Agent 查询逻辑。

设计说明：

- 该表由 Agent 服务写入，不由 Spring Boot 主后端写入。
- 当前项目按 `session_id + user_id` 隔离会话，避免用户之间串话。
- 不设置外键到 `user(id)`，是为了让 Agent 表保持独立微服务边界；逻辑上的用户合法性由 Agent 调用主后端 `/api/user/profile` 校验 JWT 保证。

### 5.10 `user_preference` 用户偏好表

用途：存储 Agent 从用户对话中提炼出的长期偏好标签，用于后续会话推荐商品。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK，自增 | 偏好主键 |
| `user_id` | `BIGINT` | NOT NULL | 用户 ID |
| `preference_tag` | `VARCHAR(50)` | NOT NULL | 偏好标签，例如“机械键盘”“预算敏感” |
| `weight` | `DECIMAL(3,2)` | NOT NULL，默认 `1.00` | 偏好权重，当前逻辑封顶 `2.00` |
| `updated_at` | `DATETIME` | NOT NULL，自动更新时间 | 最近更新时间 |

关键约束和索引：

- `uk_user_preference_tag`：同一用户同一标签只能有一条记录。
- `idx_user_preference_user_weight`：加速读取用户权重最高的偏好标签。

设计说明：

- Agent 每次对话结束后提取偏好标签，并对 `(user_id, preference_tag)` 执行 upsert。
- 已存在标签时 `weight = LEAST(2.0, weight + 0.1)`，用于体现偏好强度。
- 不设置外键到 `user(id)`，原因同 `agent_conversation`：保持 Agent 专属表边界，由应用层校验用户身份。

## 6. 索引设计汇总

| 表 | 索引/约束 | 字段 | 类型 | 目的 |
|---|---|---|---|---|
| `user` | `uk_user_username` | `username` | 唯一索引 | 登录、注册唯一性校验 |
| `user` | `uk_user_phone` | `phone` | 唯一索引 | 手机号唯一性校验 |
| `category` | `idx_category_parent` | `parent_id` | 普通索引 | 查询子分类 |
| `product` | `idx_product_category_status` | `category_id,status` | 普通索引 | 分类商品列表 |
| `product` | `idx_product_status_sales` | `status,sales_count,id` | 普通索引 | 上架商品按销量排序 |
| `product` | `idx_product_status_stock` | `status,stock` | 普通索引 | 后台低库存统计 |
| `product` | `ft_name_desc` | `name,description` | 全文索引 | 商品全文搜索扩展 |
| `cart` | `uk_cart_user_product` | `user_id,product_id` | 唯一索引 | 避免重复购物车项 |
| `cart` | `idx_cart_user_created` | `user_id,created_at` | 普通索引 | 用户购物车列表 |
| `order` | `uk_order_no` | `order_no` | 唯一索引 | 业务订单号唯一 |
| `order` | `idx_order_user_created` | `user_id,created_at` | 普通索引 | 用户订单列表 |
| `order` | `idx_order_status_created` | `status,created_at` | 普通索引 | 后台订单列表 |
| `order_item` | `idx_order_item_order` | `order_id` | 普通索引 | 订单详情 |
| `order_item` | `idx_order_item_product` | `product_id` | 普通索引 | 商品维度订单分析 |
| `product_favorite` | `uk_favorite_user_product` | `user_id,product_id` | 唯一索引 | 避免重复收藏 |
| `product_favorite` | `idx_favorite_user_created` | `user_id,created_at` | 普通索引 | 用户收藏列表 |
| `product_view_history` | `uk_view_history_user_product` | `user_id,product_id` | 唯一索引 | 合并同一用户同一商品足迹 |
| `product_view_history` | `idx_view_history_user_last` | `user_id,last_viewed_at` | 普通索引 | 用户最近浏览列表 |
| `agent_conversation` | `idx_agent_conversation_session` | `session_id` | 普通索引 | 会话历史恢复 |
| `agent_conversation` | `idx_agent_conversation_user_session` | `user_id,session_id,id` | 普通索引 | 用户会话隔离查询 |
| `user_preference` | `uk_user_preference_tag` | `user_id,preference_tag` | 唯一索引 | 偏好 upsert |
| `user_preference` | `idx_user_preference_user_weight` | `user_id,weight,updated_at` | 普通索引 | 读取 Top 偏好 |

## 7. 搜索设计

当前后端 `ProductRepository.search()` 使用 `LIKE` 兜底查询：

```sql
SELECT *
FROM product
WHERE status = 1
  AND (
    LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%'))
    OR LOWER(description) LIKE LOWER(CONCAT('%', :keyword, '%'))
  )
ORDER BY sales_count DESC, id DESC
LIMIT :size OFFSET :offset;
```

同时数据库脚本提供 `FULLTEXT(name, description) WITH PARSER ngram` 索引，便于后续切换为 MySQL 中文全文搜索：

```sql
SELECT *
FROM product
WHERE status = 1
  AND MATCH(name, description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)
ORDER BY sales_count DESC, id DESC;
```

选择说明：

- `LIKE` 版本实现简单，和当前代码完全一致，适合实训演示。
- `FULLTEXT + ngram` 保留在 DDL 中，体现搜索模块的数据库能力，后续可平滑升级。

## 8. 下单事务与并发扣库存

下单是本项目最重要的数据库事务场景。当前 `OrderService.create()` 和 `OrderService.createDirect()` 使用 `@Transactional`，在同一事务中完成库存扣减、订单插入、订单明细插入、购物车清理。

核心扣库存 SQL 逻辑如下：

```sql
UPDATE product
SET stock = stock - :quantity,
    version = version + 1,
    sales_count = sales_count + :quantity
WHERE id = :id
  AND stock >= :quantity
  AND version = :version;
```

该 SQL 同时提供两层保护：

- `stock >= :quantity`：保证库存不足时不扣减，避免负库存。
- `version = :version`：乐观锁校验，避免并发请求基于旧库存版本重复成功。

如果影响行数为 `0`，后端判定为库存不足或版本冲突，抛出业务异常。由于方法有 `@Transactional`，订单主表、订单明细、购物车清理等操作会整体回滚。

取消订单时，当前项目只允许取消 `PENDING_PAYMENT` 状态订单，并回补库存：

```sql
UPDATE product
SET stock = stock + :quantity,
    version = version + 1,
    sales_count = CASE
      WHEN sales_count >= :quantity THEN sales_count - :quantity
      ELSE 0
    END
WHERE id = :id;
```

## 9. 3NF 范式说明

### 9.1 符合 1NF

所有表字段均为原子值：

- 用户手机号、角色、状态独立存储。
- 商品名称、描述、价格、库存独立存储。
- 订单主表只保存订单级字段，订单商品拆到 `order_item`。
- Agent 偏好标签每条一行，不在一个字段中存逗号分隔标签。

### 9.2 符合 2NF

所有表都使用单列自增主键，非主键字段完全依赖主键，不存在联合主键下的部分依赖问题。

`cart` 和 `user_preference` 虽然有业务唯一键，但主键仍是 `id`。例如：

- `cart.quantity` 依赖购物车项 `id`。
- `user_preference.weight` 依赖偏好记录 `id`，业务上由 `(user_id, preference_tag)` 唯一确定。

### 9.3 符合 3NF

非主键字段不依赖其他非主键字段：

- `cart` 不保存商品名称、价格，避免从 `product_id` 推导出的冗余字段。
- `product` 只保存 `category_id`，不保存分类名称，分类名称从 `category` 查询。
- `order` 不保存用户名、手机号，用户信息从 `user_id` 关联。
- `order_item` 保存 `product_name_snapshot`、`price_snapshot` 是历史事实快照，不是当前商品表可可靠推导的数据。商品后续改名或改价后，历史订单仍应展示下单时的信息。
- `product_favorite` 不保存商品展示字段，收藏页实时关联商品表。
- `product_view_history` 只保存浏览行为事实和商品外键，不复制商品字段。

## 10. 初始化数据设计

`docs/seed.sql` 包含：

- 3 个测试用户：
  - `admin / 123456`，角色 `ADMIN`
  - `alice / 123456`，角色 `USER`
  - `bob / 123456`，角色 `USER`
- 20 个分类，覆盖数码、办公、家居、服饰、美妆、运动、图书文创等。
- 45 个商品测试数据，满足“至少 20 条商品数据”的要求。
- 1 个购物车示例，方便登录后直接演示购物车。
- 1 个已支付订单和订单明细，方便 Agent 演示“我上次买的东西到哪了”。
- 3 条收藏记录和 4 条浏览足迹，方便演示个人中心、收藏页和用户行为闭环。
- 1 条用户偏好数据，方便演示长期记忆读取。
- 1 个抢购测试商品，库存为 80，配合并发测试脚本验证不会超卖。

## 11. SQL 文件说明

| 文件 | 用途 |
|---|---|
| `docs/schema.sql` | 纯数据库结构脚本，只建库、建表、建索引，不插入测试数据 |
| `docs/seed.sql` | Docker Compose 初始化脚本，包含建表、索引和测试数据 |
| `docs/database-design.md` | 本数据库设计文档 |
|  |  |

## 12. 常用验收 SQL

查看用户密码是否为 BCrypt：

```sql
SELECT id, username, password_hash
FROM `user`
WHERE username IN ('admin', 'alice', 'bob');
```

查看商品和库存：

```sql
SELECT id, name, stock, sales_count, version
FROM product
WHERE id = 21;
```

查看订单及明细：

```sql
SELECT o.id, o.order_no, o.status, o.total_amount, o.created_at, oi.product_name_snapshot, oi.price_snapshot, oi.quantity
FROM `order` o
JOIN order_item oi ON oi.order_id = o.id
WHERE o.user_id = 2
ORDER BY o.created_at DESC;
```

查看 Agent 长期偏好：

```sql
SELECT user_id, preference_tag, weight, updated_at
FROM user_preference
WHERE user_id = 2
ORDER BY weight DESC, updated_at DESC;
```

查看用户收藏和浏览足迹：

```sql
SELECT f.user_id, p.name, f.created_at
FROM product_favorite f
JOIN product p ON p.id = f.product_id
WHERE f.user_id = 2
ORDER BY f.created_at DESC;

SELECT h.user_id, p.name, h.view_count, h.last_viewed_at
FROM product_view_history h
JOIN product p ON p.id = h.product_id
WHERE h.user_id = 2
ORDER BY h.last_viewed_at DESC;
```

查看某个会话历史：

```sql
SELECT role, content, created_at
FROM agent_conversation
WHERE user_id = 2 AND session_id = 'demo-session'
ORDER BY id ASC;
```

并发抢购验收后检查是否超卖：

```sql
SELECT id, name, stock, sales_count, version
FROM product
WHERE id = 21;

SELECT COUNT(*) AS success_orders
FROM order_item
WHERE product_id = 21;
```
