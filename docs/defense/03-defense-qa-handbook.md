# 答辩问答手册

使用方式：不要逐字背。每个问题只记回答结构、关键文件和必须承认的边界，现场用自己的话组织。

## 类别A：架构与设计决策类刁难

### A1：为什么 Agent 服务做成独立 Python 微服务，而不是直接写进 Spring Boot？

参考回答要点：

- 从职责边界说：Spring Boot 负责用户、商品、订单和事务；Python Agent 负责 LangChain 工具调用和记忆。
- 从生态说：LangChain、langchain-openai、FastAPI 在 Python 下集成成本更低。
- 从项目实现说：`agent-service/app/main.py` 独立提供 `/agent/chat`，工具通过 HTTP 调 `backend`，不直接写业务表。
- 从答辩演示说：独立 Agent 服务更容易证明“Agent 与现有系统集成”，而不是把 AI 逻辑混在业务 Controller 里。

### A2：为什么不引入 Spring Cloud Gateway、Nacos 这类微服务治理组件？

参考回答要点：

- 项目只有两个后端服务：Spring Boot 主系统和 FastAPI Agent。
- 实训目标是五大模块和 Agent 工具/记忆，不是微服务治理。
- 引入网关和注册中心会增加部署复杂度，偏离 15 天实训范围。
- 当前 `docker-compose.yml` 用服务名 `backend`、`agent-service` 已能满足容器内发现和通信。

### A3：为什么不使用消息队列处理下单？

参考回答要点：

- 当前验收规模是 100 并发抢 80 库存，MySQL 原子 UPDATE + `@Transactional` 足够保证正确性。
- MQ 会引入最终一致性、补偿、消息重复消费等额外问题。
- 项目要求重点是事务管理和乐观锁，`OrderService.createDirect` 与 `ProductRepository.deductStock` 已直接覆盖。
- 如果未来秒杀规模更高，可以再设计 Redis 预扣库存和 MQ 异步削峰。

### A4：为什么购物车不用 Redis，而是 MySQL 表？

参考回答要点：

- 购物车需要持久化、用户登录后跨设备可恢复，用 MySQL 简单可靠。
- 当前 `cart` 表有 `UNIQUE KEY uk_user_product(user_id, product_id)`，模型清晰。
- Redis 更适合高频临时缓存，但会增加缓存与数据库一致性问题。
- 实训范围强调 MySQL、事务和三范式，MySQL 购物车更便于答辩说明。

### A5：如果 Agent 服务挂了，商城主链路还能用吗？

参考回答要点：

- 能用。用户、商品、购物车、订单都在 Spring Boot 后端。
- 前端只有右下角聊天窗依赖 Agent；商品浏览和下单不依赖 Agent。
- 这体现了服务边界：Agent 是增强能力，不是主业务事务链路的一部分。
- 如果要生产化，可以给聊天窗做更友好的降级提示。

### A6：如果 MySQL 挂了，系统会怎样？

参考回答要点：

- 主业务和 Agent 记忆都依赖 MySQL，核心功能会不可用。
- `docker-compose.yml` 对 MySQL 配了 healthcheck，后端依赖 `service_healthy` 启动。
- 这不是高可用架构；实训项目只要求单 MySQL 容器。
- 生产环境需要主从、备份、连接池告警和故障恢复方案。

### A7：为什么前端只访问 `/api` 和 `/agent`，不直接访问 MySQL？

参考回答要点：

- 浏览器直接连数据库不安全，也不符合职责边界。
- 后端负责认证、权限、参数校验、事务和错误处理。
- Agent 也不能直接写业务表，只能通过后端 REST 工具查询商品/订单。
- 当前 Nginx 代理在 `frontend/nginx.conf` 中把 `/api/` 和 `/agent/` 转发到对应服务。

### A8：为什么后端宿主端口是 8081，不是规格里的 8080？

参考回答要点：

- 容器内部仍是 8080，`docker-compose.yml` 做了 `8081:8080` 映射。
- 这样可以避开宿主机 8080 端口冲突。
- README 明确写了后端 API 是 `http://localhost:8081`，容器内仍用 `backend:8080`。
- 本地脚本要注意：并发脚本默认 8080，compose 环境应传 `-BaseUrl http://localhost:8081`。

### A9：为什么保留单体业务后端，而不是把用户、商品、订单都拆成多个微服务？

参考回答要点：

- 五大模块复杂度不高，强行拆服务会带来分布式事务和部署复杂度。
- 用户、商品、订单共享同一个 MySQL，单体事务更容易保证下单一致性。
- 项目真正需要独立的只有 Agent，因为技术栈和职责不同。
- 这是“有限微服务化”：只拆有明确收益的 Agent。

### A10：系统最大的架构亮点是什么？

参考回答要点：

- 主业务和 Agent 边界清晰。
- Agent 通过工具调用真实后端 API，避免推荐幻觉。
- 订单使用事务 + 乐观锁，能讲清楚并发正确性。
- Docker Compose 把四个服务统一编排，便于演示和交付。

## 类别B：技术原理深挖类刁难

### B1：JWT 的签名机制具体是什么？Payload 被改了会怎样？

参考回答要点：

- JWT 由 Header、Payload、Signature 组成。
- 本项目 `JwtService` 用 JJWT 和 HMAC key 签名，密钥来自 `app.jwt-secret`。
- 服务端解析时用 `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` 验签。
- 如果 Payload 被篡改，签名验证失败，`JwtAuthenticationFilter` 清空上下文，后续访问受保护接口返回 401。

### B2：Access Token 和 Refresh Token 为什么要分开？

参考回答要点：

- Access Token 用于频繁请求，生命周期短，本项目 2 小时。
- Refresh Token 用于换新 Access Token，生命周期长，本项目 7 天。
- `JwtService` 在 Payload 里写 `type=access/refresh`，`AuthController.refresh` 只接受 `type=refresh`。
- 分开后即使 Access Token 过期，前端可自动刷新；Refresh 泄露风险更高，生产环境还要配合黑名单或轮换。

### B3：JWT 如果被截获怎么办？

参考回答要点：

- 在过期前可能被冒用，这是 JWT 无状态方案的典型风险。
- 项目通过短 Access 有效期降低风险，但没有实现主动吊销。
- 前端存 localStorage，实训可接受；生产环境要防 XSS、使用 HTTPS、考虑 HttpOnly Cookie 或 Token 黑名单。
- Payload 不放密码等敏感信息，只放 userId、username、role、type。

### B4：BCrypt 和 MD5 本质区别是什么？

参考回答要点：

- MD5 是快速摘要算法，不适合密码存储。
- BCrypt 是密码哈希算法，带盐、慢哈希、成本可调。
- 本项目 `SecurityConfig.passwordEncoder` 返回 `BCryptPasswordEncoder`，注册时 encode，登录时 matches。
- `PasswordEncoderTest` 验证 hash 以 `$2a$10$` 开头。

### B5：三范式具体怎么体现在表设计里？

参考回答要点：

- 用户、商品、分类、订单、订单项、购物车分别描述单一实体。
- 购物车只存 `user_id/product_id/quantity`，不冗余商品名和价格。
- 商品通过 `category_id` 关联分类，订单通过 `user_id` 关联用户。
- Agent 记忆表独立，不把偏好标签塞到用户表多个字段里。

### B6：订单项里的商品名和价格快照违反三范式吗？

参考回答要点：

- 要承认这是有意的非规范化，但不是设计错误。
- 下单时的商品名和价格是历史事实；商品表当前名称和价格可能改变，不能可靠推导历史订单。
- `order_item.product_name_snapshot`、`price_snapshot` 用于审计和展示历史订单。
- 这类“历史快照”是业务事实记录，答辩时要主动说明。

### B7：乐观锁和悲观锁区别是什么？为什么这里选乐观锁？

参考回答要点：

- 悲观锁先锁住资源，冲突少时会降低并发。
- 乐观锁在更新时检查版本或条件，失败再处理。
- 本项目用 `where stock >= :quantity and version = :version`，影响行数为 0 就失败回滚。
- 实训规模下乐观锁简单、吞吐较好，不需要长事务锁。

### B8：Spring `@Transactional` 的作用是什么？

参考回答要点：

- 把一组数据库操作放进同一事务。
- 本项目 `OrderService.create` 同时保存订单、扣库存、保存订单项、清购物车。
- 任一步抛运行时异常，事务回滚，避免库存扣了但订单没生成。
- 事务边界放 Service 层，不放 Controller，因为 Service 表达业务用例。

### B9：JPA bulk update 为什么加 `clearAutomatically = true`？

参考回答要点：

- JPQL bulk update 直接更新数据库，不会自动同步一级缓存中的实体状态。
- 如果缓存还保留旧 Product，后续读取可能看到旧 stock/version。
- `ProductRepository.deductStock` 和 `restoreStock` 加了 `flushAutomatically`、`clearAutomatically`。
- 这是为避免库存更新后实体状态不一致。

### B10：LIKE 搜索和 MySQL 全文检索有什么区别？

参考回答要点：

- LIKE 简单直观，但大数据量下性能差，难做复杂相关性排序。
- FULLTEXT + ngram 更适合中文关键词搜索，但配置和 SQL 更复杂。
- 本项目 seed 创建了全文索引，但当前 `ProductRepository.search` 实际用 LIKE 兜底。
- 这是实训允许的简化，答辩不能把当前实现说成 MATCH 查询。

## 类别C：并发与数据一致性刁难

### C1：100 个人同时抢库存 80 的商品，怎么保证不超卖？

参考回答要点：

- 下单方法 `OrderService.createDirect` 有 `@Transactional`。
- 扣库存调用 `ProductRepository.deductStock`，条件包含 `stock >= quantity` 和 `version = version`。
- 数据库原子 UPDATE 保证同一时刻只有满足条件的更新成功。
- 影响行数为 0 时抛库存不足，事务回滚。

### C2：为什么既检查 stock 又检查 version？只检查 stock 不行吗？

参考回答要点：

- `stock >= quantity` 保证不会扣成负数。
- `version = :version` 保证更新基于自己读到的版本，检测并发冲突。
- 两层保护结合，比只靠 Java 判断库存可靠。
- 版本冲突时可以提示用户重试或库存不足。

### C3：如果扣库存成功，但插入订单项失败，会发生什么？

参考回答要点：

- 因为在同一个 `@Transactional` 方法里，异常会导致整个事务回滚。
- 扣库存 UPDATE、订单主表、订单项都回滚。
- 用户看到业务错误或系统繁忙，不会留下库存扣了但订单没有的脏数据。
- 这就是下单复合操作必须放事务的原因。

### C4：购物车下单里先保存订单再扣库存，会不会留下空订单？

参考回答要点：

- `OrderService.create` 的确先 `orders.save(order)`，再循环扣库存和写订单项。
- 但它们在同一事务内，库存不足抛 `BizException` 会回滚前面的订单保存。
- 所以数据库不会最终留下空订单。
- 这个顺序主要是为了先拿到订单 id 供订单项关联。

### C5：取消订单时库存回滚是否并发安全？

参考回答要点：

- 之前识别过“库存回滚非并发安全”风险。
- 当前已修复：`ProductRepository.restoreStock` 用原子 update 增加 stock、version，并调整 salesCount。
- `OrderService.cancel` 在事务里要求订单状态为 `PENDING_PAYMENT`，再回补库存。
- 仍可补更严格测试：并发取消与下单同时发生时核对最终库存和订单状态。

### C6：订单状态非法跳转怎么防？

参考回答要点：

- 后端不是靠前端隐藏按钮防，而是 `OrderService.requireStatus`。
- `pay` 要求 `PENDING_PAYMENT`，`ship` 要求 `PAID`，`confirm` 要求 `SHIPPED`，`cancel` 要求 `PENDING_PAYMENT`。
- 不符合状态时返回“订单状态不允许该操作”。
- 前端只是辅助展示，后端才是最终规则。

### C7：为什么并发测试用 `/api/orders/direct`，不走购物车？

参考回答要点：

- 并发验收目标是库存扣减事务，不是购物车唯一键。
- 购物车有同一用户同一商品唯一约束，会干扰并发结果。
- `/orders/direct` 仍走 `OrderService.createDirect` 的事务和扣库存 SQL。
- 这是实训专用端点，用于更干净地验证库存安全。

### C8：并发脚本怎样确认成功订单数恰好为 80？

参考回答要点：

- `scripts/concurrent_order_test.ps1` 会输出 success 和 failed 数量。
- 抢购商品 seed 库存是 80，100 请求理论上应 success=80、failed=20。
- 还应查询商品 21 的最终 stock 为 0，并核对订单数量。
- 当前 B001 仍为 Open，说明需要在联调环境完成这一步回归证据。

### C9：如果两个请求同时读到 version=0，谁会成功？

参考回答要点：

- 两个请求都带 version=0 去 UPDATE。
- 数据库先执行成功的那个会把 version 改为 1。
- 另一个请求再执行时 `version = 0` 条件不成立，影响行数为 0。
- 后端把影响行数 0 当作库存不足或冲突处理。

### C10：销量 `salesCount` 并发更新安全吗？

参考回答要点：

- 扣库存 SQL 同时 `salesCount = salesCount + :quantity`，是数据库原子更新。
- 取消订单回补时用 case 防止销量扣成负数。
- 这比 Java 里先读销量再加减更安全。
- 更严格的业务可能需要区分“已支付销量”和“待付款占用”，当前为实训简化。

## 类别D：LangChain Agent 原理与实现刁难

### D1：Agent 是怎么“决定”调用工具的？

参考回答要点：

- LLM 根据 system prompt、用户问题、工具描述生成工具调用请求。
- LangChain 的 `create_tool_calling_agent` 把工具和 prompt 绑定。
- `AgentExecutor` 执行工具并把结果放回推理过程。
- 本项目要求商品问题调用 `search_products`，订单问题调用 `query_order_status`。

### D2：工具调用和普通接口调用有什么区别？

参考回答要点：

- 普通接口调用是代码固定调用某个 API。
- Agent 工具调用是模型根据语义决定调用哪个工具、参数是什么。
- 工具结果再作为上下文影响最终回复。
- 但本项目也有离线兜底路径，用规则决定工具，这是降级方案，不是主要 LLM Agent 路径。

### D3：怎么防止 Agent 编造不存在的商品？

参考回答要点：

- system prompt 明确“推荐商品时必须基于工具返回的真实商品数据，不要编造”。
- `search_products` 工具调用后端 `/api/products/search`，返回真实商品名、价格、库存。
- 前端显示 `toolsUsed`，烟测检查工具是否被调用。
- 仍不能 100% 消除 LLM 幻觉，生产可要求最终答案只从结构化工具结果中选择。

### D4：短期记忆和长期记忆分别存在哪里？

参考回答要点：

- 短期记忆：`memory` 字典中的 deque，key 是 `userId:sessionId`，最多 12 条。
- 兜底持久化：每轮对话写 `agent_conversation`。
- 长期记忆：`user_preference` 表，存 `user_id`、`preference_tag`、`weight`。
- 新会话会读取权重最高 3 个偏好注入 system prompt。

### D5：长期偏好是怎么提取的？

参考回答要点：

- `extract_preferences` 先用规则识别明显标签，如机械键盘、预算敏感、数码产品、办公学习。
- 规则没命中但有偏好信号时，再调用 LLM 要求返回 JSON 标签数组。
- `upsert_preferences` 插入或更新标签，已有标签权重 +0.1，封顶 2.0。
- 这样兼顾离线可演示和 LLM 提炼能力。

### D6：Agent 查询订单时怎么知道查哪个用户？

参考回答要点：

- 前端聊天请求传 `userId` 和 `sessionId`，未登录时聊天窗要求先登录。
- Agent 工具 `query_order_status(user_id, order_id)` 用该 userId 调后端内部接口。
- 后端内部接口还要求服务间密钥，不是任意匿名查询。
- 生产中还要进一步防止前端篡改 userId，例如让 Agent 验证用户 JWT 或由后端代理转发。

### D7：服务间鉴权为什么不用用户 JWT？

参考回答要点：

- 用户 JWT 代表用户本人，不应该随意交给 Agent 长期使用。
- 服务间调用用独立凭据更清楚，当前是 `X-Internal-Service` + `X-Internal-Secret`。
- 后端只开放受限内部订单摘要，不暴露敏感字段。
- 这是轻量方案，生产可换成 mTLS 或服务 Token。

### D8：为什么之前 Agent 长期是离线兜底模式？怎么发现的？

参考回答要点：

- 现象：`/health` 里 `llmEnabled=false`，日志显示 `langchain agent unavailable, using offline fallback`。
- 根因：`LLM_API_KEY` 未通过 `.env` 注入或为空。
- 修复：用 `.env` 配置 LLM 密钥和模型，compose 通过 `env_file` 和环境变量传给 Agent，不硬编码密钥。
- 验证：`/health` 变 true，提问键盘推荐看到 `toolsUsed=search_products`。

### D9：预算正则误伤“推荐3款键盘”这个 bug 是什么？

参考回答要点：

- 原正则把单位部分设为可选，任何数字都可能被当预算。
- “推荐3款键盘”被解析为最高 3 元，过滤掉所有商品。
- 当前 `extract_budget` 要求预算语境，如“预算/不超过/元/以内/¥”。
- 这个例子说明规则兜底也要做边界测试。

### D10：LLM 不调用工具是否一定是失败？

参考回答要点：

- 不是。非购物问题正确行为就是不调用工具并礼貌拒答。
- 之前有 bug 把“不调用工具”当失败并用离线模板覆盖。
- 当前 `handle_chat` 只在 LLM 异常或回复为空时兜底。
- `agent_smoke.py` 验证天气问题不应使用工具。

### D11：为什么要做流式输出？

参考回答要点：

- Agent 可能有 LLM、工具、偏好提取多个耗时步骤，普通请求会让用户等待完整结果。
- `/agent/chat/stream` 用 SSE 推送 token 和工具状态。
- 前端 `ChatWidget.vue` 显示“正在检索真实商品和库存”等状态，降低等待焦虑。
- `AgentTiming` 日志还能定位慢在哪个阶段。

### D12：如果 LLM_API_KEY 没配置，项目还能演示吗？

参考回答要点：

- 能演示主链路，但要说明是离线兜底。
- `offline_agent` 仍调用真实 `search_products` 和 `query_order_status` 工具。
- `/health` 的 `llmEnabled` 可以区分真实 LLM 路径和兜底路径。
- 答辩时最好配置真实 LLM，避免被认为没有真正 LangChain Agent。

## 类别E：DevOps 与部署刁难

### E1：Docker 容器化解决了什么实际问题？

参考回答要点：

- 统一 MySQL、Java、Python、Node/Nginx 环境。
- 新机器只需 Docker Compose 和 `.env`，不用手动分别配置服务。
- 容器内通过服务名通信，减少本地端口和环境差异。
- 日志可用 `docker compose logs` 统一查看。

### E2：docker-compose 里各服务怎么互相发现？

参考回答要点：

- compose 默认创建网络，服务名可作为 DNS 名。
- 后端连数据库：`mysql:3306`。
- Agent 连后端：`http://backend:8080`。
- 前端 Nginx 代理后端：`http://backend:8080/api/`，代理 Agent：`http://agent-service:8000/agent/`。

### E3：`depends_on` 是否保证后端启动时 MySQL 一定可用？

参考回答要点：

- 当前 MySQL 配了 healthcheck，backend 使用 `condition: service_healthy`，比普通 depends_on 更可靠。
- Agent 对 backend 是 `service_started`，不等于业务完全可用。
- 生产环境还要做应用层重试和健康检查。
- 实训环境下 MySQL healthcheck 已能解决主要启动顺序问题。

### E4：为什么后端 Dockerfile 当前是多阶段 Maven 构建？

参考回答要点：

- 当前 `backend/Dockerfile` 第一阶段 Maven 编译，第二阶段 JRE 运行。
- 这样 clean checkout 也能构建，不依赖宿主已有 jar。
- 历史上为了绕过网络慢，曾用本机预编译 + 单阶段复制 jar。
- 后来发现 clean checkout 无 jar 会失败，B011 修复为多阶段构建。

### E5：这是否和之前“本机预编译更快”的说法矛盾？

参考回答要点：

- 不矛盾，是不同阶段的工程权衡。
- 网络不稳定时，本机预编译能快速推进演示。
- 最终交付要求可复现构建，所以改回多阶段。
- 答辩要讲清楚“临时折中”和“最终实现”不同。

### E6：Docker Hub 网络失败和 Maven 超时是怎么处理的？

参考回答要点：

- 过程里经历 Docker Hub 认证连接失败、镜像加速器、手动预拉镜像、Maven 容器内下载依赖超时。
- `dependency:go-offline` 会拉大量插件和依赖，网络差时扩大失败面。
- 最终当前版本仍用多阶段，但配了 `settings.xml` 镜像源，并通过 `.dockerignore` 减少上下文干扰。
- 经验是网络问题要分层定位：镜像拉取、依赖下载、文件系统复制分别看。

### E7：`.docker` 权限问题到底是什么？

参考回答要点：

- 曾经误判为权限故障。
- 后来用 `icacls` 验证权限正常。
- 真正问题更像是执行环境和错误诊断混淆，Codex 某次把失败归因到权限并尝试提权。
- 这个案例说明排错要用命令验证，不靠报错直觉。

### E8：前端为什么需要 `.dockerignore`？

参考回答要点：

- Docker build 上下文默认包含当前目录。
- 如果把 Windows 宿主 `node_modules` 复制进 Linux 容器，会导致构建失败或依赖污染。
- 当前 `frontend/.dockerignore` 排除 `node_modules`、`dist`、日志、Git 和 IDE 文件。
- B012 已记录为 Fixed。

### E9：日志和监控怎么做？

参考回答要点：

- 后端 `application.yml` 配 `logging.file.path: logs`，compose 挂载 `backend_logs:/app/logs`。
- `OrderService` 记录下单成功/失败、支付、发货、取消回补。
- Agent 记录工具调用、LLM 是否启用、耗时 breakdown。
- 没上 Prometheus/Grafana，是实训范围内的轻量监控。

### E10：如果 `.env` 里没有 LLM_API_KEY，会不会导致 compose 启动失败？

参考回答要点：

- 不会，Agent 会启动，`llmEnabled=false`。
- 聊天走 offline fallback，仍能调用真实商品/订单工具。
- 但答辩要展示真实 LangChain Agent，最好配置有效密钥。
- `.env` 被 `.gitignore` 排除，避免密钥进仓库。

## 类别F：测试与质量保证刁难

### F1：单元测试具体覆盖了什么？

参考回答要点：

- `PasswordEncoderTest` 覆盖 BCrypt hash 格式、正确密码匹配、错误密码不匹配。
- `OrderStockSqlTest` 覆盖库存扣减使用 version 条件，旧版本更新失败。
- 都针对高风险基础能力：认证安全和并发库存。
- 覆盖面不算完整，后续应补更多订单状态和 Controller 测试。

### F2：集成测试和单元测试区别是什么？

参考回答要点：

- 单元测试测小范围逻辑或单个组件。
- 集成测试测多个模块和服务之间的协作。
- 本项目 Agent smoke 脚本跨 Agent、后端和数据库验证工具调用。
- 并发脚本跨登录、JWT、订单接口和库存 SQL。

### F3：Agent smoke 脚本测了什么？

参考回答要点：

- 商品推荐问题要求使用 `search_products`。
- 订单问题要求使用 `query_order_status`。
- 新 session 推荐要体现机械键盘长期偏好。
- 天气问题不能调用工具，验证非购物拒答。

### F4：并发测试脚本有什么不足？

参考回答要点：

- 默认端口是 8080，compose 环境要传 8081。
- 当前只统计 success/failed，没有自动查最终库存和订单数。
- 依赖 seed 数据状态，如果旧 volume 没重置，库存可能不是 80。
- B001 仍为 Open，需要联调环境复测。

### F5：如果现在新增一个测试，你优先测什么？

参考回答要点：

- 优先补订单完整链路集成测试：登录、加购物车、下单、支付、查询订单。
- 或补取消订单回补库存并发场景，因为它曾经是 bug。
- 还可以补 `AuthController.updateProfile` 只传头像不清空 phone。
- 原则：优先测高风险、曾出 bug、答辩高频追问的模块。

### F6：为什么库存 SQL 测试用 H2，也能说明问题吗？

参考回答要点：

- H2 的测试能验证 Repository JPQL 逻辑和 version 条件的行为。
- 但不能完全替代 MySQL 并发验收，尤其是锁和隔离细节。
- 所以项目还提供 PowerShell 并发脚本打真实后端。
- 答辩时不要把 H2 单测说成完整并发验收。

### F7：缺陷清单有什么作用？

参考回答要点：

- `docs/bug-list.md` 记录问题、复现步骤、状态和修复说明。
- 它体现缺陷管理流程，而不是只展示最终代码。
- B003、B004、B005 等说明真实发现和修复过问题。
- B001 Open 说明仍有待回归风险，诚实比掩盖更好。

### F8：前端有没有测试？

参考回答要点：

- 当前没有专门的前端单元测试框架。
- 前端质量主要通过构建、手动验收和端到端演示保证。
- 这是一个不足，后续可以引入 Vitest 或 Playwright。
- 但关键交互错误处理已在代码中体现，如购物车超库存 toast。

### F9：怎么验证未登录访问受保护接口返回 401？

参考回答要点：

- 可用 Swagger/Postman 直接请求 `/api/user/profile`，不带 Authorization。
- `SecurityConfig.authenticationEntryPoint` 返回 401 和 `{"msg":"未登录或Token无效"}`。
- 前端路由也会挡住未登录页面，但后端验证才是关键。
- 登录后带 Bearer Token 再访问应返回用户资料。

### F10：如何验证密码不是明文？

参考回答要点：

- 看数据库 `user.password_hash`，seed 中是 `$2a$10$...`。
- 注册新用户后查询数据库，也应是 BCrypt 格式。
- `PasswordEncoderTest` 也验证了格式和 matches。
- 登录时统一返回“用户名或密码错误”，避免枚举用户名。

## 类别G：AI 辅助开发反思类

### G1：你具体怎么用 Claude Code/Codex 的？

参考回答要点：

- 用于需求拆解、生成代码初稿、排查错误、整理文档和答辩材料。
- 例如生成多模块目录、Controller/Service、Vue 页面、Agent 工具和 Docker 配置。
- 不是直接相信输出，而是读源码、跑测试、看日志验证。
- 本次答辩材料也先读取真实代码再写，避免模板化。

### G2：AI 生成的代码怎么验证正确性？

参考回答要点：

- 静态读关键类和方法，例如 `OrderService`、`ProductRepository`、`main.py`。
- 跑单元测试和 smoke 脚本。
- 用前端手动演示主链路。
- 看 Docker 和应用日志确认真实执行。

### G3：有没有发现 AI 或自动化过程给出错误方向？

参考回答要点：

- 有。`.docker` 权限问题曾被误判为权限故障，后来用 `icacls` 排除。
- Docker 后端构建方案也经历过本机预编译和多阶段构建的反复权衡。
- 预算正则也是典型“小规则看似合理但边界错”的问题。
- 这些说明 AI 需要人工审核，不是生成即正确。

### G4：AI 辅助开发最大的收益是什么？

参考回答要点：

- 多技术栈项目能快速搭骨架：Spring Boot、Vue、FastAPI、Docker、文档。
- 重复性代码和接口 DTO 生成效率高。
- 排错时能提供候选方向。
- 文档和答辩材料整理速度明显提升。

### G5：AI 辅助开发最大的风险是什么？

参考回答要点：

- 容易产生“看起来完整但没真实验证”的代码或文档。
- 可能夸大实现，例如把 LIKE 搜索说成全文检索。
- 可能误判环境错误，如权限和网络问题。
- 需要用源码、测试、日志和 Git 约束。

### G6：如果完全不用 AI，这个项目要多久？

参考回答要点：

- 取决于团队熟悉度，但三端加 Docker 和 Agent，手写会明显更慢。
- 可能需要数周而不是 15 天内完成可演示版本。
- AI 节省的是初稿和排错时间，不替代需求判断和验收。
- 核心模块仍需要人工理解后讲清楚。

### G7：AI 生成的文档如何避免和代码不一致？

参考回答要点：

- 先读真实文件再写文档。
- 每个实现细节绑定文件名、类名、方法名。
- 对未实现或不一致的地方明确标注。
- 本次文档就指出当前搜索是 LIKE，B001 仍 Open，Git 分支证据不足。

### G8：AI 在 Agent 模块开发中扮演了什么角色？

参考回答要点：

- 帮助组织 LangChain Agent、工具、记忆和 FastAPI 接口结构。
- 但工具是否真实调用、记忆是否入库，要通过 `agent_smoke.py`、`/health` 和日志验证。
- 离线兜底和 LLM 路径也需要人区分。
- Agent 本身是项目功能，AI 辅助开发是开发过程，两者不要混淆。

### G9：如果 AI 建议引入很多新技术，你怎么判断要不要用？

参考回答要点：

- 回到教学大纲和验收清单。
- 是否解决当前真实问题，而不是增加展示复杂度。
- 是否能在 15 天内稳定演示。
- 本项目拒绝了 MQ、Redis 购物车、Elasticsearch、Spring Cloud Gateway 等过度设计。

### G10：如何保留 AI 辅助开发证据？

参考回答要点：

- Git 提交记录、缺陷清单、生成记录截图、对话摘要都可作为证据。
- 当前仓库有提交历史和 `docs/bug-list.md`。
- 若老师要求 PR 协作证据，需要诚实说明当前仓库没有保留 feature 分支/PR。
- 后续应把关键 AI 生成过程截图放入答辩材料附件。

## 类别H：综合应变类

### H1：如果要加商品评价功能，你怎么设计？

参考回答要点：

- 新增 `product_review` 表：id、user_id、product_id、order_item_id、rating、content、created_at、status。
- 限制已购买用户才能评价，可通过 order_item 验证。
- 商品平均分不要直接随便冗余，初期可查询聚合；性能需要再做统计表。
- 前端商品详情新增评价列表和评价表单。

### H2：如果要支持多商家入驻，现有架构怎么改？

参考回答要点：

- 新增 merchant/store 表，product 加 merchant_id。
- 管理后台权限从 ADMIN/USER 扩展为平台管理员、商家、普通用户。
- 订单可能拆单：一个购物车下单按商家拆成多个子订单。
- Agent 推荐也要考虑商家、库存和权限边界。

### H3：如果要接真实支付，需要改哪些地方？

参考回答要点：

- 订单状态要加支付单、支付流水、回调验签和幂等处理。
- `PUT /orders/{id}/pay` 不能直接改 PAID，而是创建支付请求，等待支付平台回调。
- 需要处理支付成功但回调延迟、重复回调、支付超时。
- 当前模拟支付是实训范围内简化。

### H4：如果搜索数据量从 20 条变成 100 万条怎么办？

参考回答要点：

- 当前 LIKE 会成为瓶颈。
- 可先使用 MySQL FULLTEXT ngram，进一步可引入 Elasticsearch/OpenSearch。
- 需要建立索引、相关性排序、同步策略。
- 但实训项目当前数据量小，LIKE 兜底足够演示。

### H5：这个项目最大的技术亮点是什么？

参考回答要点：

- Agent 不是静态聊天，而是用 LangChain 工具调用真实商品/订单接口。
- 有短期会话和长期偏好记忆。
- 订单下单用事务 + 乐观锁处理并发库存。
- Docker Compose 把多服务整合起来。

### H6：这个项目最大的不足是什么？

参考回答要点：

- 并发验收 B001 仍需联调回归，脚本还应自动核对 stock。
- 搜索实际是 LIKE，不是真正全文检索查询。
- Git 分支/PR 协作证据不足。
- 服务间鉴权是共享密钥，生产级安全还要加强。

### H7：如果老师问“这是不是生产级系统”，怎么回答？

参考回答要点：

- 不是生产级商业系统，是实训项目。
- 目标是五大模块可演示、技术栈落地、架构和 Agent 原理讲清楚。
- 已做关键正确性：JWT、BCrypt、事务、乐观锁、Docker、日志。
- 生产还要补高可用、支付、监控告警、审计、安全加固、CI/CD。

### H8：如果要把 Agent 做得更可靠，你下一步做什么？

参考回答要点：

- 工具结果改为结构化数据，最终回复严格引用工具结果。
- 加用户 JWT 校验或后端代理，防止前端篡改 userId。
- 对偏好提取做异步队列或后台任务，减少对话等待。
- 增加 Agent 回归测试和工具超时降级策略。

### H9：如果用户问天气、新闻、股票，为什么不回答？

参考回答要点：

- 系统定位是智能商城导购助手，不是通用助手。
- 回答非购物问题会增加幻觉和责任边界风险。
- `system_prompt` 明确只能回答购物、推荐、订单问题。
- `agent_smoke.py` 也验证天气问题不调用工具。

### H10：如果数据库旧 volume 导致 seed 数据没有重新导入怎么办？

参考回答要点：

- README 已提醒 MySQL volume 已存在时 `docs/seed.sql` 不会自动重跑。
- 答辩前要确认 `alice` 有订单 `SM202606300001`，商品 21 库存为 80。
- 可清理旧 `mysql_data` volume 或手动执行 seed 中相关 SQL。
- 这是 Docker volume 持久化的正常行为，不是 seed 脚本失效。

### H11：如果前端显示登录了，但后端仍返回 401，怎么排查？

参考回答要点：

- 看 localStorage 是否有 accessToken 和 refreshToken。
- 看 axios 请求是否带 `Authorization: Bearer ...`。
- 看 Token 是否过期，refresh 是否成功。
- 看后端 `JwtAuthenticationFilter` 是否解析失败，必要时重新登录。

### H12：如果让你现在重构一个地方，你会选哪里？

参考回答要点：

- 优先补测试和验收脚本，而不是大改架构。
- 并发脚本自动查询最终库存和订单数量。
- Agent 鉴权改成更严谨的用户身份验证。
- 搜索从 LIKE 切换到真正 MySQL FULLTEXT，前提是时间允许。
