# Bug List

| ID | 模块 | 问题 | 复现步骤 | 状态 | 修复说明 |
|---|---|---|---|---|---|
| B001 | 订单 | 并发扣库存需重点回归 | 运行并发脚本购买库存80的抢购测试商品 | Open | 已在后端使用事务 + 乐观锁 SQL，等待联调环境验证 |
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
