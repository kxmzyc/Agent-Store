# 答辩 PPT 大纲

## 1. 项目定位

- 智能商城：淘宝核心链路 + AI 导购助手
- 目标：五大模块可演示，体现软件工程全流程和 Agent 集成能力

## 2. 总体架构

- Vue3 前端
- Spring Boot 主业务后端
- FastAPI + LangChain 独立 Agent 服务
- MySQL 唯一业务数据源
- Docker Compose 一键启动

## 3. 数据库设计

- 用户、分类、商品、购物车、订单、订单项
- Agent 会话和用户偏好两张专属表
- 说明 3NF 与订单快照字段合理性

## 4. 用户模块演示

- 注册、登录、JWT 鉴权
- 未登录访问个人中心返回 401
- BCrypt 密码哈希

## 5. 商品与搜索模块演示

- 分类浏览
- 商品详情
- 模糊搜索“键盘”
- 管理员商品增删改

## 6. 订单模块演示

- 加入购物车、提交订单、模拟支付
- 订单状态机
- 取消订单回补库存
- 并发扣库存脚本验证不超卖

## 7. AI 助手模块演示

- 问“有没有适合敲代码的键盘，预算500”
- 展示工具调用 `search_products`
- 问“我上次买的东西到哪了”
- 展示工具调用 `query_order_status`
- 新会话展示长期偏好记忆

## 8. Agent 工作原理

- 用户消息进入 `/agent/chat`
- 读取短期历史和长期偏好
- 根据意图调用商品/订单工具
- 工具通过 REST 调 Spring Boot
- 对话与偏好写入 MySQL

## 9. 日志与部署

- `docker-compose up -d`
- `docker compose logs backend`
- `docker compose logs agent-service`
- 后端业务日志：下单成功/失败、状态流转

## 10. 遇到的问题与解决

- JPA bulk update 后一级缓存旧值：使用 `clearAutomatically`
- Agent 无 API Key 场景：规则降级保证主链路可演示
- 并发测试不走购物车唯一键，使用实训专用直接下单端点验证库存事务
