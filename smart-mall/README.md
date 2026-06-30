# Smart Mall

智能商城实训项目：Vue3 前端 + Spring Boot 主业务后端 + FastAPI/LangChain Agent 服务 + MySQL。

## 本地启动

1. 复制环境变量示例：

```bash
cp .env.example .env
```

2. 修改 `.env` 中的密钥和模型配置。

3. 启动全部服务：

```bash
docker-compose up -d
```

访问地址：

- 前端：http://localhost
- 后端 API：http://localhost:8081
- Agent API：http://localhost:8000
- Swagger：http://localhost:8081/swagger-ui/index.html

> 注：docker-compose 将后端容器的 8080 映射到宿主机 **8081**，MySQL 映射到 **3307**。容器之间仍走内部端口（backend:8080、mysql:3306）。

## 测试账号

- 管理员：`admin` / `123456`
- 用户：`alice` / `123456`
- 用户：`bob` / `123456`

## 技术栈

- 前端：HTML/CSS/JavaScript、Vue3、axios、Vite
- 后端：Spring Boot、Spring Security、JWT、Spring Data JPA、MySQL
- Agent：FastAPI、LangChain、SQLAlchemy、HTTP 工具调用
- 部署：Docker Compose

## 测试

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
powershell -ExecutionPolicy Bypass -File scripts/concurrent_order_test.ps1
```

Agent 助手验收：

```bash
python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --user-id 2
```

这个脚本会依次验证 `search_products`、`query_order_status`、长期偏好记忆和非购物问题拒答。未配置 `LLM_API_KEY` 时，Agent 会降级为规则路径，但仍会真实调用后端商品/订单工具，便于离线答辩演示。

如果之前已经启动过 MySQL 容器，`docs/seed.sql` 不会自动重新导入。答辩前需要确认 `alice` 用户有订单 `SM202606300001`，否则先清理旧的 `mysql_data` volume 或手动执行 seed 中的演示订单 SQL。

## 团队分工建议

- 用户与认证模块：注册、登录、JWT、个人中心
- 商品与搜索模块：分类、商品 CRUD、搜索、分页
- 订单模块：购物车、下单、事务、状态流转、并发测试
- AI 助手模块：Agent 工具、短期记忆、长期偏好记忆、前端聊天窗
