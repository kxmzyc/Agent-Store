# Smart Mall

智能商城实训项目：Vue3 前端 + Spring Boot 主业务后端 + FastAPI/LangChain Agent 服务 + MySQL。

项目目标是实现“淘宝 + AI 助手”的微型闭环：用户注册登录、商品浏览与搜索、购物车、下单与订单状态流转，以及能调用真实系统工具的 AI 导购助手。

## 当前实现

- 前端：Vue3、Vue Router、axios、Vite、Nginx 容器部署。
- 后端：Spring Boot 3.3、Spring Security、JWT、Spring Data JPA、MySQL、Swagger UI。
- 数据库：MySQL 8.0，初始化脚本位于 `docs/seed.sql`。
- Agent：FastAPI、LangChain、HTTP 工具调用、短期对话记忆、长期偏好记忆。
- 部署：根目录 `docker-compose.yml` 一键启动 MySQL、backend、agent-service、frontend。

## 目录结构

```text
smart-mall/
├── frontend/            # Vue3 前端
├── backend/             # Spring Boot 后端
├── agent-service/       # FastAPI + LangChain Agent 服务
├── docs/                # ER图、接口文档、架构说明、缺陷记录、答辩材料
├── scripts/             # 验收和烟测脚本
├── docker-compose.yml
└── README.md
```

## 快速启动

前置条件：

- Docker Desktop 已启动。
- 当前工作目录在 `smart-mall/`。

1. 准备环境变量：

```bash
cp .env.example .env
```

`.env.example` 已包含可本地运行的默认值。没有 `LLM_API_KEY` 时，Agent 会降级到规则兜底路径，但仍会真实调用后端商品和订单工具，适合离线演示。

2. 启动全部服务：

```bash
docker compose up -d
```

3. 查看容器状态：

```bash
docker compose ps
```

4. 停止服务：

```bash
docker compose down
```

如需清空 MySQL 数据并重新导入 `docs/seed.sql`：

```bash
docker compose down -v
docker compose up -d
```

## 访问地址

| 服务 | 地址 | 说明 |
|---|---|---|
| 前端 | http://127.0.0.1/ | Nginx 托管的 Vue 页面 |
| 后端 Swagger | http://127.0.0.1:8081/swagger-ui/index.html | 后端接口调试入口 |
| 后端健康检查 | http://127.0.0.1:8081/health | Spring Boot 健康检查 |
| Agent API | http://127.0.0.1:8000 | FastAPI Agent 服务 |
| Agent 健康检查 | http://127.0.0.1:8000/health | 返回工具和记忆机制状态 |
| MySQL | 127.0.0.1:3307 | 容器内端口仍是 `mysql:3306` |

Compose 端口映射说明：

- `backend` 容器内是 `8080`，宿主机映射为 `8081`。
- `mysql` 容器内是 `3306`，宿主机映射为 `3307`。
- 容器之间使用内部服务名访问，例如 `backend:8080`、`mysql:3306`。

## 测试账号

初始化数据来自 `docs/seed.sql`：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `123456` |
| 普通用户 | `alice` | `123456` |
| 普通用户 | `bob` | `123456` |

## 主要功能

- 用户模块：注册、登录、JWT 刷新、个人信息查看与更新、账户概览、商品收藏、浏览足迹。
- 商品模块：分类、商品列表、商品详情、收藏状态、管理员商品增删改、运营工作台。
- 搜索模块：商品关键词搜索、分页、排序。
- 订单模块：购物车、购物车下单、直接下单验收接口、模拟支付、发货、确认收货、取消订单、历史订单再次购买。
- AI 助手：悬浮聊天窗、商品搜索工具、订单查询工具、短期会话记忆、长期用户偏好记忆。

## 常用接口

后端基础地址：`http://127.0.0.1:8081`

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/categories`
- `GET /api/products`
- `GET /api/products/search?keyword=键盘&page=1&size=20`
- `GET /api/user/overview`
- `GET /api/user/favorites`
- `GET /api/cart`
- `POST /api/orders`
- `POST /api/orders/direct`
- `POST /api/orders/{id}/rebuy`
- `GET /api/orders?status=all`
- `GET /api/admin/dashboard`

Agent 基础地址：`http://127.0.0.1:8000`

- `POST /agent/chat`
- `POST /agent/chat/stream`
- `GET /agent/history?sessionId=<sessionId>&userId=<userId>`
- `GET /health`

更完整的接口说明见 `docs/api-spec.md`。

## 本地开发

后端单独运行：

```bash
cd backend
mvn spring-boot:run
```

后端默认连接 `localhost:3306`。如果使用 Compose 里的 MySQL，需要在本地运行时配置：

```bash
set SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/smart_mall?useUnicode=true^&characterEncoding=utf8^&serverTimezone=Asia/Shanghai^&allowPublicKeyRetrieval=true^&useSSL=false
set SPRING_DATASOURCE_PASSWORD=smartmall_root
mvn spring-boot:run
```

前端单独运行：

```bash
cd frontend
npm install
npm run dev
```

Vite 开发代理已配置：

- `/api` -> `http://localhost:8081`
- `/agent` -> `http://localhost:8000`

Agent 单独运行：

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

并发扣库存验收脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/concurrent_order_test.ps1 -BaseUrl http://localhost:8081
```

Agent 烟测：

```bash
python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --user-id 2
```

该脚本用于验证：

- `search_products` 商品搜索工具调用。
- `query_order_status` 订单查询工具调用。
- 长期偏好记忆写入与读取。
- 非购物问题拒答。

## 日志查看

查看所有服务日志：

```bash
docker compose logs -f
```

查看后端日志：

```bash
docker compose logs -f backend
```

查看 Agent 工具调用日志：

```bash
docker compose logs -f agent-service
```

后端容器内日志目录为 `/app/logs`，Compose 使用 `backend_logs` volume 持久化。

## 关键文档

- `docs/er-diagram.md`：数据库 ER 图。
- `docs/api-spec.md`：接口文档。
- `docs/architecture.md`：架构、认证、事务、Agent 工作流和日志说明。
- `docs/bug-list.md`：缺陷记录。
- `docs/ppt-outline.md`：答辩 PPT 提纲。
- `docs/defense/`：答辩技术材料。

## 排查建议

- 前端打不开：确认 `docker compose ps` 中 `frontend` 正常运行，并检查宿主机 `80` 端口是否被占用。
- Swagger 打不开：确认访问的是宿主机端口 `8081`，不是容器内部端口 `8080`。
- 数据没有更新：如果 MySQL volume 已存在，`docs/seed.sql` 不会自动重复导入；需要 `docker compose down -v` 后重新启动，或手动执行 SQL。
- Agent 无法查订单：检查 `INTERNAL_SERVICE_SECRET` 是否在 backend 和 agent-service 中保持一致。
- Agent 没有真实大模型回答：检查 `.env` 中的 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`；不配置时会使用离线兜底逻辑。
