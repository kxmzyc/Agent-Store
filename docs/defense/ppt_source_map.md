# 答辩PPT逐页内容依据核查表

生成时间: 2026-07-11（本次运行复核）

## 本次重新审计得到的关键数字

| 项 | 当前结果 | 依据 |
|---|---:|---|
| 后端测试 | 27/27 通过 | 2026-07-11 `mvn.cmd test` 输出 `Tests run: 27, Failures: 0, Errors: 0` |
| 知识库块 | 88 | SQL 查询 `knowledge_chunk`: product 60 / review 16 / faq 12 |
| 并发库存 | 连续3轮均 success=80、库存不足=20 | `python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081 --rounds 3` |
| 并发后库存 | 每轮 stock=0, sales_count=80, version=80；订单/明细各80条 | 脚本按本轮独立商品、用户和订单集合校验 |
| Docker 构建 | 成功 | `docker compose up -d --build` 输出四个镜像 built、容器 started |
| Agent 健康 | OK | `/health` 返回 `llmEnabled=true` 和 8 个工具 |
| Agent 烟测 | 通过，`llmEnabled=true` | 2026-07-11 `python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081`；对比实际走 `get_product_detail` + `compare_products` 等价路径 |

## 逐页来源

### 第1页: 封面
- 项目定位与技术范围来自 README.md、AGENTS.md；测试和知识库数字来自本次执行结果。

### 第2页: 目录
- 由 agent_store_ppt_skill_based_prompt.md 的 20 页答辩结构整理。

### 第3页: 项目背景与定位
- README.md 功能概览与项目定位；AGENTS.md 五大模块和实训范围说明。

### 第4页: 系统架构总览
- docs/architecture.md 架构图、边界、认证和 Agent 工作流；docker-compose.yml 服务编排。

### 第5页: 技术栈
- README.md 技术栈表；backend/pom.xml、frontend/package.json、agent-service/requirements.txt。

### 第6页: 数据库设计
- docs/database-design.md 表清单、3NF 说明；docs/schema.sql / docs/seed.sql；agent-service/scripts/build_knowledge_base.py 创建 knowledge_chunk。

### 第7页: 核心功能一览
- Controller 扫描结果：AuthController、ProductController、OrderController、AfterSaleController、AdminController、CouponController、AddressController 等。

### 第8页: 个性化推荐闭环
- agent-service/app/main.py preference_tags、extract_preferences、upsert_preferences；ProductController /products/recommendations；docs/architecture.md Agent 工作流。

### 第9页: RAG商品知识库
- 本次 SQL 查询：knowledge_chunk 共 88 条，product 60 / review 16 / faq 12；agent-service/scripts/build_knowledge_base.py；agent-service/app/main.py search_knowledge_base。

### 第10页: 多步工具编排
- agent-service/app/main.py system_prompt、TOOLS 列表、build_purchase_plan、add_to_cart；Agent 日志显示 query_order_status 等工具调用。

### 第11页: 高刷新率动效工程
- docs/self-audit-motion-performance.md Part B：CDP 未连接，不伪造 FPS；frontend/scripts/perf-real-browser.mjs；frontend/src/dev/fxDiagnostics.js / perfHud.js。

### 第12页: 并发库存与事务安全
- scripts/concurrent_order_test.ps1 本次执行 success=80 failed=20；SQL 查询 product id=21 得到 stock=0、sales_count=80、version=80；OrderService / ProductRepository 扣库存逻辑。

### 第13页: 系统截图-商品中心与AI对话
- docs/defense/screenshots/home-product-center-logged-in.png；docs/defense/screenshots/ai-chat-order-query.png；Agent 日志显示 query_order_status。

### 第14页: 系统截图-购物车与订单
- docs/defense/screenshots/cart-page.png；docs/defense/screenshots/orders-page.png；CartView.vue、OrdersView.vue。

### 第15页: 系统截图-管理后台
- docs/defense/screenshots/admin-dashboard.png；docs/defense/screenshots/admin-after-sales.png；AdminView.vue；AdminController / AfterSaleController。

### 第16页: 测试与验证
- `mvn.cmd test`：27项通过；并发脚本连续3轮均为80成功/20库存不足；Docker重建和 Agent 真实模型烟测均通过。PPT二进制文件尚未因存在多个候选版本而更新，答辩前应从本表同步后重新渲染核对。

### 第17页: 部署与工程严谨性
- docker-compose.yml；backend/Dockerfile、frontend/Dockerfile、agent-service/Dockerfile；docs/architecture.md 日志说明；本次 docker compose up -d --build 输出。

### 第18页: 已完成 vs 待改进
- docs/bug-list.md；docs/Current_Project_Architecture_Analysis.md；docs/self-audit-motion-performance.md；本次 agent_smoke.py 结果。

### 第19页: 团队分工与AI辅助开发
- README.md 团队分工建议；git log --oneline -n 12；docs/ai-development-record.md / docs/defense 技术材料。

### 第20页: 结尾
- 由全 deck 叙事线总结；演示顺序来自 README.md 与实际页面截图。
