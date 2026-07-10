# Codex Master Prompt - Smart Mall Full Improvement

> 使用方式：将本文件从“你现在接手”开始完整粘贴给 Codex。不要删减验收标准，也不要只让 Codex 输出方案。

---

你现在接手项目 `G:\claudeproject\Agent Store\smart-mall`。你的任务不是继续分析，也不是只给建议，而是基于现有代码实施必要修复，把项目提升到“大学本科企业实训优秀/满分项目”的可验收状态。

## 一、最高层目标

在严格遵守仓库根目录 `AGENTS.md` 的前提下，完成以下目标：

1. 五大核心模块继续保持可运行：用户、商品、搜索、订单、AI 智能推荐助手。
2. 修复订单并发场景中的“防超卖但严重少卖”问题。
3. 建立真实、可重复、失败时返回非零退出码的一键验收链路。
4. 让 Agent 的测试既验证真实工具和真实数据，又不因为大模型选择了等价工具路径而随机失败。
5. 补齐注册到下单查询的完整自动化回归。
6. 消除密钥硬编码默认值及文档、PPT、缺陷表之间的结论冲突。
7. 收敛范围外功能带来的数据库完整性、前端体积和维护性问题。
8. 最终所有“已通过”结论都必须由本次实际执行结果支持，不能引用旧截图或历史口头结论。
9. 逐项核对课程强制技术栈：Vue3、axios、Spring Boot、RESTful、JWT、BCrypt、`@Transactional`、MySQL、LangChain Tools、短期/长期记忆、Docker、Git、日志监控。
10. 留下真实的 Codex/Claude Code 辅助开发证据，但不得补造历史记录或虚构团队协作。

满分标准不是“页面很多”，而是“核心链路正确、可复现、可解释、证据一致”。停止增加新的业务功能。

## 二、必须先读取的上下文

开始任何修改前，完整读取并遵守：

- `G:\claudeproject\Agent Store\AGENTS.md`
- `README.md`
- `docker-compose.yml`
- `docs/schema.sql`
- `docs/seed.sql`
- `docs/bug-list.md`
- `docs/architecture.md`
- `docs/database-design.md`
- `docs/api-spec.md`
- `docs/er-diagram.md`
- `docs/defense/ppt_source_map.md`
- `docs/defense/项目开发报告.md`
- `docs/defense` 下全部 PPTX/PDF/预览，当前至少包括旧 `答辩PPT.pptx`、`final/*最高分版*`、`final/*自检完善版*`，不得默认编辑旧文件
- `backend/Dockerfile`、`agent-service/Dockerfile`、`frontend/Dockerfile`
- `.env.example`、`.gitignore`、`open-project.cmd`、`frontend/nginx.conf`
- 后端安全、订单、库存 Repository 及现有测试
- Agent 主文件和 `scripts/agent_smoke.py`
- 前端路由、请求拦截器、商城主页面、管理页面和 AI 组件

先执行只读盘点：

```powershell
git status --short --branch
git diff --stat
git diff --check
git branch --all
git log --oneline --decorate -n 30
docker compose ps
```

当前工作区可能存在用户尚未提交的 Agent 与前端改动。必须逐项阅读并在其基础上工作。

严禁：

- `git reset --hard`
- `git checkout -- <file>`
- 删除或覆盖无法确认来源的未提交修改
- 为了让测试通过而降低断言强度
- 为了让文档好看而把失败写成成功

不要提交、推送、创建 PR 或改写 Git 历史，除非用户另行明确授权。可以建议分支和 PR 方案，但不能伪造协作记录。

## 三、当前已知风险，必须重新验证

以下是前次审计发现的风险，不允许直接当成最终事实；先重跑，再决定如何修复：

1. `OrderServiceIntegrationTest.concurrentDirectOrdersDoNotOversell` 只断言成功数小于等于库存，可能把大量版本冲突导致的“少卖”判为成功。
2. 一次测试曾出现库存 5、12 个同步请求只成功 2 个、剩余库存 3。
3. `scripts/concurrent_order_test.ps1` 只输出成功/失败数，没有同步起跑屏障、最终数据库断言和可靠退出码。
4. `docs/bug-list.md` 将并发验收标为 Open，但 PPT/报告部分位置写成已通过。
5. Agent 严格烟测曾因模型用两次 `get_product_detail` 完成对比、没有使用 `compare_products` 而失败；业务结果正确，但脚本把工具名称写死。
6. `docs/defense/ppt_source_map.md` 承认烟测失败，`项目开发报告.md` 多处声称烟测通过。
7. 后端与 Agent 配置中存在 JWT 和内部服务密钥的硬编码默认值。
8. 仓库只有 `main`，无可验证的模块分支、合并或 PR 记录。
9. 前端生产构建存在大 chunk 警告；单个全局 CSS 和管理页文件过大。
10. 部分范围外数据表只有索引没有外键，`user.points` 与积分流水之间存在可推导冗余，但 3NF 文档未充分解释。
11. 当前测试以 Spring 集成测试和 H2 为主，课程明确要求的 JUnit5 + Mockito 单元测试证据不足。
12. 尚未发现满足课程指定格式的 RestAssured/Newman 完整业务链回归。
13. 需要重新核实商品图片上传与 `/uploads` 静态资源映射是否真实实现，而不是只允许填写图片 URL。
14. 需要重新核实日志 volume、关键业务 INFO 日志、Swagger、健康检查和 clean checkout 构建是否都能现场演示。
15. 需要核实 seed SQL 是否能在全新数据库中稳定产生至少 3 个用户、3 个分类、20 个商品以及并发测试夹具。

## 四、执行原则

1. 先复现，再修复；没有失败证据时不要猜测改代码。
2. 先解决 P0 验收问题，再做代码组织和视觉优化。
3. 修改范围要小，复用现有 Spring Data JPA、FastAPI、LangChain、Vue3 模式。
4. 不引入 Redis、MQ、Elasticsearch、Spring Cloud、Nacos、网关、Kubernetes 或 CI/CD。
5. 不新增支付、物流、语音、多模态、多 Agent 等范围外功能。
6. 测试不得仅检查源码字符串或注解存在，必须验证行为和数据库最终状态。
7. H2 测试不能替代 MySQL/InnoDB 并发验收；两者职责要分开。
8. 外部 LLM 测试不能成为唯一自动化门禁；需要确定性测试与真实 LLM 烟测两层。
9. 任何数据库清空、`docker compose down -v` 或测试数据重置，都必须先说明影响并取得用户确认。优先使用独立测试数据或独立 Compose 项目。
10. 保留中文用户体验，不向前端暴露异常堆栈、SQL 或内部密钥。
11. 每次只允许一个实施阶段处于进行中。P0 未全部通过前，不得开始大规模 CSS、组件或文档重构。
12. 允许并行执行互不冲突的只读审计或测试；同一文件的实际修改由主代理统一完成，避免并发覆盖。
13. 不因为当前代码已经有优惠券、售后、积分或 RAG 就继续扩展这些模块。它们最多是加分项，不得占用 P0 修复时间。
14. 不把旧截图、旧数据库、旧日志或旧报告中的数字当成本轮基线。
15. 若某个阶段需要明显扩大范围，先说明必要性和最小替代方案；能通过测试和局部修改解决时，不做全模块重写。

### 阶段门禁

严格按以下顺序推进：

1. **Gate A：基线可构建**——现有后端测试、前端构建、Compose 配置和 Agent 语法结果已记录。
2. **Gate B：核心正确性**——并发、事务回滚、状态机、认证和完整业务链全部通过。
3. **Gate C：Agent 可重复验收**——确定性测试通过，真实 LLM 烟测结果如实记录。
4. **Gate D：部署与数据**——新环境启动、seed、日志、Swagger、健康检查和密钥方案通过。
5. **Gate E：维护性与材料**——最后才处理前端拆分、文档、DOCX 和 PPT。

任何 Gate 失败时，先修复该 Gate；不要同时铺开后续所有文件。

## 五、工作计划与强制验收

### 阶段 0：建立基线

执行并记录：

```powershell
cd "G:\claudeproject\Agent Store\smart-mall\backend"
mvn.cmd test

cd "G:\claudeproject\Agent Store\smart-mall\frontend"
npm.cmd run build

cd "G:\claudeproject\Agent Store\smart-mall"
docker compose config --quiet
docker compose ps
python -c "import ast,pathlib; ast.parse(pathlib.Path(r'agent-service/app/main.py').read_text(encoding='utf-8')); print('agent syntax OK')"
```

同时记录：

- Java、Maven、Node、npm、Python、Docker 和 Compose 版本
- 当前 commit、分支、未提交文件列表和原有 diff 摘要
- 测试数量、失败信息和前端构建 chunk 警告
- 四个服务状态、端口和 Agent `/health` 的 `llmEnabled`
- 当前数据库的用户、分类、商品数量，但不得把这些历史数据当成 seed 验收结果

将基线写入临时工作记录或最终 `docs/acceptance-report.md` 的“修复前基线”部分。不要为了建立基线修改业务代码。

不要在基线失败时直接跳到后续阶段。

### 阶段 1（P0）：修复并发库存“少卖”

必须理解当前逻辑：

```sql
UPDATE product
SET stock = stock - ?, version = version + 1, sales_count = sales_count + ?
WHERE id = ? AND stock >= ? AND version = ?
```

保留 `stock >= quantity` 与 `version = expectedVersion` 两个条件，不得删除任何一个。

问题目标不是仅保证 `stock >= 0`，而是库存为 80、100 个有效请求同时竞争时：

- 成功订单必须恰好 80
- 失败请求必须恰好 20
- 最终 `stock = 0`
- 最终 `sales_count` 增加 80
- 最终 `version` 增加 80
- 成功订单和订单明细各恰好 80 条
- 不存在只扣库存未写订单或只写订单未扣库存

推荐采用“外层协调 + 新事务 attempt”的最小 CAS 重试方案：

1. 外层协调方法本身不持有一个贯穿所有重试的旧事务快照。
2. 每个 attempt 都在一个完整的新事务中重新读取商品、校验库存、执行带 version 的原子 UPDATE，并完成订单、明细、购物车、优惠券/积分等该次下单副作用。
3. 影响行数为 1：该 attempt 正常提交。
4. 影响行数为 0：抛出明确的 version 冲突异常，让整个 attempt 回滚。
5. 外层捕获 version 冲突后，用新的事务和新的数据库快照重试。
6. 新 attempt 读到真实库存不足时立即终止，返回明确库存不足；库存不足不重试。
7. 仅 version 冲突允许重试；死锁、连接错误、约束错误和未知异常不能伪装成库存不足。
8. 可使用 `TransactionTemplate` 或独立事务 Bean。禁止依赖同类内部方法自调用触发 `@Transactional`，因为 Spring 代理不会生效。

不要在同一个既有 `@Transactional` 方法中简单执行 `findById -> bulk UPDATE -> findById` 循环。MySQL 默认 `REPEATABLE_READ`、JPA 一级缓存或 rollback-only 状态可能让它持续使用旧快照。若选择同事务重读，必须用真实 MySQL 证明隔离级别、current read 和 persistence context 行为完全正确，否则不采用。

CAS 重试实现还必须满足：

- 每个 attempt 都重新读取数据库中的最新库存和 version。
- 不引入 Spring Retry 等通用重试基础设施；优先使用现有 Spring 事务能力完成小范围实现。
- 不能用固定 3 次或 5 次重试后把仍有库存的请求判失败。采用可解释的 deadline/上限，并记录每个请求的重试次数。
- 100/80 验收中只要库存仍充足却出现 `retry_exhausted`，该轮就必须失败。
- 不得在 attempt 提交前生成不可回收的外部副作用。
- 多商品订单中任何一个商品最终失败时，该 attempt 内之前商品的库存、销量、version、订单、明细和购物车变更都必须回滚。
- 订单号生成应在并发下保持足够唯一；若现有“毫秒时间 + 四位随机数”可发生冲突，采用项目规模适合的 UUID/高熵方案，但不要引入分布式 ID 服务。

不要通过全局 Java `synchronized`、单机锁、延长 HTTP 超时或降低并发来掩盖问题。

需要新增或强化测试：

- 正常扣减成功
- 库存真实不足
- 旧 version 冲突后重新读取并成功
- 多次冲突后明确失败
- 下单中途异常导致库存、订单、明细、购物车全部回滚
- 非法状态跳转被拒绝
- 取消待付款订单回补库存
- 两个并发取消请求不能重复回补库存或重复减少销量
- 支付、发货、确认等重复请求不能绕过状态机
- 若积分扩展继续启用，再验证重复确认不会重复发放积分；积分不得阻塞核心 P0 完成

不要把 H2 线程调度结果当成 100/80 证据。默认 `mvn test` 中的 JUnit 负责确定性重试分支、状态机和事务回滚；100/80“恰好售完”的唯一强制证据来自真实 MySQL/InnoDB 黑盒验收。除非仓库已经具备稳定、可离线运行的 Testcontainers 环境，否则不要为了这一点引入 Testcontainers 作为默认构建依赖。

课程要求的单元测试必须显式使用 **JUnit5 + Mockito**，至少覆盖：

- Repository 首次扣减成功
- Repository 返回 0 且重读后库存不足
- Repository 返回 0 且重读后 version 已变化，随后重试成功
- 重试耗尽返回明确冲突错误
- 密码编码、正确密码匹配、错误密码拒绝

Mockito 单元测试用于验证分支和重试行为；Spring/H2 集成测试用于验证事务边界；MySQL 黑盒并发脚本用于验证真实 InnoDB 最终状态。三层证据不能相互冒充。

事务中途失败测试应使用测试范围内的 mock/spy 或自然业务失败点，让订单明细保存等步骤在扣库存之后抛出异常。不得增加生产故障开关或公开测试接口。异常后必须在新的事务/清空 EntityManager 后，按运行前后增量断言 stock、sales_count、version、order、item 和 cart 全部恢复。

### 阶段 2（P0）：重写并发黑盒验收脚本

优先保留现有脚本入口；如 PowerShell Job 无法实现可靠同步，可以增加一个简单 Python 多线程脚本，但不要引入大型压测框架。

脚本必须：

1. 使用固定线程池和起跑屏障，让请求尽可能同时发出。
2. 使用真实登录 Token 和真实 HTTP `/api/orders/direct`。
3. 默认由管理员 API 为每轮创建名称含唯一 `runId` 的全新测试商品，`stock=80`、`sales_count=0`、`version=0`，不得复用 seed 商品 21。
4. 默认创建或注册本轮独立测试用户，避免把 alice 的历史订单和偏好混入结果。
5. 统计成功与每类失败原因。
6. 查询最终商品状态。
7. 查询或核对本轮成功订单与明细数量。
8. 严格断言 `80/20/0/80/80` 等最终值。
9. 任一不匹配时输出诊断信息并以非零退出码结束。
10. 成功时输出一段可直接放入答辩材料的结构化摘要。

并发调度和响应分类必须严格：

- 本验收 worker 数必须等于请求数，或者使用不会因线程池小于 Barrier 参与者而死锁的父线程 start event/latch。
- ready/start 等待都必须有超时；线程未就绪属于验收失败。
- 成功只认 HTTP 201，且 `orderId`、`orderNo` 非空并在本轮唯一。
- 预期的 20 个失败只认后端明确返回的“库存不足”业务错误。
- 401、403、version conflict 外泄、retry exhausted、429、5xx、连接错误和超时都属于实现或基础设施失败，不能混入预期失败 20。
- 80 个成功订单必须属于本轮用户，每单包含指定测试商品和正确数量。

`/api/orders/direct` 是并发验收辅助入口，不在原始核心接口清单中。它必须复用 `/api/orders` 相同的库存、订单、明细和事务服务，不能维护另一套弱化逻辑。另行使用核心 `/api/orders` 购物车入口验证“购物车项 -> 扣库存 -> 订单 -> 明细 -> 清购物车”的完整事务和回滚；direct 入口不能代替核心入口验收。

测试数据隔离要求：

- 每轮生成唯一 `runId`，只统计本轮返回的订单号和订单 ID。
- 记录运行前 product stock/sales/version、订单数和明细数，并使用 delta 断言；即使初始值不是 0，也不能把绝对值误写成结果。
- 查询订单数量时必须按本轮用户、商品、地址/runId 或返回订单集合核对，禁止对历史 `order_item` 总数做等值断言。
- 优先使用独立 Compose project、独立数据库 schema 或专用测试商品；不得污染当前演示数据库。
- 若使用 `docker compose -p smart-mall-acceptance`，必须解决宿主端口冲突并确认 volume 名称独立。
- 隔离验收项目可以删除它自己明确创建的临时 volume；绝不能自动操作默认 `smart-mall` volume。若无法证明 volume 属于本轮脚本，立即暂停并请求用户批准。
- 关键 100/80 验收至少连续执行 3 次，每轮创建新商品，每次都必须恰好 80/20；只成功一次不能证明稳定。

不能仅凭当前数据库已有 `stock=0, sales_count=80, version=80` 推断某一次并发测试成功，因为数据库可能包含多轮历史数据。

### 阶段 3（P0）：让 Agent 验收稳定且诚实

保留真实 LangChain `create_tool_calling_agent`、`AgentExecutor`、工具调用和长期记忆，不得把 Agent 降级为纯规则路由器。

将测试分成两层：

#### A. 确定性测试

确定性测试不得访问外部 LLM。使用 FastAPI `TestClient`、httpx `MockTransport`、可控 fake model 或直接测试工具/编排纯逻辑。测试依赖放入明确的 dev/test requirements，不要污染生产镜像。

覆盖：

- 用户 JWT 必须由主后端校验，不能信任请求体 `userId`
- 工具不能跨用户查询订单或加购物车
- 商品卡片字段必须复用后端 API 返回值
- 搜索预算筛选正确
- 购物车必须先确认再执行
- 短期 session 相互隔离
- 长期偏好 upsert、权重封顶、读取 Top 3
- 非购物问题不调用业务工具
- LLM 不可用时离线兜底仍只能基于真实后端工具数据
- Agent 重启或内存为空后，指定 session 能从 `agent_conversation` 恢复最近窗口
- 新 session 只继承长期偏好，不泄漏旧 session 的短期任务状态、待确认加购或购买方案
- Agent 请求模型中的 `userId` 字段不得覆盖 JWT 校验得到的用户身份；可移除无效字段或显式拒绝不一致值
- 错误或缺失的内部服务凭据调用 `/api/internal/**` 必须被拒绝
- 服务凭据只能访问明确允许的订单/购物车内部接口，不能读取密码哈希或任意敏感用户数据
- `POST /agent/chat`、`GET /agent/history` 的 session 必须归属于当前认证用户，不能凭猜测 sessionId 读取他人历史
- 每轮 user/assistant 消息都持久化，role、sessionId、userId 和顺序正确
- Agent 只能直连 `agent_conversation`、`user_preference` 及明确属于 Agent 的知识表，禁止直连或写入 `user`、`product`、`order`、`order_item`、`cart` 业务表
- LLM 可用时偏好提炼按规格使用模型输出；规则提取只能作为离线兜底，不能让答辩中的“模型提炼长期偏好”实际从未调用模型

#### B. 真实 LLM 烟测

真实模型可能选择等价工具路径，断言业务能力而不是唯一工具名称。例如“对比商品1和2”可接受：

- 一次 `compare_products`；或
- 分别调用两个 `get_product_detail`，并基于两份真实结果完成对比。

但必须验证两个商品 ID 都被真实查询，回复中的名称、价格、库存与后端一致，不能只检查自然语言看起来合理。

烟测不能依赖回复中某个固定句子、Markdown 排版或是否出现 emoji。应优先校验结构化 `toolsUsed`、`ui.source`、商品卡片以及后端真实响应。对于必须出现的拒答、确认或偏好语义，只做最小的语义关键词断言。

真实烟测需要支持：

- `--require-llm`：答辩前强制真实模型可用，否则失败
- 明确输出使用的模型、工具列表、耗时与结果
- 超时或模型波动时输出真实错误，不能自动把失败写成通过

真实烟测数据必须动态准备：

- 使用唯一用户和唯一 session，不依赖 alice 的旧偏好
- 先从后端搜索获得两个当前存在且可售的商品 ID，不写死 1、2、45
- 开始前检查 `/health.llmEnabled=true`；`--require-llm` 未满足时在写购物车、偏好或对话数据前退出非零
- 等价工具路径以结构化 `ui.productCards` 或经过脱敏的 tool trace 中实际商品 ID 为证据，不通过解析自然语言猜测调用参数
- 逐字段对照实时后端商品数据，但不要求模型回复逐字重复名称、价格和库存
- 烟测结束后清理它创建的购物车项；无法安全清理的偏好/会话测试数据要使用清晰的 `smoke-*` session/runId 标记

如需要提高稳定性，可把 Agent 推理温度降到 0，但不要用硬编码回答替代模型工具选择。

同时检查 `agent-service/app/main.py` 是否存在重复函数定义、被后定义静默覆盖的逻辑或过长的单文件职责。只清理会影响行为、测试或可解释性的重复，不借机重写整个 Agent 服务。

### 阶段 4（P0）：补齐完整业务链集成测试

按项目规格使用 RestAssured 或 Newman。优先选择与 Spring Boot 测试体系一致的 RestAssured。

新增一条可一键执行的链路：

1. 注册唯一测试用户
2. 重复用户名返回 409
3. 登录并获取 access/refresh token
4. 未登录访问 profile 返回 401
5. 普通用户创建商品返回 403
6. 游客浏览、搜索和分页商品
7. 登录用户加入购物车
8. 创建订单
9. 查询订单详情
10. 模拟支付
11. 管理员发货
12. 用户确认收货
13. 非法状态跳转被拒绝
14. Refresh Token 能签发新 Access Token，禁用用户不能继续 refresh
15. 库存为 0 的商品不能加入购物车或直接购买
16. page 超过最大页时返回空列表而不是 500
17. 取消待付款订单后库存恢复且第二次取消被拒绝

测试必须断言响应码、关键 JSON 字段和最终状态，而不是只判断请求没有抛异常。

RestAssured 测试应使用随机端口和隔离测试数据。管理员账号可以使用测试夹具，不得依赖开发者本机已经登录或浏览器 localStorage。测试完成后不能修改正式 seed 文件中的演示订单状态。

明确区分两层并禁止混称：

1. **控制器集成测试**：`mvn test` 内的 RestAssured RANDOM_PORT 测试，可使用 H2 和测试代码创建的 fixture，用于验证 HTTP 契约、认证和状态机。
2. **部署后黑盒 E2E**：统一验收脚本对本轮重新构建的 Compose 栈发真实 HTTP 请求，使用真实 MySQL。只有这一层通过，才能在 PPT 中写“容器化端到端业务链通过”。

两层都使用唯一用户名。管理员凭据来自测试 profile、seed 测试账号或环境变量，不硬编码任何生产账号。非法跳转要写成具体断言，例如：

- `PAID -> cancel` 返回 400
- `COMPLETED -> pay` 返回 400
- 第二次 `confirm` 返回 400
- 第二次 `cancel` 返回 400，库存不再次变化

### 阶段 4.1（P0）：逐项回归五大核心模块

在继续安全和维护性工作前，按 AGENTS.md 接口规格检查：

#### 用户模块

- 注册成功返回 201
- 用户名和手机号重复返回 409
- 密码少于 6 位返回 400
- 用户不存在和密码错误统一返回 401“用户名或密码错误”
- profile 未登录 401，登录后可读写
- BCrypt 数据库值不是明文或 MD5
- axios 请求统一注入 Token，401 refresh 使用单飞锁，refresh 失败清理登录状态
- Access Token 有效期为 2 小时，Refresh Token 为 7 天，并有可验证的 claims/过期测试
- BCrypt cost 必须是默认 10，验收格式为 `$2a$10$...`、`$2b$10$...` 等 cost=10 BCrypt，而不是任意“兼容哈希”
- 商品游客白名单只对 GET 生效，受保护 Controller 通过统一 `CurrentUser`/`@AuthenticationPrincipal` 获取身份，不能各自解析 Token
- 登录、注册、个人中心是三个独立 Vue 路由；登录后刷新页面状态保持
- 课程要求可在 localStorage 观察 Token。若保留“记住我/本次会话”双模式，默认验收路径必须使用 localStorage，并在文档解释 sessionStorage 模式
- backend API 与 agent API 的 401 都必须有一致的 refresh/重新登录行为

#### 商品与搜索模块

- 分类树、商品列表、详情、搜索、排序、分页真实可用
- 管理员 CRUD 可用，普通用户为 403
- 搜索实现若实际使用 `LIKE` 降级，代码和文档都必须如实写明；不能仅因为 schema 有 FULLTEXT 索引就声称运行时使用了 ngram
- 商品图片上传必须核实。若尚未实现，补一个最小 ADMIN-only multipart 上传接口，保存到 `/uploads`，校验文件类型和合理大小，并通过 Spring Boot/Nginx 静态访问；不要接入 OSS
- 商品库存为 0 时前后端都阻止加购

#### 购物车与订单模块

- 加购、改数量、删除、选择结算和金额计算正确
- 下单仅处理当前用户拥有的购物车项
- 价格和商品名使用下单快照
- 支付、发货、确认、取消状态机严格
- 取消回补库存，事务失败无脏数据
- 订单列表具有全部/待付款/待发货/已完成/已取消筛选，订单详情具有清晰状态时间线

#### AI 助手模块

- 至少两个核心工具真实可演示：`search_products`、`query_order_status`
- 短期记忆窗口和数据库恢复可演示
- 长期偏好跨新 session 生效
- 服务间认证只能访问设计允许的订单/购物车内部接口

任何一项未通过，都回到对应实现修复；不要用前端隐藏按钮代替后端权限或状态校验。

### 阶段 5（P1）：安全配置收口

检查并处理：

- `application.yml` 中 JWT、数据库密码、内部服务密钥默认值
- `docker-compose.yml` 中 JWT 和内部服务密钥默认值
- Agent 中数据库 URL 和内部密钥默认值
- `.env.example`
- README 启动说明

目标：

1. 生产代码只从环境变量读取密钥。
2. 缺少 JWT 或内部服务密钥时应快速失败，并给出可理解的启动错误。
3. 不再自动补齐过短 JWT secret。
4. `.env.example` 只提供占位说明，不提交真实密钥。
5. `open-project.cmd` 如负责生成本地配置，只能生成随机开发密钥，不得写死公共密钥。
6. 日志、Swagger、健康检查和错误响应不得输出密钥。
7. Refresh Token 必须校验用户仍存在且状态正常。

例外边界：`application-test.yml` 可以使用固定、明确标注为非生产的测试密钥，以保证离线测试可重复；不得误删后让 `mvn test` 依赖本机 `.env`。`LLM_API_KEY` 始终是可选配置，缺失时 Agent 可以健康启动并标记 `llmEnabled=false`；只有 Full/Defense 验收的 `-RequireLlm` 模式才把它视为失败。

保持游客 GET 商品接口开放、受保护接口 401、普通用户管理接口 403。

#### 密钥外置与一键启动的冲突处理

AGENTS.md 同时要求“密钥不提交、从环境变量读取”和“Compose 一键启动”。不要通过重新加入公共默认密钥来假装同时满足两者。采用以下最小方案：

1. Java/Python 源码与 Compose 中不包含可用的固定密钥默认值。
2. 提供幂等的本地初始化入口，例如 `scripts/init-env.ps1`，在 `.env` 不存在时生成随机 JWT 和内部服务密钥；已存在时绝不覆盖。
3. `open-project.cmd` 先调用初始化入口，再执行 `docker compose up -d --build`。
4. README 明确区分首次初始化与后续一键启动。
5. 验收机器准备好 `.env` 后，`docker compose up -d --build` 必须无需继续手工修改配置。
6. 不得提交生成后的 `.env`，并验证 `.gitignore` 生效。

Compose 对必需变量使用 `${VAR:?清晰错误说明}` 或等价 fail-fast 方式。初始化脚本生成适合 `.env` 的 URL-safe/字母数字随机值，不能产生会被 Compose 特殊解析的字符。验收输出只显示“已配置/未配置”或非敏感摘要，绝不打印实际值。

如果现有课程验收人员坚持从全新 clone 直接执行裸 `docker compose up`，在最终报告中明确说明安全要求与裸命令之间的冲突，演示安全的初始化脚本；不要牺牲“密钥不得入库”来换取表面的一条命令。

### 阶段 5.1（P1）：Docker、日志与监控验收

验证而不是仅检查文件存在：

- 三个 Dockerfile 都能从 clean checkout 构建，不依赖宿主 `target/`、`dist/` 或 `node_modules/`
- MySQL 健康后后端再启动；后端可用后 Agent 再接受流量
- 前端 Nginx 的 `/api`、`/agent` 和 Vue history fallback 正确
- `/health`、Swagger UI 和 OpenAPI JSON 可访问
- OpenAPI 中能找到五大模块主要接口、认证要求、请求 DTO、响应码和错误响应；不能只验证 Swagger 页面空壳可打开
- 后端日志写入挂载的 `logs/` volume，容器重建后日志仍可查看
- 下单成功、库存冲突、订单取消回补、Agent 工具调用都产生 INFO 日志
- 日志包含必要业务 ID，但不记录密码、Token、LLM key 或内部密钥
- `docker compose logs backend` 和 `docker compose logs agent-service` 能用于答辩现场排障

最终部署验收不能只执行 `docker compose ps`：

1. 使用本轮源码执行 `docker compose up -d --build`，或在隔离项目中执行等价命令。
2. 轮询 MySQL health、backend `/health`、agent `/health` 和 frontend `/`，全部等待都有超时。
3. 通过前端 Nginx 反向代理实际请求一次 `/api/products` 和 Agent health/接口，证明代理链路不是只在容器内部可用。
4. schema/seed 改动必须在本轮新建的验收 volume 上验证，因为 MySQL initdb 不会更新已有 volume。
5. 不能用旧容器、旧镜像或旧 volume 的状态证明 clean checkout 可部署。

Logback 日志应采用稳定的 key=value 或 JSON-like 结构化字段，例如 event、userId、orderId、productId、result；不要为此引入重型日志依赖。

不要引入 Prometheus、Grafana、ELK 或新的日志平台。

### 阶段 6（P1）：数据库完整性与范围控制

首先区分“核心必答表”和“范围外扩展表”。不能让优惠券、积分、售后、评价、RAG 等扩展功能拖累五大核心模块。

检查：

- 所有声明关系的表是否真实存在外键
- 索引是否与查询一致
- `user.points` 是否与 `point_record` 构成可推导冗余
- `coupon.remain_count` 等汇总字段如何保持一致
- ER 图是否与实际 schema 一致
- JPA 字段长度、nullable、unique 是否与 MySQL DDL 一致

处理策略：

1. 默认交付 schema 中的所有表都必须满足 AGENTS.md 对 3NF、InnoDB、utf8mb4、字段解释和 ER 覆盖的要求，不能只保证核心表。
2. 能轻量补外键的扩展表补齐外键和必要索引；加外键前先查询孤儿数据，并优先在全新验收 schema 上验证，不直接 ALTER 用户现有 volume。
3. 对确有性能或历史语义原因的反范式字段，文档必须明确标注来源、维护事务和一致性策略。
4. 若扩展功能无法在合理范围内保证一致性，仅“答辩降级”不够：要么修复默认 schema，要么评估后将该扩展从默认交付 schema 和运行链路安全移除。涉及已有用户数据时必须先请求授权。
5. 不要为了追求形式上的范式大规模重写整个数据库。

由于 AGENTS.md 明确要求所有交付表满足其定义的 3NF，不能仅写一句“这是缓存字段”就结束。对每个可推导字段必须做出明确选择：

- 它是课程明确要求的业务事实或历史快照，并在文档中解释；或
- 移除冗余并从唯一事实来源计算；或
- 将对应范围外扩展功能降级/移出核心交付。

不得为了保留一个范围外页面，让数据库设计答辩无法自洽。未解决的扩展表同样会阻塞数据库验收通过。

还要检查 `agent_conversation`、`user_preference` 的 DDL 是否同时存在于 schema 和 Agent 启动代码并发生漂移。必须指定一个权威定义，并保证另一处不会创建字段、索引或约束不同的同名表。

最终数据库证据至少包括：

- 默认 schema 全部表为 InnoDB、utf8mb4
- 全新 seed 至少 3 个可登录用户、3 个分类、20 个商品
- `product.version` 在真实库存 SQL 中使用
- `order_item` 两个快照字段的历史事实解释
- ER 图覆盖默认 schema 中全部表和实际外键
- schema、JPA 实体、seed、ER 图与数据库 information_schema 一致

执行实际数据库校验：

```sql
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'smart_mall';

SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'smart_mall'
  AND REFERENCED_TABLE_NAME IS NOT NULL;
```

### 阶段 7（P1）：前端可维护性与性能

保持现有视觉方向，不进行全站重做。优先做低风险、高收益的整理：

1. 首先只做路由页面动态 import，并用构建产物证明 initial chunk 改善。
2. ECharts 保持按需加载，不进入普通用户首屏。
3. 只有路由组件按需 import 的 CSS 才能计入首屏性能优化；单纯把全局 CSS 搬到多个文件只算维护性整理，不能声称降低首屏。
4. 若继续拆分 `style.css`，按全局基础、商城、订单、管理、Agent 小步迁移，保持原导入顺序和 CSS cascade，每一步 build 并检查关键页面后再继续。
5. 将 `AdminView.vue` 按现有 tab 拆成少量职责明确的子组件；避免过度组件化。若没有可靠视觉对比能力，不进行大规模 CSS/Admin 迁移。
6. 删除仅由本次拆分产生的无用代码，不清理无关旧代码。
7. 添加最少但关键的前端测试：优先提取并测试路由守卫、401 refresh 单飞锁、SSE parser、购物车合计等纯函数；最多引入 Vitest/jsdom，不新增完整 E2E/组件测试体系。
8. 将前端测试接入 `npm test` 或明确的 `npm run test`，并纳入统一验收入口；不能写了测试却从不执行。
9. 构建后量化检查：入口 initial JS chunk 目标小于 500KB；懒加载的 admin/ECharts chunk 可以单列并报告原始和 gzip 体积，不能用含糊的“无大 chunk”。
10. 在桌面和手机视口检查导航、搜索框、商品卡片、购物车、订单、聊天窗，无重叠、裁切和横向溢出。

前端视觉验收优先使用 Browser 技能和真实运行页面。若浏览器能力不可用，可以使用仓库现有 Playwright 检查脚本，但必须说明未完成手工交互检查，不能拿旧截图冒充当前版本。

所有最终截图必须来自最终构建和最终 commit/worktree 状态。发现仓库里存在旧绿版、新蓝版等不同版本截图时，删除或替换哪些文件必须先确认其用途，不得混用到同一答辩材料。

不要为了性能移除可演示的 Agent 工具调用状态、商品卡片和错误提示。

### 阶段 8（P1）：文档、PPT和缺陷表统一

先列出 `docs/defense` 下所有 `.pptx`、`.pdf`、`.docx`、预览目录和生成源，确定唯一 canonical 交付组。若同时存在普通版和“最高分版”，根据 README、最近修改时间、source map 和用户说明选择唯一正式版本；无法判断时询问用户。旧版本明确标记 archived，不能两套同时更新后让答辩人员猜测。

建立单一事实来源，例如 `docs/acceptance-report.md`，只记录本次最终验收结果：

- 执行时间与环境
- Git commit/worktree 状态
- 四个服务状态
- 后端测试数量
- 前端构建结果和 chunk 信息
- 401/403 验证
- 100/80 并发最终值
- Agent 确定性测试结果
- Agent 真实 LLM 烟测结果
- 完整业务链结果
- 尚未通过的项目
- 每个结果对应的命令、退出码和日志/JSON证据位置

随后同步：

- `README.md`
- `docs/bug-list.md`
- `docs/architecture.md`
- `docs/api-spec.md`
- `docs/database-design.md`
- `docs/defense/ppt_source_map.md`
- `docs/defense/项目开发报告.md`
- 答辩 PPT 中的测试数字与结论
- `docs/ai-development-record.md`：记录本轮真实 Codex 提示词、关键决策、修改文件、发现的 AI 错误和人工验证方式
- `docs/er-diagram.md`、`docs/schema.sql`、`docs/seed.sql` 中与数据库验收有关的内容

规则：

1. 测试失败时必须标注失败或待修复。
2. `bug-list.md` 只有在自动化验收真正通过后才能把 B001 改成 Fixed。
3. Agent 采用等价工具路径通过时，应写清楚实际工具列表，不得声称调用了未调用的工具。
4. 不得用历史数据库状态代替清洁测试结果。
5. 不得伪造团队成员、分支、PR、Claude Code 截图或提交历史。
6. Git 协作不足要如实列为当前证据缺口，并为后续真实开发给出分支方案。
7. Claude Code/Codex 记录只能使用真实对话、终端结果和本轮修改，不得生成假截图。可以在文档中预留“由学生现场截图”的明确位置。
8. 文档完成后进行一次反向核对：从 PPT 每一个数字追溯到 acceptance report，再追溯到实际命令结果。

Codex 记录不能冒充课程要求中的 Claude Code 记录。如果仓库没有真实 Claude Code 对话/截图，必须把它列为非代码验收阻塞项，不能宣称满分。可以保存本轮真实 Codex 记录作为补充证据，并明确提醒学生使用真实 Claude Code 完成一个后续小修复、保留原始对话和截图。

同样，仓库没有真实模块分支和 PR 时不能靠文档补造。只有用户授权后，后续真实修改才可以从现在开始使用 `feature/order-module`、`feature/agent-service` 等分支、真实 commit 和 PR；历史缺口必须如实说明。

编辑 `.docx` 或 `.pptx` 时必须使用对应文档/演示技能，重新导出并渲染全部页面/幻灯片检查裁切、重叠、字号和事实一致性。渲染工具不可用时标记“二进制材料未更新/未完成视觉QA”，不能只改 Markdown 后声称 PPT/DOCX 已同步。

## 六、统一验收入口

在不引入 CI/CD 的前提下，提供一个 Windows 可执行的验收入口，例如：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_acceptance.ps1
```

定义两个明确档位：

1. **Quick/Core**：执行确定性的后端、前端、认证、RestAssured、Agent fake/离线测试和现有服务健康检查。它可以用于日常回归，但只能得出“核心确定性检查通过”。
2. **Full/Defense**：必须包含本轮全新 Compose/MySQL 栈、100/80 并发、部署后黑盒 E2E、`-RequireLlm` 真实模型烟测、seed/schema/log/Swagger 和材料一致性检查。只有 Full 全绿才允许在报告/PPT 写“全部验收通过”。

任何 mandatory 项为 `SKIPPED` 时，Full 状态必须是 `INCOMPLETE` 或 `FAIL`，不能退出 0 后宣布满分。Quick 成功也不能替代 Full。

该入口按顺序执行：

1. 环境和配置检查
2. 后端测试
3. 前端确定性测试
4. 前端构建
5. Docker Compose 配置与健康检查
6. 401/403安全检查
7. 完整业务链集成测试
8. Agent 确定性测试
9. Full 模式强制真实 LLM 烟测
10. Full 模式强制独立 MySQL 数据库并发验收
11. 汇总结果

任何必选步骤失败，入口必须返回非零退出码。Full 模式中的真实 LLM 和并发不是可选项。破坏性共享数据库重置仍必须通过参数明确启用；默认方案应使用脚本自己创建的隔离夹具或临时 Compose project，避免依赖破坏性重置。

建议参数：

```text
-RequireLlm
-RunConcurrency
-Mode Quick|Full
-AllowFixtureReset
-AcceptanceProjectName smart-mall-acceptance
-OutputDir docs/acceptance
```

验收入口还必须：

- 在开始时打印将使用的 base URL、Compose project、数据库和是否会修改数据
- 为每个步骤设置合理超时，不能永久等待
- 捕获子命令退出码，不能只打印错误后继续
- 在结束时生成机器可读 JSON 与人类可读 Markdown 摘要
- 每项输出 `PASS`、`FAIL` 或 `SKIPPED`；Full 中的强制项不允许 `SKIPPED`
- 默认不打印 `.env` 内容或任何密钥
- 重复执行时不会因为上次残留临时文件而误判

## 七、最终验收矩阵

最终至少满足：

| 项目 | 强制结果 |
|---|---|
| `mvn test` | 全部通过，包含新增订单、认证和业务链测试 |
| `npm run build` | 通过，无未解释的大首屏 chunk |
| Compose | 四个服务启动并健康 |
| JWT | profile 无Token为401，普通用户管理接口403，Access 2h、Refresh 7d、禁用用户refresh失败 |
| BCrypt | 数据库密码为cost=10的BCrypt格式，正确密码匹配、错误密码拒绝 |
| 商品搜索 | “键盘”能命中真实商品，分页越界返回空列表 |
| 商品管理 | ADMIN CRUD和本地图片上传可用，普通用户403 |
| 订单并发 | 100 请求、库存 80、成功 80、失败 20、stock 0 |
| 事务回滚 | 人工制造中途失败后无脏订单、脏明细或库存误扣 |
| 状态机 | 非法跳转拒绝，取消正确回补 |
| Agent 商品 | 基于真实后端商品，工具证据可见 |
| Agent 订单 | 只能查询当前认证用户订单 |
| Agent 长期记忆 | 新 session 体现已持久化偏好 |
| Agent 越界 | 天气等非购物问题不编造回答 |
| Agent 架构 | 不直连业务表，至少2个真实工具，内部服务凭据错误时拒绝 |
| 业务链 | 注册、登录、浏览、下单、支付、发货、确认可自动回归 |
| 前端回归 | `npm run test`通过，三独立用户路由、订单筛选和状态时间线可演示 |
| Seed | 全新数据库至少3用户、3分类、20商品，测试账号可登录 |
| 数据库 | 全部默认表InnoDB/utf8mb4，ER/schema/JPA一致，所有表3NF可解释 |
| Swagger | UI与OpenAPI可访问，主要接口和错误码可核对 |
| 日志 | 关键业务INFO日志可通过volume和docker logs查看且无密钥 |
| Clean build | 不依赖宿主构建产物，三个镜像均可重新构建 |
| 文档 | README、bug list、报告、PPT结论完全一致 |
| Git | 有真实模块分支/PR协作证据；若不存在则Full状态不得声称满分，且禁止伪造 |
| AI辅助开发 | 有真实Claude Code记录；Codex记录只能作为补充，不得替代课程指定证据 |

## 八、完成标准

只有同时满足以下条件才能宣布完成：

1. 必要代码已经实施，不是只给方案。
2. 新测试先能复现旧问题，修复后通过。
3. 所有关键命令已经实际执行。
4. 没有后台命令或测试仍在运行。
5. 没有覆盖用户原有未提交修改。
6. `git diff --check` 通过。
7. 文档数字来自本次执行结果。
8. 仍失败的项目被明确列出，不能隐藏。
9. 100/80并发在隔离环境连续3次通过，而不是只保留一次成功输出。
10. Agent真实LLM烟测与确定性测试的结果被分别报告。
11. 全新或隔离数据库的schema与seed验收通过。
12. README中的启动命令已经由本次实际执行验证。
13. Full 模式没有任何强制项为 `SKIPPED`。
14. 若真实 Claude Code、团队分支或 PR 证据缺失，最终只能报告“代码验收完成但课程满分证据不完整”，不能宣布整体满分。

## 九、最终回复格式

完成后用中文输出，先说结果，再说证据。格式如下：

### 完成结果

- 本次解决的 P0/P1 问题
- 是否达到全部验收标准

### 关键修改

- 文件路径 + 行为变化
- 为什么选择该最小方案

### 实际验证

- `mvn test`：测试数、失败数
- `npm run build`：结果和 chunk
- Compose：服务状态
- 并发：请求数、成功、失败、stock、sales_count、version、订单数
- Agent：模型、实际工具路径、记忆结果
- E2E：完整链路状态

### 未完成或残余风险

- 明确说明原因、影响和下一步

### 工作区说明

- 修改文件
- 原有未提交修改如何被保留
- 是否执行了 commit/push（默认不得执行）

不要使用“应该可以”“理论上通过”“大概没问题”等表述。没有执行的测试必须明确写“未执行”。

## 十、立即开始

现在开始执行，不要停留在计划阶段：

1. 使用任务计划列出 Gate A-E，任何时刻最多一个实施步骤为 `in_progress`。
2. 读取文件并建立基线，不修改代码。
3. 首先写出能稳定复现并发少卖或冲突处理不足的测试。
4. 实施最小修复，先跑针对性测试，再跑完整后端测试。
5. 完成黑盒并发、事务、状态机、认证和 RestAssured 链路，关闭 Gate B。
6. 完成 Agent 两层测试，关闭 Gate C。
7. 完成安全、Compose、seed、Swagger、日志和数据库检查，关闭 Gate D。
8. 只有前四个 Gate 稳定后，才拆分前端大文件和同步答辩材料。
9. 执行统一验收入口，生成最终报告并按规定格式交付。

每完成一个 Gate，立即记录：

- 修改了什么
- 哪条旧失败已被复现
- 执行了哪些命令
- 当前通过/失败结果
- 是否产生新的风险

不要一次修改十几个模块后再统一测试。出现回归时应能定位到最近一个 Gate。

遇到非破坏性问题自行排查。只有需要清空数据库 volume、覆盖冲突文件、安装系统软件、下载新的大型依赖、使用真实外部密钥、提交或推送 Git 时才向用户请求授权。

如果某个工具、浏览器、文档渲染器或外部模型不可用，继续完成不依赖它的工作，并在最终结果中精确说明哪项验收未完成。不得因为一个可选工具不可用就停止整个项目，也不得把未执行项标成通过。
