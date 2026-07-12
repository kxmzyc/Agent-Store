# Bug List

| ID | 模块 | 问题 | 复现步骤 | 状态 | 修复说明 |
|---|---|---|---|---|---|
| B001 | 订单 | 并发扣库存需重点回归 | 运行 `python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081 --rounds 3` | Fixed | 2026-07-11 真实 MySQL/InnoDB 三轮均得到 100 请求中成功80、库存不足20、stock=0、sales_count=80、version=80，订单与明细各80条。 |
| B002 | Agent | 未配置 LLM_API_KEY 时不能真实调用模型 | 清空 LLM_API_KEY 后调用 `/agent/chat` | Accepted | 服务降级为规则回复，便于离线演示主链路 |
| B003 | Agent | 预算正则把任意数字当成最高价 | 离线路径发送「推荐3款键盘」，结果被过滤为空 | Fixed | extract_budget 仅在出现预算语境（预算/元/以内/¥等）时识别金额 |
| B004 | 用户 | 更新个人资料时未提交字段被覆盖为 null | 只改头像不传 phone，phone 被清空 | Fixed | updateProfile 改为字段非空才更新 |
| B005 | 订单 | 取消订单回滚库存非并发安全 | 并发取消/取消与下单竞争导致库存对不上 | Fixed | 新增 restoreStock 原子 SQL，与 deductStock 一致 |
| B006 | 订单 | 购物车数量不校验库存 | 反复加购可超过库存，下单才报错 | Fixed | addCart/updateCart 校验不超过库存 |
| B007 | Agent | LLM 正常回复被离线逻辑覆盖 | 已配置 LLM 时合理拒答被替换 | Fixed | 仅空回复或购物意图无工具时才回落 |
| B008 | 前端 | 购物车 +/- 更新未捕获错误 | 加库存校验后点「+」超库存无提示 | Fixed | CartView.update 加 try/catch + toast |
| B009 | 商品 | 管理员无法查看/重新上架已下架商品 | 下架后商品从管理列表消失 | Fixed | 新增 ADMIN-only /products/admin，AdminView 支持上架/下架 |
| B010 | 订单 | 订单页「查看详情」按钮无响应 | 点击无任何反应 | Fixed | 接为展开收货地址/支付时间，对所有订单可用 |
| B011 | 部署 | backend 镜像依赖预编译 jar | clean checkout（无 target/）跑 docker-compose up --build 报 no source files | Fixed | 改多阶段 Maven 构建，镜像内编译打包，复用 settings.xml 镜像源 |
| B012 | 部署 | frontend 缺 .dockerignore | COPY . . 把宿主 node_modules(Windows) 覆盖进镜像，npm run build 失败 | Fixed | 新增 frontend/.dockerignore；并补 backend/agent .dockerignore |
| B013 | Agent | 本地启动脚本后端端口未随 8081 更新 | 后端在 docker(宿主8081)、agent 本地跑时，工具调用连 8080 被拒，返回「服务暂时无法完成工具查询」 | Fixed | start_agent_local.py / start-agent-local.cmd BACKEND_BASE 改 8081 |
| B014 | 前端 | vite dev 代理后端端口未随 8081 更新 | npm run dev 时 /api 代理到 8080，连不上 docker 后端 | Fixed | vite.config.js /api 改 8081 |
| B015 | 用户 | 禁用用户仍可刷新 Access Token；非法 Refresh Token 可能返回500 | 登录后禁用用户，再请求 `/api/auth/refresh`；或提交无效 Token | Fixed | refresh 统一将非法 Token 返回401，并在签发前检查用户仍为启用状态；RestAssured 回归已覆盖。 |
| B016 | 部署 | 后端、Agent 和 Compose 存在弱密钥/密码默认值 | 不设置环境变量启动服务 | Fixed | JWT、内部服务密钥和数据库连接变量改为必填；JWT 少于32字符时拒绝启动；`.env.example` 只保留空占位。 |
| B017 | 商品 | 仅能填写图片URL，未实现本地图片上传与静态访问 | 管理员上传 PNG 后访问返回URL | Fixed | 新增 ADMIN-only `/api/products/upload-image`，限制图片类型与5MB大小，保存至 `/uploads` 并由后端/Nginx提供静态访问；RestAssured 回归已覆盖。 |
