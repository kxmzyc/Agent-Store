# Smart Mall 智能商城

Smart Mall 是一个面向企业实训答辩的“淘宝 + AI 助手”迷你商城系统。项目采用前后端分离架构，并将 AI 导购助手拆成独立 Python 微服务，覆盖用户、商品、搜索、购物车、订单和智能推荐助手的完整演示链路。

项目定位是教学实训项目，重点是功能闭环、工程结构、Docker 部署、JWT 鉴权、事务并发控制和 LangChain Agent 工具调用，不追求生产级高并发架构。

## 功能概览

- 用户与认证：注册、登录、JWT 鉴权、Token 刷新、个人中心、账户概览。
- 商品与搜索：分类浏览、商品列表、商品详情、关键词搜索、排序分页、管理员商品管理。
- 购物车与订单：加入购物车、数量调整、下单结算、模拟支付、取消订单、管理员发货、确认收货、再次购买。
- 并发库存：订单创建使用 `@Transactional` 和乐观锁 SQL 扣减库存，提供并发抢购验收脚本。
- AI 导购助手：悬浮聊天窗、商品搜索、商品详情、商品对比、偏好推荐、加入购物车、订单查询、短期会话记忆、长期偏好记忆。
- 商城体验增强：收藏、浏览足迹、商品评价、优惠券、积分、个人中心工作台。
- 部署与日志：Docker Compose 一键启动 MySQL、Spring Boot、FastAPI Agent 和 Nginx 前端，后端日志持久化到 volume。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue3、Vue Router、axios、Vite、HTML/CSS/JavaScript、Nginx |
| 后端 | Spring Boot 3.3、Spring Security、JWT、Spring Data JPA、Swagger UI |
| 数据库 | MySQL 8.0、InnoDB、utf8mb4、ngram 全文索引、seed SQL |
| AI 服务 | FastAPI、LangChain、langchain-openai、SQLAlchemy、httpx |
| 部署 | Docker、Docker Compose |
| 测试 | JUnit5、Spring Security Test、PowerShell 并发脚本、Agent 烟测脚本 |

## 架构

```text
Vue3 Frontend (Nginx, :80)
  ├─ /api   -> Spring Boot Backend (:8081 -> container 8080)
  └─ /agent -> FastAPI LangChain Agent (:8000)

Spring Boot Backend
  └─ MySQL (:3307 -> container 3306)

FastAPI Agent
  ├─ 携带用户 JWT 调后端 /api/user/profile 校验身份
  ├─ 通过内部服务密钥调用 Backend 订单/购物车受限接口
  └─ 读写 agent_conversation / user_preference 维护记忆
```

核心边界：

- 前端只访问后端 REST API 和 Agent API，不直连数据库。
- Spring Boot 是用户、商品、购物车、订单等主业务数据的唯一写入口。
- Agent 不信任前端传入的 `userId`，每次通过用户 JWT 调主后端确认身份。
- Agent 不直接写商品、订单、用户等业务表；订单查询和加购通过主后端内部接口完成。
- Agent 专属记忆表由 Agent 服务直接读写，用于短期对话恢复和长期偏好记忆。
- 后端订单创建用 `@Transactional` 保证扣库存、写订单、写订单明细和清购物车的一致性。

## 目录结构

```text
smart-mall/
├── frontend/                 # Vue3 前端项目
├── backend/                  # Spring Boot 主业务后端
├── agent-service/            # FastAPI + LangChain Agent 服务
├── docs/                     # ER 图、接口文档、架构说明、缺陷记录、答辩材料
├── scripts/                  # 并发扣库存和 Agent 验收脚本
├── docker-compose.yml        # 一键部署编排
├── open-project.cmd          # Windows 一键启动并打开前端
├── .env.example              # 环境变量示例
└── README.md
```

## 快速启动

前置条件：

- 已安装并启动 Docker Desktop。
- 当前终端位于 `smart-mall/` 目录。

Windows 最快方式（首次启动先生成 `.env`）：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
.\open-project.cmd
```

脚本会执行 `docker compose up -d`，显示容器状态，并打开前端页面。已有 `.env` 后可直接双击 `open-project.cmd`。

命令行方式：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --build
docker compose ps
```

打开前端：

```text
http://127.0.0.1/
```

停止服务：

```bash
docker compose down
```

清空 MySQL volume 并重新导入 `docs/seed.sql`：

```bash
docker compose down -v
docker compose up -d --build
```

注意：MySQL volume 已存在时，`docs/seed.sql` 不会自动重复导入。修改种子数据后，如需同步到已有数据库，需要清空 volume 或手动执行 SQL。

## 访问地址

| 服务 | 地址 | 说明 |
|---|---|---|
| 前端页面 | http://127.0.0.1/ | Nginx 托管的 Vue 应用 |
| 后端 Swagger | http://127.0.0.1:8081/swagger-ui/index.html | 后端接口调试 |
| 后端健康检查 | http://127.0.0.1:8081/health | Spring Boot 健康检查 |
| Agent API | http://127.0.0.1:8000 | FastAPI 服务 |
| Agent 健康检查 | http://127.0.0.1:8000/health | Agent 工具与记忆状态 |
| MySQL | 127.0.0.1:3307 | 容器内为 `mysql:3306` |

端口说明：

- 后端容器内端口是 `8080`，宿主机映射到 `8081`。
- MySQL 容器内端口是 `3306`，宿主机映射到 `3307`。
- 容器之间使用服务名访问，例如 `backend:8080`、`mysql:3306`。

## 测试账号

初始化数据来自 `docs/seed.sql`。

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `123456` |
| 普通用户 | `alice` | `123456` |
| 普通用户 | `bob` | `123456` |

管理员登录后可访问 `/admin`，普通用户访问管理员接口会返回 403。

## 环境变量

`.env.example` 提供本地可运行默认值：

```env
MYSQL_ROOT_PASSWORD=smartmall_root
MYSQL_DATABASE=smart_mall
JWT_SECRET=replace-with-at-least-32-characters-secret
INTERNAL_SERVICE_SECRET=agent-internal-secret
LLM_API_KEY=
LLM_BASE_URL=
LLM_MODEL=gpt-4o-mini
```

说明：

- `JWT_SECRET` 用于后端签发 JWT，本地演示可用默认值，公开部署应替换。
- `INTERNAL_SERVICE_SECRET` 用于 Agent 调用后端内部接口时做服务间认证。
- `LLM_API_KEY` 为空时，Agent 会走离线兜底逻辑，但仍会真实调用后端商品和订单工具，适合无模型额度时演示。

不要提交 `.env`。仓库已在 `.gitignore` 中排除 `.env`、`node_modules/`、`target/`、`dist/`、日志和 IDE 配置。

## 主要页面

| 路由 | 说明 |
|---|---|
| `/` | 商品列表、轮播推荐、搜索、分类筛选、AI 聊天入口 |
| `/login` | 登录 |
| `/register` | 注册 |
| `/products/:id` | 商品详情、收藏、浏览记录、评价 |
| `/cart` | 购物车 |
| `/checkout` | 结算 |
| `/orders` | 订单列表 |
| `/orders/:id` | 订单详情 |
| `/favorites` | 商品收藏 |
| `/profile` | 个人中心 |
| `/coupons` | 优惠券 |
| `/admin` | 管理员工作台 |

## 常用接口

后端基础地址：`http://127.0.0.1:8081`

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/user/profile`
- `GET /api/user/overview`
- `GET /api/categories`
- `GET /api/products`
- `GET /api/products/search?keyword=键盘&page=1&size=20`
- `GET /api/products/{id}`
- `GET /api/cart`
- `POST /api/orders`
- `POST /api/orders/direct`
- `GET /api/orders?status=all`
- `PUT /api/orders/{id}/pay`
- `PUT /api/orders/{id}/cancel`
- `PUT /api/orders/{id}/confirm`
- `POST /api/orders/{id}/rebuy`
- `GET /api/admin/dashboard`

Agent 基础地址：`http://127.0.0.1:8000`

- `POST /agent/chat`
- `POST /agent/chat/stream`
- `GET /agent/history?sessionId=<sessionId>`
- `GET /agent/preferences`
- `DELETE /agent/preferences/{tag}`
- `GET /health`

Agent 接口需要携带用户登录 JWT。Agent 会调用主后端 `/api/user/profile` 校验 Token，并以后端返回的用户 ID 作为唯一可信身份。

完整接口说明见 `docs/api-spec.md`。

## 本地开发

### 后端

```bash
cd backend
mvn spring-boot:run
```

如果使用 Compose 暴露的 MySQL，需要指定宿主机端口 `3307`：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3307/smart_mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:SPRING_DATASOURCE_PASSWORD="smartmall_root"
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

Vite 代理已配置：

- `/api` -> `http://localhost:8081`
- `/agent` -> `http://localhost:8000`

### Agent

```bash
cd agent-service
pip install -r requirements.txt
python start_agent_local.py
```

`start_agent_local.py` 默认连接 Compose 暴露的 MySQL `127.0.0.1:3307` 和后端 `127.0.0.1:8081`。

## 测试与验收

后端单元测试：

```bash
cd backend
mvn test
```

前端构建：

```bash
cd frontend
npm install
npm run build
```

并发扣库存验收：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/concurrent_order_test.ps1 -BaseUrl http://localhost:8081
```

Agent 烟测：

```bash
python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081
```

Agent 烟测会先登录 `alice`，再验证：

- `search_products` 商品搜索工具调用。
- `get_product_detail` 商品详情工具调用。
- `compare_products` 商品对比工具调用。
- `query_order_status` 订单查询工具调用。
- `add_to_cart` 确认后加入购物车。
- 长期偏好记忆写入与读取。
- 非购物问题拒答。

## 数据库与种子数据

数据库初始化脚本：`docs/seed.sql`

包含：

- 用户、分类、商品、购物车、订单、订单明细。
- 收藏、浏览足迹、评价、优惠券、积分相关测试数据。
- Agent 对话表 `agent_conversation`。
- 长期偏好表 `user_preference`。
- 至少 20 条商品测试数据和演示订单。

如需手动把 seed 导入当前 MySQL 容器：

```powershell
cmd /c "docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -psmartmall_root smart_mall < docs\seed.sql"
```

ER 图见 `docs/er-diagram.md`，建表说明见 `docs/schema.sql` 和 `docs/database-design.md`。

## 日志查看

查看全部服务日志：

```bash
docker compose logs -f
```

查看后端日志：

```bash
docker compose logs -f backend
```

查看 Agent 日志：

```bash
docker compose logs -f agent-service
```

后端容器内日志目录为 `/app/logs`，Compose 使用 `backend_logs` volume 持久化。下单成功/失败、Agent 工具调用等关键业务节点会输出 INFO 级别日志。

## 答辩材料

- `docs/er-diagram.md`：数据库 ER 图。
- `docs/api-spec.md`：接口文档。
- `docs/architecture.md`：架构、认证、事务、Agent 工作流和日志说明。
- `docs/database-design.md`：数据库设计说明。
- `docs/bug-list.md`：缺陷记录。
- `docs/ppt-outline.md`：答辩 PPT 提纲。
- `docs/defense/`：答辩技术材料。
- `CODEX_DEFENSE_PREP.md`：Codex 辅助开发与答辩准备记录。

## 常见问题

### 前端打不开

先检查容器：

```bash
docker compose ps
```

确认 `frontend` 状态为 `Up`，并检查宿主机 `80` 端口是否被占用。

### Swagger 打不开

访问宿主机端口：

```text
http://127.0.0.1:8081/swagger-ui/index.html
```

不要访问容器内部端口 `8080`。

### 修改 seed 后页面数据没变

已有 MySQL volume 不会自动重新执行初始化脚本。可选择：

```bash
docker compose down -v
docker compose up -d --build
```

或手动执行 `docs/seed.sql`。

### Agent 返回 401

Agent 接口需要前端传入用户登录 JWT。请先登录商城，再使用右下角聊天窗或在请求头中携带：

```http
Authorization: Bearer <accessToken>
```

### Agent 查不到订单

检查 backend 和 agent-service 的 `INTERNAL_SERVICE_SECRET` 是否一致，并确认后端容器正常运行。

### Agent 没有真实大模型回答

检查 `.env` 中的 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`。不配置时会使用离线兜底逻辑。

## GitHub 上传前检查

建议上传前执行：

```bash
docker compose ps
cd frontend && npm run build
cd ../backend && mvn test
```

确认不要提交：

- `.env`
- `node_modules/`
- `target/`
- `dist/`
- `logs/`
- IDE 配置目录，例如 `.idea/`、`.vscode/`

## 团队分工建议

- 用户与认证模块：注册、登录、JWT、个人中心。
- 商品与搜索模块：分类、商品 CRUD、搜索、分页、前端商品展示。
- 订单模块：购物车、下单、事务、状态流转、并发测试。
- AI 助手模块：Agent 工具、短期记忆、长期偏好记忆、前端聊天窗。
- 文档与答辩：ER 图、接口文档、架构说明、PPT 和演示脚本。
