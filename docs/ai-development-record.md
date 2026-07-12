# Codex 开发记录（2026-07-11）

本文件仅记录本轮真实 Codex 操作，不能替代课程要求的 Claude Code 原始对话或截图。

## 用户目标

审阅既有项目改进任务，在保留工作区现有修改的前提下继续未完成的项目改进。

## 关键决策与修改

- 审阅已有订单并发修改后，保留 `stock >= quantity` 与 version CAS 条件，使用每次 `REQUIRES_NEW` 尝试处理 version 冲突；未使用全局锁、Redis 或消息队列。
- 新增 RestAssured 随机端口 HTTP 回归，覆盖注册、重复注册、登录、Refresh Token、401/403、搜索分页、购物车、下单、支付、发货、确认、禁用用户刷新拒绝和商品上传。
- 增加管理员本地图片上传与 `/uploads/**` 静态访问；使用既有 `backend_uploads` volume。
- 移除后端、Compose、Agent 和本地 Agent 脚本中的可运行密钥默认值；JWT 少于32字符时拒绝启动。
- 更新缺陷表、接口文档、架构说明和验收事实源；未伪造 Claude Code、团队分支或 PR 记录，未修改二进制答辩材料。

## 实际验证

- `mvn.cmd test`：27 tests，0 failures，0 errors。
- `npm.cmd run build`：成功；保留一个大 chunk 警告。
- `docker compose up -d --build`：重新构建并启动四个服务，未执行 `down -v`。
- `python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081 --rounds 3`：每轮严格80成功/20库存不足。
- `python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081`：通过，健康接口显示 `llmEnabled=true`。

## 证据边界

- 没有生成或补造 Claude Code 截图。
- 没有创建 Git 提交、分支或 PR。
- 没有对共享 MySQL volume 执行删除、清空或重置。
