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
- 后端 API：http://localhost:8080
- Agent API：http://localhost:8000
- Swagger：http://localhost:8080/swagger-ui/index.html

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

## 团队分工建议

- 用户与认证模块：注册、登录、JWT、个人中心
- 商品与搜索模块：分类、商品 CRUD、搜索、分页
- 订单模块：购物车、下单、事务、状态流转、并发测试
- AI 助手模块：Agent 工具、短期记忆、长期偏好记忆、前端聊天窗
