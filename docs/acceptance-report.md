# 验收报告

生成时间：2026-07-11（Asia/Shanghai）

## 总体状态

`INCOMPLETE`。本次已完成核心代码与运行验证，但不能宣称课程“全部验收通过”：没有独立新数据库的 schema/seed 验收、Agent 确定性测试套件、统一验收入口、真实 Claude Code 记录或真实模块分支/PR 证据。

## 本次通过项

| 项目 | 状态 | 实际命令与结果 |
|---|---|---|
| 后端单元/集成/HTTP回归 | PASS | `mvn.cmd test`：27 tests，0 failures，0 errors。包含 Mockito 重试分支、H2 事务边界和 RestAssured 随机端口“注册到确认收货”链路。 |
| 前端生产构建 | PASS | `npm.cmd run build`：成功。产物含一个 1,135.53 kB（gzip 382.15 kB）chunk 警告。 |
| Compose 配置 | PASS（临时验证变量） | `docker compose config --quiet` 使用只在当前进程注入的变量通过；未修改 `.env`。 |
| 容器 | PASS | 重新执行 `docker compose up -d --build` 后，frontend、backend、agent-service、mysql 均为 Up；Agent/MySQL 健康。 |
| 订单并发 | PASS | `python scripts/concurrent_order_test.py --base-url http://127.0.0.1:8081 --rounds 3`：每轮 100 请求、成功80、库存不足20、异常0、stock=0、sales_count=80、version=80、订单80、明细数量80。运行ID：`1783742074-1-efb91ede25`、`1783742076-2-5c91abcaa3`、`1783742079-3-0f2d9ff68f`。 |
| Agent 真实模型烟测 | PASS | `/health` 返回 `llmEnabled=true`；`python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081` 通过，验证商品、购买方案、详情、等价对比、订单、长期记忆、确认加购和非购物拒答。 |
| 图片上传 | PASS | RestAssured 回归以管理员身份上传 PNG，验证 `201` 和 `/uploads/**` 静态读取。 |
| JWT/权限 | PASS | RestAssured 回归验证 profile 未登录401、普通用户创建商品403、无效或禁用用户 Refresh Token 返回401。 |

## 本次修改

- 下单 CAS 冲突采用独立 `REQUIRES_NEW` 尝试和可解释的重试上限；订单状态转移改为条件更新，防止重复取消、确认和支付副作用。
- 增加 RestAssured 随机端口业务回归与本地图片上传验收。
- 移除可运行的数据库、JWT 和内部服务密钥默认值；Agent 和本地辅助脚本要求显式配置敏感变量。

## 未完成项和风险

- 当前 `.env` 缺少必填变量时，普通 `docker compose` 会拒绝启动；这是安全收口的预期行为。答辩前应由项目成员填写本地 `.env`，不要提交。
- 本次并发使用共享演示数据库中的唯一夹具，未执行破坏性 reset；全新独立数据库的 schema/seed 验收未执行。
- 仍未提供 Agent FastAPI/MockTransport 的确定性测试，真实 LLM 烟测不替代该项。
- 前端大 chunk 警告尚未优化；本次未进行浏览器桌面/移动端视觉回归。
- 未发现真实 Claude Code 对话截图、模块分支或 PR 记录；Codex 本轮记录不能替代这些课程证据。
- 现有答辩 PPT/PDF/DOCX 有多个候选版本，未在本轮二进制同步或视觉QA；本报告和 `docs/defense/ppt_source_map.md` 是当前数字事实源。
