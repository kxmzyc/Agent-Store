# AGENTS.md — 智能商城（淘宝 + AI 助手 微型生态闭环）

> 本文档是面向 Codex CLI（`--full-auto`）/ Claude Code 的执行规格文档。
> 来源：《大模型与软件工程的企业工程实践》教学大纲 + Day1实训PPT（智能商城最终项目定义）。
> 团队规模假设：3-4人，15个实训工作日。
> 执行原则：**先打通主链路再优化细节，不做大纲未要求的基础设施。**

---

## 0. 项目背景

这是一个企业实训最终项目，不是生产级商业系统。验收标准是"五大模块功能可演示 + 技术栈全部落地 + 答辩能讲清楚架构和Agent工作原理"，不是"扛得住双十一流量"。

项目定位（原文）：构建"淘宝 + AI 助手"的迷你版系统，打通电商业务全链路，实现从0到1的产品落地。商城只是载体，真正目标是通过实战掌握从需求分析、技术选型到系统开发、部署上线的全流程软件工程思维。

**五大核心模块（验收时逐一核对，缺一不可）：**

1. 用户模块 — 注册登录、权限认证、个人信息中心
2. 商品模块 — 商品全流程（增删改查、分类、库存）
3. 订单模块 — 购物车、下单、支付状态、订单全流程
4. 搜索模块 — 商品模糊搜索、排序、分页
5. AI智能推荐助手模块 — 智能导购 + 个性化推荐（LangChain Agent，工具调用 + 记忆机制）

**必须在最终答辩中可演示的技术栈清单（来自教学大纲，逐项打勾，不允许缺项）：**

| 类别 | 必须使用的技术 | 验证方式 |
|---|---|---|
| 前端 | HTML/CSS/JavaScript + Vue3 + AJAX(axios) | 页面可跑，能看到组件化代码 |
| 后端 | Spring Boot + RESTful API设计规范 | Swagger/Postman可调通全部接口 |
| 认证 | JWT令牌认证 | 未登录访问受保护接口返回401 |
| 事务 | Spring `@Transactional` 事务管理 | 下单并发测试不超卖 |
| 数据库 | MySQL + 三范式设计 | 提供ER图与建表SQL，无冗余字段 |
| AI辅助开发 | Claude Code 全程参与编码 | 保留关键模块的Claude Code生成记录/对话截图 |
| 智能体框架 | LangChain，工具调用 + 短期/长期记忆机制 | Agent能调用至少2个工具，能跨对话记住用户偏好 |
| DevOps | Git版本控制 + Docker容器化部署 + 应用监控与日志管理 | `docker-compose up` 一键启动全部服务，能查看容器日志 |

---

## 1. 整体架构

采用**前后端分离 + 独立Agent微服务**架构，三个独立可部署单元：

```
┌─────────────┐      REST API(JWT)      ┌──────────────────┐
│  Vue3 前端   │ ───────────────────────▶│  Spring Boot 后端  │
│ (Nginx静态)  │ ◀───────────────────────│  (主业务系统)      │
└─────────────┘                          └──────────┬────────┘
       │                                             │ JDBC
       │  HTTP (AI对话窗)                              ▼
       ▼                                      ┌──────────────┐
┌─────────────────┐    内部服务调用(回访)         │    MySQL     │
│ LangChain Agent  │ ───────────────────────▶ │  (唯一数据源)  │
│ 服务(FastAPI/Py) │ ◀─────────────────────── └──────────────┘
└─────────────────┘   (查商品/查订单/写偏好)
```

**关键架构决策（必须遵守，不要自由发挥）：**

- Agent服务是**独立微服务**，语言用 Python（FastAPI + LangChain），不要把LangChain逻辑塞进Spring Boot。这是为了让"Agent与现有系统集成"这个考核点有清晰边界。
- Agent服务**不直接连MySQL写业务表**（订单、商品、用户表只能由主后端写），Agent只能通过调用主后端暴露的REST API来读写数据，长期记忆表（用户偏好）例外，可以由Agent服务直接读写（因为这张表是Agent专属的）。
- 前端只对接两个后端：主业务API（端口8080）和Agent对话API（端口8000），自己不直连数据库。
- 所有服务必须能用 `docker-compose up -d` 一键拉起，包括 MySQL。

**目录结构（在项目根目录下创建）：**

```
smart-mall/
├── frontend/                 # Vue3项目
├── backend/                   # Spring Boot项目
├── agent-service/             # FastAPI + LangChain项目
├── docker-compose.yml
├── docs/
│   ├── er-diagram.md          # 数据库ER图(mermaid)
│   ├── api-spec.md            # 接口文档
│   └── architecture.md        # 架构说明(给答辩用)
└── README.md
```

---

## 2. 数据库设计（完整建表要求）

**约束：所有表必须满足第三范式（3NF），不允许出现可由其他字段推导的冗余列（例如订单表里存商品名称——除非明确标注为"价格快照"用途）。所有表使用 `InnoDB` 引擎、`utf8mb4` 字符集。**

### 2.1 用户模块表

```sql
CREATE TABLE `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,   -- BCrypt加密，不允许存明文或MD5
  phone VARCHAR(20) UNIQUE,
  avatar_url VARCHAR(255),
  role VARCHAR(16) NOT NULL DEFAULT 'USER',  -- USER / ADMIN
  status TINYINT NOT NULL DEFAULT 1,         -- 1正常 0禁用
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.2 商品模块表

```sql
CREATE TABLE category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  parent_id BIGINT DEFAULT NULL,         -- 支持二级分类，NULL表示顶级分类
  sort_order INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  sales_count INT NOT NULL DEFAULT 0,
  image_url VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,     -- 1上架 0下架
  version INT NOT NULL DEFAULT 0,        -- 乐观锁，防止并发超卖
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全文索引用于模糊搜索（搜索模块依赖）
ALTER TABLE product ADD FULLTEXT INDEX ft_name_desc (name, description) WITH PARSER ngram;
```

### 2.3 订单模块表

```sql
CREATE TABLE cart (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (user_id, product_id),  -- 同一用户同一商品只能有一条购物车记录
  FOREIGN KEY (user_id) REFERENCES `user`(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL UNIQUE,   -- 业务订单号，如时间戳+随机数，不暴露自增id
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
  -- 状态机: PENDING_PAYMENT → PAID → SHIPPED → COMPLETED
  --                       └────────────────→ CANCELLED
  shipping_address VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME DEFAULT NULL,
  FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(128) NOT NULL,  -- 下单时刻的商品名快照（允许的非规范化，原因：商品后续改名不应影响历史订单展示）
  price_snapshot DECIMAL(10,2) NOT NULL,         -- 下单时刻的价格快照（同理）
  quantity INT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES `order`(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.4 AI助手模块表（Agent服务专属，体现"长期记忆"）

```sql
CREATE TABLE agent_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(10) NOT NULL,            -- user / assistant
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 用途：短期记忆的持久化兜底（内存里也会缓存当前session，这张表保证服务重启不丢上下文）

CREATE TABLE user_preference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  preference_tag VARCHAR(50) NOT NULL,   -- 如 "机械键盘" "预算敏感" "数码产品"
  weight DECIMAL(3,2) NOT NULL DEFAULT 1.0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tag (user_id, preference_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 用途：长期记忆。Agent每次对话结束后提炼偏好写入此表，下次对话开场查出来注入system prompt
```

### 2.5 数据库验收标准

- [ ] 提供 `docs/er-diagram.md`，用 mermaid `erDiagram` 语法画出全部表及外键关系
- [ ] 每张表能说出"为什么不违反3NF"（特别是 order_item 的两个快照字段要能解释清楚为什么是合理的非规范化，而不是设计失误）
- [ ] product表的乐观锁 `version` 字段必须在下单扣库存逻辑中真实使用
- [ ] 提供至少20条商品测试数据、3个分类、3个测试用户的seed SQL脚本

---

## 3. 用户模块

### 3.1 需求

实现注册、登录、JWT鉴权、个人信息管理。这是整个系统的鉴权基石，其他所有模块的"需要登录才能操作"都依赖这个模块产出的JWT校验机制。

### 3.2 技术要求

- 密码使用 **BCrypt**（Spring Security的`BCryptPasswordEncoder`），`strength`参数用默认10即可，不要为了"看起来更安全"调到15+（实训环境会明显拖慢响应，没有必要）。
- JWT用 `io.jsonwebtoken:jjwt` 库实现，Access Token有效期2小时，Refresh Token有效期7天，密钥从环境变量读取（不要硬编码在代码里）。
- 实现一个 `JwtAuthenticationFilter`（继承`OncePerRequestFilter`），拦截所有 `/api/**` 路径，白名单排除：`/api/auth/**`、`/api/products/**`（GET方法，允许游客浏览商品）、`/api/products/search`。
- 校验通过后将 `userId` 存入 `SecurityContextHolder` 或自定义 `ThreadLocal`，后续Controller通过 `@AuthenticationPrincipal` 或自定义注解 `@CurrentUser` 取值，**不要让每个Controller自己重复解析Token**。

### 3.3 接口规格

```
POST /api/auth/register
请求: { "username": "string", "password": "string", "phone": "string" }
响应: 201 { "userId": 1, "username": "..." }
校验: username/phone唯一性冲突返回409，密码长度<6位返回400

POST /api/auth/login
请求: { "username": "string", "password": "string" }
响应: 200 { "accessToken": "...", "refreshToken": "...", "userInfo": {...} }
校验: 用户不存在或密码错误统一返回401 "用户名或密码错误"（不要分别提示，防止用户名枚举）

POST /api/auth/refresh
请求: { "refreshToken": "string" }
响应: 200 { "accessToken": "..." }

GET /api/user/profile     [需JWT]
响应: 200 { "id":1, "username":"...", "phone":"...", "avatarUrl":"...", "createdAt":"..." }

PUT /api/user/profile     [需JWT]
请求: { "phone": "string", "avatarUrl": "string" }
响应: 200 更新后的用户信息
```

### 3.4 前端要求

- 注册页、登录页、个人中心页三个独立路由
- 使用 Vue3 `<script setup>` 组合式API风格（不要用Options API，和大纲"主流前端框架"的现代实践对齐）
- Token存储用 `localStorage`，axios请求拦截器统一注入 `Authorization: Bearer {token}` header
- axios响应拦截器统一处理401（自动跳转登录页或尝试refresh），不要在每个组件里单独写错误处理

### 3.5 验收标准

- [ ] 未携带Token访问 `/api/user/profile` 返回401
- [ ] 注册重复用户名返回409且前端有友好提示（不是裸露的JSON错误）
- [ ] 登录成功后localStorage能看到token，刷新页面登录状态保持
- [ ] 密码字段在数据库中确认是BCrypt哈希格式（`$2a$10$...`），不是明文

### 3.6 不在本模块范围内

- 不做手机验证码登录（大纲未要求，纯增加工作量）
- 不做OAuth第三方登录（微信/QQ登录）
- 不做RBAC复杂权限系统，role字段USER/ADMIN两档够用

---

## 4. 商品模块（含搜索）

### 4.1 需求

商品的增删改查、分类浏览、模糊搜索、排序分页。这个模块的数据是订单模块和AI助手模块共同依赖的核心资源，要最先开发并稳定下来。

### 4.2 技术要求

- 列表查询统一用 `PageHelper`（MyBatis分页插件）或 Spring Data JPA 的 `Pageable`，二选一，团队统一，不要混用两套分页方案。
- 模糊搜索：中文场景下 `MATCH...AGAINST` 配合 `ngram` 解析器（已在2.2节DDL中体现），如果团队对ngram配置没信心，**允许降级**为 `LIKE CONCAT('%',?,'%')` + 按 `sales_count DESC` 排序兜底，这是教学大纲允许范围内的简化，不要为了上Elasticsearch而超出"MySQL"的技术栈约束。
- 商品上传图片：实训环境不需要对接OSS，本地存到后端的`/uploads`目录并用Nginx或SpringBoot静态资源映射对外提供即可。

### 4.3 接口规格

```
GET /api/categories
响应: 200 [{ "id":1, "name":"数码电子", "children":[...] }]

GET /api/products?categoryId=1&page=1&size=20&sort=sales_desc
响应: 200 { "total":120, "list":[{ "id":1,"name":"...","price":99.00,"stock":50,"imageUrl":"..." }] }

GET /api/products/search?keyword=机械键盘&page=1&size=20
响应: 同上结构

GET /api/products/{id}
响应: 200 商品详情完整字段

POST /api/products        [需JWT，需ADMIN角色]
PUT  /api/products/{id}   [需JWT，需ADMIN角色]
DELETE /api/products/{id} [需JWT，需ADMIN角色]
```

### 4.4 前端要求

- 商品列表页：分类侧边栏 + 商品网格卡片 + 顶部搜索框 + 分页器
- 商品详情页：图片、价格、库存、"加入购物车"按钮、"立即购买"按钮
- 管理后台简单页面（仅ADMIN可见）：商品增删改表单，不需要做得多花哨，一个简单的表格+表单弹窗即可

### 4.5 验收标准

- [ ] 搜索"键盘"能搜到名称或描述含"键盘"的商品
- [ ] 普通用户调用 `POST /api/products` 返回403
- [ ] 分页参数边界测试：page超出最大页返回空列表而不是报错
- [ ] 库存为0的商品前端显示"已售罄"且无法加入购物车

### 4.6 不在本模块范围内

- 不做商品评价/评论系统（大纲五大模块未提及，属于范围外加项）
- 不做商品多规格/SKU（颜色、尺码组合），统一当作单一SKU商品处理，降低复杂度
- 不接入真实的Elasticsearch（明确超出大纲MySQL技术栈范围）

---

## 5. 订单模块

### 5.1 需求

购物车管理、下单结算、订单状态流转、并发安全的库存扣减。这是PPT里"100人订红烧肉只有80份"案例对应的真实落地模块，**并发安全是本模块的验收重点，不是可选项**。

### 5.2 技术要求（核心，必须严格遵守）

下单是整个项目里**唯一必须用 `@Transactional` 包裹的复合操作**，必须同时完成：

1. 校验并扣减库存（`UPDATE product SET stock = stock - ?, version = version + 1 WHERE id = ? AND stock >= ? AND version = ?`，这条SQL同时利用了"原子UPDATE条件"和"乐观锁version校验"两层保护，缺一不可）
2. 插入 `order` 记录
3. 插入 `order_item` 记录（带价格快照）
4. 清空对应购物车项

任意一步失败（尤其是第1步影响行数为0，说明库存不足或版本冲突），整个事务必须回滚，并向前端返回明确的"库存不足，请重新下单"提示，**不要让用户看到裸异常堆栈**。

订单状态机严格按以下流转，不允许跳转到非法状态：

```
PENDING_PAYMENT --(支付成功)--> PAID --(发货)--> SHIPPED --(确认收货)--> COMPLETED
PENDING_PAYMENT --(取消/超时)--> CANCELLED
```

支付环节**不接入真实第三方支付**（支付宝/微信支付需要企业资质，超出实训范围），用一个"模拟支付"按钮，点击后直接把订单状态从 `PENDING_PAYMENT` 改为 `PAID`，这是教学大纲范围内的合理简化，**不要在这里浪费时间接支付宝沙箱**。

### 5.3 接口规格

```
GET  /api/cart                    [需JWT]
POST /api/cart                    [需JWT]  { "productId":1, "quantity":2 }
PUT  /api/cart/{id}                [需JWT]  { "quantity":3 }
DELETE /api/cart/{id}              [需JWT]

POST /api/orders                  [需JWT]
请求: { "cartItemIds":[1,2], "shippingAddress":"..." }
响应: 201 { "orderId":1, "orderNo":"...", "totalAmount":199.00 }
失败: 400 { "msg":"商品「机械键盘」库存不足" }

GET  /api/orders?status=all       [需JWT]
GET  /api/orders/{id}             [需JWT]
PUT  /api/orders/{id}/pay         [需JWT]  -- 模拟支付
PUT  /api/orders/{id}/cancel      [需JWT]
PUT  /api/orders/{id}/confirm     [需JWT]  -- 确认收货
```

### 5.4 前端要求

- 购物车页：商品列表、数量加减、勾选结算、合计金额实时计算
- 结算页：收货地址表单、订单确认、模拟支付按钮
- 订单列表页：按状态筛选tab（全部/待付款/待发货/已完成/已取消）
- 订单详情页：状态时间线展示（用简单的步骤条组件即可，不需要复杂动画）

### 5.5 验收标准（重点）

- [ ] **并发测试**：用JMeter或简单的多线程脚本模拟同时100个请求购买库存为80的同一商品，最终成功订单数必须恰好为80，商品stock字段必须为0，不允许出现负库存
- [ ] 任意一步事务失败后，用 `SELECT` 确认相关表没有脏数据残留（订单插入了但库存没扣，或反过来）
- [ ] 订单状态非法跳转（如直接从PENDING_PAYMENT跳到COMPLETED）被后端拒绝
- [ ] 取消订单后，如果该订单已扣库存，需要回补库存（这一点要在代码里明确处理，很容易被遗漏）

### 5.6 不在本模块范围内

- 不接入真实支付渠道（支付宝/微信支付SDK）
- 不做退款/售后流程（明确不在五大模块要求内）
- 不做物流跟踪集成（发货只是状态字段，不接入真实快递API）
- 不用消息队列（RabbitMQ/Kafka）做异步下单，单体事务方案对实训规模完全够用，引入MQ是过度设计

---

## 6. AI智能推荐助手模块（LangChain Agent服务）

### 6.1 需求

这是本项目技术含金量最高、也是大纲第10-11天专门考核的模块。要让Agent**真正调用工具**而不是简单套壁纸式地调用一次大模型API，并且要**有可演示的记忆能力**（短期记住当前对话上下文，长期记住用户偏好）。

### 6.2 架构与技术选型

- 独立服务，技术栈：Python 3.11+ / FastAPI / LangChain（建议用 `langchain` + `langchain-openai` 或兼容Claude API的封装，模型本身可以用Claude API或其他兼容OpenAI协议的relay，由团队根据已有API额度决定）
- 不直连业务数据库的`user`/`product`/`order`表，只通过HTTP调用主后端API来获取数据（这样Agent天然只能看到主系统"允许看到"的数据，权限边界清晰，也更符合企业微服务实践）
- `agent_conversation` 和 `user_preference` 两张表是Agent服务专属，由Agent服务自己用SQLAlchemy直连MySQL读写（这两张表不属于主业务边界，不需要走主后端）

### 6.3 工具（Tools）设计 — 必须至少实现这两个，体现"工具调用"考核点

```python
from langchain.tools import tool
import httpx

BACKEND_BASE = "http://backend:8080"  # docker-compose内部服务名

@tool
def search_products(keyword: str, max_price: float = None) -> str:
    """根据关键词搜索商品，可选传入最高预算价格筛选。
    返回商品名称、价格、库存的简要列表，用于回答用户的购物咨询。"""
    params = {"keyword": keyword, "page": 1, "size": 5}
    resp = httpx.get(f"{BACKEND_BASE}/api/products/search", params=params)
    products = resp.json()["list"]
    if max_price:
        products = [p for p in products if p["price"] <= max_price]
    return "\n".join(f"{p['name']} - ¥{p['price']} - 库存{p['stock']}" for p in products) or "未找到符合条件的商品"

@tool
def query_order_status(user_id: int, order_id: int = None) -> str:
    """查询指定用户的订单状态。如果不传order_id，返回该用户最近的订单列表。
    需要内部服务间认证token访问主系统订单接口（不能让Agent伪造任意用户身份查任意订单）。"""
    headers = {"Authorization": f"Bearer {get_service_token()}"}
    if order_id:
        resp = httpx.get(f"{BACKEND_BASE}/api/orders/{order_id}", headers=headers)
    else:
        resp = httpx.get(f"{BACKEND_BASE}/api/orders?userId={user_id}&size=3", headers=headers)
    return format_order_summary(resp.json())
```

**关于"服务间认证"**：Agent服务调用主后端的订单接口时不应该用普通用户的JWT（用户不会把自己的token交给Agent），而应该用一个独立的**服务间Token**（可以是主后端给Agent服务签发的一个长期有效、scope受限的特殊JWT，或者简单方案是一个共享密钥+`X-Internal-Service: agent`请求头，主后端校验这个头并允许其代查任意`userId`的订单，但禁止其访问用户密码等敏感字段）。这一点在答辩时会被问到"Agent怎么知道该用户的订单"，要能讲清楚。

### 6.4 记忆机制 — 短期记忆

- 用 LangChain 的 `ConversationBufferWindowMemory`（保留最近6轮对话）或手动维护一个按 `session_id` 隔离的内存字典，服务重启会丢失也没关系（这是"短期"记忆的合理特性）
- 每轮对话同时异步写入 `agent_conversation` 表做持久化兜底，这样即使服务重启，重新加载session时可以从数据库恢复最近N轮

### 6.5 记忆机制 — 长期记忆（这是最容易在答辩被深挖的点，逐步说明）

**写入时机**：每次对话结束后（或每隔N轮），调用一次大模型，prompt类似：

```
分析以下对话，提炼用户表现出的购物偏好标签（如"机械键盘""预算敏感""数码爱好者"），
如果没有明显偏好则返回空列表。以JSON数组格式返回标签字符串。
对话内容：{conversation_history}
```

将提炼出的标签 upsert 进 `user_preference` 表（已存在的标签 `weight` 加0.1，封顶2.0）。

**读取时机**：每次新会话开始时，查询该 `user_id` 在 `user_preference` 表里weight最高的3个标签，拼进system prompt：

```
你是智能商城的导购助手。该用户的历史偏好标签：{tags}。
在推荐商品时优先考虑这些偏好，但不要生硬地把标签词塞进每句回复里。
```

这样就实现了"下次对话Agent还记得你喜欢机械键盘"的可演示效果，是验收时最值得当场演示的功能点。

### 6.6 接口规格

```
POST /agent/chat
请求: { "userId":5, "sessionId":"uuid-xxx", "message":"有没有适合敲代码的键盘，预算500" }
响应: 200 {
  "reply": "为你找到几款适合编程的机械键盘：...",
  "toolsUsed": ["search_products"],
  "sessionId": "uuid-xxx"
}

GET /agent/history?sessionId=uuid-xxx
响应: 200 [{ "role":"user","content":"..." }, { "role":"assistant","content":"..." }]
```

### 6.7 前端要求

- 一个悬浮聊天窗组件（右下角圆形按钮展开聊天面板），全局可用，不需要独立路由页面
- 聊天气泡区分用户/AI两种样式，支持基础的发送/接收交互即可，不需要做打字机流式效果（除非团队时间充裕，属于加分项而非必做项）

### 6.8 验收标准（重点，答辩高频问点）

- [ ] 当场演示：问"有没有适合敲代码的键盘"，Agent能调用`search_products`工具并基于真实商品数据回答（不是凭空编造商品名）
- [ ] 当场演示：问"我上次买的东西到哪了"，Agent能调用`query_order_status`工具查出该用户真实订单
- [ ] 当场演示长期记忆：第一次对话中表达"我喜欢机械键盘"，结束对话后**开启新session**，问"有什么新品推荐"，Agent的回复要能体现出"记得"用户喜欢机械键盘（可以让答辩老师直接看`user_preference`表里多了一条记录）
- [ ] 能说清楚"Agent服务如何鉴权调用主系统接口"这个架构问题
- [ ] Agent对不知道的问题（如"今天天气怎么样"）应礼貌说明自己是购物助手，不能瞎编

### 6.9 不在本模块范围内

- 不做语音输入/输出
- 不做多模态（图片识别"以图搜物"），明确超出大纲范围
- 不接入向量数据库做RAG检索（大纲第10-11天只要求"工具调用+记忆"，没要求RAG；如果团队时间充裕可以作为加分项，但不是验收必须项，**不要本末倒置在这里耗费过多时间**）
- 不做多Agent协作/Agent编排框架（如AutoGen式的多智能体），单一Agent+工具调用已经完全覆盖大纲考核点

---

## 7. 测试要求（对应大纲第9天）

- **单元测试**：至少覆盖订单模块的库存扣减逻辑（正常扣减、库存不足、并发冲突三种情况）和用户模块的密码校验逻辑，用 JUnit5 + Mockito
- **集成测试**：用 Postman/Newman 或简单的 `RestAssured` 脚本，覆盖"注册→登录→浏览商品→下单→查询订单"这条完整链路，作为CI/答辩演示的回归脚本
- **缺陷管理**：哪怕只是一个简单的Markdown表格（`docs/bug-list.md`），记录测试中发现的问题、复现步骤、修复状态，这是大纲明确要求的"缺陷报告"产出物

## 8. Docker部署要求（对应大纲第12天）

每个服务（frontend / backend / agent-service）各自一个 `Dockerfile`，根目录一个 `docker-compose.yml` 统一编排：

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: smart_mall
    volumes:
      - mysql_data:/var/lib/mysql
      - ./docs/seed.sql:/docker-entrypoint-initdb.d/seed.sql
    ports: ["3306:3306"]

  backend:
    build: ./backend
    depends_on: [mysql]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/smart_mall
      JWT_SECRET: ${JWT_SECRET}
    ports: ["8080:8080"]

  agent-service:
    build: ./agent-service
    depends_on: [mysql, backend]
    environment:
      DATABASE_URL: mysql://mysql:3306/smart_mall
      LLM_API_KEY: ${LLM_API_KEY}
    ports: ["8000:8000"]

  frontend:
    build: ./frontend
    depends_on: [backend, agent-service]
    ports: ["80:80"]

volumes:
  mysql_data:
```

**日志与监控（大纲明确要求"应用监控与日志管理"，不需要上Prometheus/Grafana这种重型方案）：**
- 后端用 Logback 输出结构化日志到 `logs/` 目录（挂载为volume，方便用`docker logs`或直接`tail`查看）
- 关键业务节点（下单成功/失败、Agent工具调用）必须打INFO级别日志，方便答辩时现场展示"系统在做什么"
- 提供一份 `docs/architecture.md` 里写清楚"如果系统出问题，去哪看日志、看什么"

## 9. Git工作流要求

- 主分支 `main` 只接受经过自测的代码合并，开发分支按模块命名：`feature/user-module`、`feature/order-module`、`feature/agent-service` 等
- 每个团队成员对应自己负责的模块分支独立开发，定期向`main`提PR（哪怕团队内部审核很简单），保留PR记录作为"团队协作"的答辩证明材料
- `.gitignore` 必须排除：`node_modules/`、`target/`、`.env`、`__pycache__/`，密钥类配置全部走环境变量，不要提交到仓库

---

## 10. 总体验收清单（答辩前逐项自查）

- [ ] 五大模块全部可独立演示（用户/商品/订单/搜索/AI助手）
- [ ] `docker-compose up -d` 能一键拉起全部4个服务，无需任何手动额外配置
- [ ] 未登录用户访问受保护接口返回401，登录后正常访问
- [ ] 100并发抢购测试无超卖（订单模块5.5节验收标准）
- [ ] AI助手能调用工具、能演示长期记忆效果（6.8节三个当场演示项）
- [ ] 提供ER图、接口文档、架构说明三份文档（docs/目录）
- [ ] Git提交记录体现团队协作，分支命名规范
- [ ] 至少能展示一段"用Claude Code生成代码"的过程记录（大纲第4点技术目标的证明材料）
- [ ] 单元测试和集成测试均可一键运行并通过
- [ ] 答辩PPT能讲清楚：架构图、五大模块演示、Agent工作原理（重点）、遇到的坑与解决方案

## 11. 项目级别的不在范围内（避免团队过度发挥/偏题）

- 不做小程序/App端，只做Web端
- 不做多语言国际化
- 不做秒杀专题优化（如Redis预扣库存、限流），订单模块的事务+乐观锁方案已经能正确处理大纲案例规模的并发场景
- 不引入微服务网关（Spring Cloud Gateway）、注册中心（Nacos）等重型微服务治理组件——本项目只有2个后端服务，引入这些是严重过度设计，会让团队把时间耗在运维配置而不是大纲要求的核心能力上
- 不做CI/CD自动化流水线（GitHub Actions等），Git版本控制本身已覆盖大纲对"Git"的要求，自动化部署流水线不在五大模块和技术栈清单内

---

## 12. 交付物清单

1. 完整源码（frontend / backend / agent-service 三个目录）
2. `docker-compose.yml` 一键启动配置
3. `docs/er-diagram.md`、`docs/api-spec.md`、`docs/architecture.md`、`docs/bug-list.md`
4. 数据库seed脚本（含测试数据）
5. 单元测试 + 集成测试代码，附运行说明
6. 答辩PPT
7. README.md（项目简介、本地启动步骤、团队分工说明）

