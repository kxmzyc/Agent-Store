# 项目实施全过程复盘

这份复盘按真实工程过程组织，不写成“一路顺风”的成功叙事。答辩时更有价值的是说明如何定位问题、如何权衡方案、哪些地方仍有技术债。

## 阶段：需求理解与模块拆解

### 做了什么

项目一开始按教学大纲和最终项目规格拆成五大模块：用户、商品、订单、搜索、AI 智能推荐助手。架构上选择“Vue3 前端 + Spring Boot 主业务后端 + FastAPI/LangChain Agent 服务 + MySQL”的前后端分离加独立 Agent 微服务方案。目录最终对应为 `frontend/`、`backend/`、`agent-service/`、`docs/` 和根目录 `docker-compose.yml`。

### 遇到的问题（如果有）

最容易偏题的风险是把项目做成生产级电商系统：引入 Redis、消息队列、Spring Cloud Gateway、OAuth、真实支付、Elasticsearch 等。教学大纲的目标不是高并发秒杀系统，而是五大模块可演示、技术栈全部落地、能讲清楚 Agent 工作原理。

### 问题的根本原因

智能商城题目听起来像完整商业系统，但实训周期只有 15 天。过度追求生产级基础设施会挤占核心模块开发时间，尤其是用户认证、订单事务、Agent 工具调用和记忆机制这些验收必问点。

### 解决方案

按“先打通主链路再优化细节”执行：业务系统保留单体 Spring Boot，Agent 独立为 Python 微服务；订单并发用 MySQL 事务和乐观锁，不上 MQ；搜索实际用 LIKE 兜底，不上 Elasticsearch；支付使用模拟支付；监控用日志和 health 接口，不上 Prometheus/Grafana。

### 这次解决方案带来的启示/原则

实训项目要围绕评分点做架构决策。技术选型不是越多越好，而是能否解释“为什么这里需要它、它解决了什么问题、它有没有被真实用起来”。

## 阶段：数据库设计与 seed 数据准备

### 做了什么

数据库围绕用户、分类、商品、购物车、订单、订单项、Agent 会话、用户偏好设计。`docs/seed.sql` 创建全部表，使用 InnoDB 和 utf8mb4，插入 3 个测试用户、6 个分类节点、21 个商品、演示订单和初始偏好。`docs/er-diagram.md` 用 Mermaid ER 图说明关系，`docs/architecture.md` 说明 Agent 只直接写专属记忆表。

### 遇到的问题（如果有）

商品数据曾出现中文乱码：前端静态文字正常，但动态商品数据乱码。这说明不是浏览器字体或 Vue 模板问题，而是后端、数据库连接或 MySQL 字符集链路的问题。

### 问题的根本原因

中文数据要经过 seed SQL 文件、MySQL 表字符集、JDBC URL、Spring 响应编码、前端解析多个环节，任一环节不是 UTF-8/utf8mb4 都可能出现乱码。前端静态文字正常说明前端源码编码没问题，问题更可能在动态数据来源链路。

### 解决方案

当前代码中，`docs/seed.sql` 每张表都指定 `DEFAULT CHARSET=utf8mb4`；`docker-compose.yml` 的 MySQL command 指定 `--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci`；后端 `application.yml` 的 JDBC URL 包含 `useUnicode=true&characterEncoding=utf8`，并配置 `server.servlet.encoding.charset=UTF-8`、`force=true`；前端 `nginx.conf` 设置 `charset utf-8`。这些配置共同保证中文 seed 数据、后端响应和前端展示一致。

### 这次解决方案带来的启示/原则

乱码问题要从“静态内容是否正常、接口返回是否正常、数据库存储是否正常”分层排查，不要一开始就盲目改前端。动态数据乱码时优先核对数据库和接口编码链路。

## 阶段：后端骨架、REST API 与异常处理

### 做了什么

后端使用 Spring Boot 3.3.6、Spring Web、Spring Data JPA、Spring Security、Validation、JJWT、MySQL Connector 和 springdoc-openapi。`AuthController`、`ProductController`、`OrderController` 分别承载用户、商品、订单接口；`ApiSupport.java` 定义 DTO、分页结构和全局异常处理；`BizException` 统一业务错误状态码和中文信息。

### 遇到的问题（如果有）

如果没有统一异常处理，前端会看到裸异常或英文堆栈，不符合实训演示要求。订单库存不足、重复用户名、未登录、无权限都需要稳定、友好的错误返回。

### 问题的根本原因

Controller 直接抛普通异常会被 Spring 默认错误处理接管，响应格式不稳定。前端组件如果每处单独解析错误，也容易遗漏。

### 解决方案

`ApiSupport.java` 中 `GlobalExceptionHandler` 捕获 `BizException`、参数校验异常、`AccessDeniedException` 和其他异常，分别返回 `ErrorResponse(msg)`。前端 `frontend/src/api/http.js` 通过 `errorMessage(error)` 统一取 `error.response?.data?.msg`。

### 这次解决方案带来的启示/原则

接口规范不只是 URL 命名，还包括错误格式。答辩演示时，能把“库存不足”“用户名已存在”“无权限访问”稳定展示出来，比后台抛堆栈更能体现工程完整性。

## 阶段：用户认证、JWT 与个人中心

### 做了什么

用户模块实现注册、登录、Refresh Token、个人资料查询和更新。`SecurityConfig` 配置无状态 session、JWT 过滤器、未登录 401 和无权限 403；`JwtService` 生成 2 小时 Access Token 和 7 天 Refresh Token；`JwtAuthenticationFilter` 解析 `Authorization: Bearer` 并把 `CurrentUser` 放入 `SecurityContextHolder`。

### 遇到的问题（如果有）

个人资料更新曾出现“未提交字段被覆盖为 null”的 bug：例如只改头像不传 phone，后端如果直接把请求对象全量覆盖到实体，就会清空原手机号。

### 问题的根本原因

更新接口的请求 DTO 是部分字段更新语义，但实现如果按“全量替换”处理，就会把没有传的字段误认为用户要清空。前端表单和后端 DTO 的语义不一致，是这类 bug 的常见根源。

### 解决方案

当前 `AuthController.updateProfile` 已修复：只有 `request.phone() != null` 才校验唯一性并更新 `user.phone`，只有 `request.avatarUrl() != null` 才更新头像。`docs/bug-list.md` 的 B004 状态为 Fixed。前端 `ProfileView.vue` 保存后会更新 `store.user` 和 `localStorage.userInfo`，避免刷新后显示旧资料。

### 这次解决方案带来的启示/原则

部分更新接口要明确 PATCH/PUT 语义。即使用 PUT，也可以约定“非 null 才更新”，但必须在后端实现中体现，不能让前端承担数据保护责任。

## 阶段：商品模块、搜索与管理员后台

### 做了什么

商品模块实现分类树、商品列表、商品详情、搜索、排序分页和管理员 CRUD。`ProductController.list` 支持 `categoryId/page/size/sort`，`ProductController.search` 支持关键词搜索，`ProductController.adminList` 供管理员查看全部状态商品。前端 `ProductListView.vue` 有搜索框、分类按钮、排序下拉和分页器，`AdminView.vue` 支持新增、编辑、下架、重新上架。

### 遇到的问题（如果有）

项目规格里给出了 MySQL ngram 全文索引，但团队对全文检索链路和部署环境配置有不确定性。另一个真实问题是管理员下架商品后，如果后台列表只查在售商品，就无法再看到和重新上架它。

### 问题的根本原因

全文索引涉及 MySQL parser、字符集和 SQL 写法，实训项目不一定值得把风险集中在这里。管理员列表和用户列表如果复用同一个“只查 status=1”的接口，就会造成管理功能缺失。

### 解决方案

当前代码保留 `docs/seed.sql` 中 `FULLTEXT INDEX ft_name_desc ... WITH PARSER ngram`，但后端实际搜索使用 `ProductRepository.search` 的 JPQL LIKE 兜底，并按 `salesCount desc, id desc` 排序。管理员列表新增 `/api/products/admin`，后端查 `findAll(pageable)` 或按分类查全部状态，前端可对已下架商品点击“上架”。`docs/bug-list.md` 的 B009 已记录该修复。

### 这次解决方案带来的启示/原则

搜索功能的答辩重点是“能搜、能排序、能分页、基于真实数据”，不是一定要展示 Elasticsearch。简化实现可以接受，但必须诚实说明当前实现是 LIKE 兜底，不要把 DDL 里的全文索引说成后端查询实际使用了 MATCH。

## 阶段：购物车、订单创建与状态机

### 做了什么

订单模块实现购物车增删改查、下单、直购并发验收端点、订单列表/详情、模拟支付、取消、管理员发货、确认收货。`OrderService` 作为事务边界，`create` 和 `createDirect` 都使用 `@Transactional`。状态机由 `requireStatus` 约束：待付款才能支付或取消，已支付才能发货，已发货才能确认收货。

### 遇到的问题（如果有）

购物车曾经没有校验数量是否超过库存，导致用户可以加购超过库存的数量，最终下单才失败。取消订单回滚库存也曾被识别为非并发安全风险：如果回滚不是原子 SQL，在并发取消或取消与下单竞争时可能造成库存和销量不一致。

### 问题的根本原因

购物车不是最终库存扣减点，但用户体验上应该提前限制明显超过库存的数量。库存回补如果用“先查当前库存，再在 Java 里加，再 save”的方式，会有读-改-写竞争；正确方式应和扣库存一样交给数据库原子更新。

### 解决方案

当前 `OrderController.addCart` 和 `updateCart` 都读取商品库存并限制 `targetQty > product.stock`。`ProductRepository.restoreStock` 已实现原子 JPQL：`stock = stock + :quantity, version = version + 1, salesCount = case when ...`。`OrderService.cancel` 在事务里把订单状态改成 `CANCELLED`，遍历订单项调用 `products.restoreStock`。`docs/bug-list.md` 中 B005、B006 均为 Fixed。

### 这次解决方案带来的启示/原则

订单模块不能只看“下单成功”这条正向路径。取消、库存回补、状态非法跳转、购物车边界这些异常路径，才是答辩老师最容易追问的地方。

## 阶段：并发扣库存验收

### 做了什么

项目为并发验收准备了 `docs/seed.sql` 中的“抢购测试商品”，id 为 21，库存 80。后端提供 `/api/orders/direct`，跳过购物车唯一键约束，直接对同一商品下单。`scripts/concurrent_order_test.ps1` 登录后启动多个 PowerShell Job 并发请求该接口。

### 遇到的问题（如果有）

并发验收如果走购物车下单，会受同一用户同一商品购物车唯一键、购物车项复用和删除逻辑影响，不能纯粹验证库存扣减事务。另一个问题是脚本默认 `BaseUrl` 为 `http://localhost:8080`，但当前 compose 后端宿主端口是 `8081`。

### 问题的根本原因

并发测试需要隔离被测点，目标是验证 `deductStock` 原子更新和事务回滚，而不是购物车数据模型。端口问题来自 compose 为避免宿主 8080 冲突，把 `backend` 映射成 `8081:8080`。

### 解决方案

用 `/api/orders/direct` 作为实训并发验收端点，`OrderService.createDirect` 内部仍走 `products.deductStock(product.id, quantity, product.version)` 和事务。运行脚本时，如果服务由 compose 启动，应执行类似：`powershell -ExecutionPolicy Bypass -File scripts/concurrent_order_test.ps1 -BaseUrl http://localhost:8081`。当前 `docs/bug-list.md` 的 B001 仍为 Open，说明虽然事务和乐观锁代码已就位，但仍需在联调环境复测并核对最终成功数和 stock。

### 这次解决方案带来的启示/原则

并发测试不要让无关机制干扰目标结论。脚本还应补强：并发结束后自动查询商品库存和订单成功数量，形成更完整的验收证据。

## 阶段：Docker 构建网络问题与后端镜像方案演进

### 做了什么

项目要求 `docker-compose up -d` 一键启动，因此需要为前端、后端、Agent 和 MySQL 都准备 Docker 构建。过程中遇到多类网络和文件系统问题：Docker Hub 认证连接失败、需要配置镜像加速器、手动预拉取基础镜像、前端 `npm install` 或构建中出现 ETXTBSY 文件系统竞态、Maven 容器内构建因网络中断反复超时。

### 遇到的问题（如果有）

当时尝试过 `mvn dependency:go-offline`，希望提前缓存依赖，但它会额外拉取大量插件和传递依赖，网络不稳定时反而扩大失败面。为了快速推进，一度决策放弃容器内 Maven 构建，改成“本机先 `mvn package`，Dockerfile 单阶段直接复制 jar”。这个方案让构建从十几分钟降到几十秒，但代价是 clean checkout 或别人机器没有 `target/*.jar` 时无法构建。

### 问题的根本原因

本质是实训网络环境不稳定和 Docker 构建可复现性之间的冲突。单阶段复制 jar 是局部最优，适合临时演示，但不适合交付物，因为它依赖宿主机先完成构建，不符合“源码 + Dockerfile 可独立构建镜像”的要求。

### 解决方案

当前源码已经改回多阶段 Maven 构建。`backend/Dockerfile` 第一阶段基于 `maven:3.9-eclipse-temurin-17`，复制 `settings.xml`、`pom.xml`，执行 `mvn -s settings.xml -B dependency:go-offline`，再复制 `src` 并 `mvn -s settings.xml -B clean package -DskipTests`；第二阶段基于 `eclipse-temurin:17-jre` 运行 jar。`docs/bug-list.md` 的 B011 明确记录：单阶段预编译 jar 方案在 clean checkout 下报 no source files，最终 Fixed 为多阶段 Maven 构建。

### 这次解决方案带来的启示/原则

工程权衡要随阶段变化。临时演示可以用本机预编译绕过网络问题，但最终交付要回到可复现构建。答辩时可以诚实说：我们经历过一个折中方案，后来发现它破坏 clean checkout，于是改回多阶段构建。

## 阶段：`.docker` 配置目录权限误判排查

### 做了什么

Docker 过程中曾反复出现 `.docker` 配置目录 access denied 类问题。当时有一段时间把它当作真实权限故障处理，甚至尝试从“需要提权”的方向解释。

### 遇到的问题（如果有）

后来通过 `icacls` 等命令验证发现，权限从始至终是正常的。问题不是 `.docker` 目录真的没有权限，而是某次 Codex 执行时对失败原因做了误判，把普通环境问题解释成权限问题，进而试图走提权路径。

### 问题的根本原因

自动化代理在看到 access denied、网络失败或 Docker 错误时，容易把相邻症状混在一起。尤其是在 Windows + Docker Desktop + 受限沙箱环境下，错误信息可能来自多层系统，不能只看一句报错就下结论。

### 解决方案

把权限问题和网络问题分开验证：用 `icacls` 看目录 ACL，用 Docker 命令验证镜像拉取和构建，用日志判断具体失败步骤。最终把 `.docker` 权限判定为干扰因素，不再作为主要排查方向。

### 这次解决方案带来的启示/原则

答辩时可以把这个案例作为“如何排除干扰因素”的例子：不是所有看起来像权限的问题都真是权限问题，诊断必须有验证命令支撑。AI 辅助排错也可能误判，需要人用证据纠偏。

## 阶段：前端 Docker 构建与 node_modules 污染

### 做了什么

前端采用多阶段构建：`frontend/Dockerfile` 第一阶段用 `node:20-alpine` 执行 `npm ci` 和 `npm run build`，第二阶段用 `nginx:1.27-alpine` 托管 `dist`。`frontend/nginx.conf` 处理 Vue history 路由、`/api/` 代理和 `/agent/` 代理。

### 遇到的问题（如果有）

如果 Docker build 上下文包含宿主机 Windows 的 `node_modules`，复制进 Linux 容器后可能导致构建失败或出现平台不兼容问题。`docs/bug-list.md` B012 记录了 frontend 缺 `.dockerignore` 时，`COPY . .` 把宿主 `node_modules` 覆盖进镜像，导致 `npm run build` 失败。

### 问题的根本原因

Docker build 的上下文默认包含目录下所有文件。Node 依赖含平台相关二进制和大量文件，不能把宿主依赖目录复制进容器。

### 解决方案

当前 `frontend/.dockerignore` 已排除 `node_modules`、`dist`、日志、Git 和 IDE 文件。`backend/.dockerignore` 和 `agent-service/.dockerignore` 也分别排除了构建产物、日志和 pycache。B012 已标记 Fixed。

### 这次解决方案带来的启示/原则

Dockerfile 和 `.dockerignore` 要一起设计。多阶段构建解决运行镜像精简问题，`.dockerignore` 解决构建上下文污染问题。

## 阶段：Agent 服务接入 LangChain

### 做了什么

Agent 服务用 FastAPI 提供 `/agent/chat`、`/agent/chat/stream`、`/agent/history`、`/health`。`agent-service/app/main.py` 中用 LangChain 的 `create_tool_calling_agent` 绑定 `search_products` 和 `query_order_status` 两个工具。工具通过 HTTP 调 Spring Boot 后端，Agent 自己用 SQLAlchemy 读写 `agent_conversation` 和 `user_preference`。

### 遇到的问题（如果有）

Agent 最初长期处于“离线兜底模式”：`/health` 的 `llmEnabled` 为 false，日志出现 `langchain agent unavailable, using offline fallback`，看起来能回复，但不是真正 LLM Agent 在推理。

### 问题的根本原因

根因是 LLM 配置未注入，尤其是 docker-compose 里的 `LLM_API_KEY` 为空或占位。代码中的 `llm_enabled()` 要求 `LLM_API_KEY` 有效且 `ChatOpenAI` 可用，否则 `llm()` 返回 None，`run_langchain_agent` 抛出 “LLM is not configured”，`handle_chat` 进入离线兜底。

### 解决方案

当前方案是通过根目录 `.env` 注入 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`，并用 `.gitignore` 排除 `.env`，避免密钥硬编码。`docker-compose.yml` 的 `agent-service` 使用 `env_file: .env` 并传入 LLM 环境变量。修复后用 `/health` 检查 `llmEnabled` 是否从 false 变 true，再用“推荐3款适合写代码的机械键盘”这类边界用例验证工具调用和预算识别。

### 这次解决方案带来的启示/原则

AI 功能不能只看“页面有回复”，要看 health、日志和 `toolsUsed`。离线兜底能保障演示主链路，但答辩时必须区分“LLM Agent 路径”和“规则兜底路径”。

## 阶段：预算提取正则误伤

### 做了什么

Agent 离线兜底路径需要从用户消息中提取预算，例如“预算500”“500元以内”，再调用 `search_products` 过滤价格。这个逻辑在 `agent-service/app/main.py` 的 `extract_budget`。

### 遇到的问题（如果有）

原始正则类似 `(\d+(?:\.\d+)?)\s*(?:元|块|预算|以内)?`，单位部分是可选的，导致“推荐3款键盘”这种不含预算意图的句子被解析为预算上限 3 元，进而过滤掉所有商品。

### 问题的根本原因

正则把“任何数字”都当成金额，没有判断预算语境。自然语言里数字可能表示数量、型号、尺寸、页码，不一定是价格。

### 解决方案

当前 `extract_budget` 使用三组更严格的 pattern：一类要求数字前有“预算/不超过/最多/控制在”等词，一类要求显式 `¥/￥`，一类要求数字后紧跟“元/块钱/预算以内/以内/以下/左右”等预算词。没有预算语境时返回 None。`docs/bug-list.md` 的 B003 标记为 Fixed。

### 这次解决方案带来的启示/原则

自然语言解析不能只匹配数字，要匹配意图。答辩时可以用这个案例说明：AI 项目里即使是小的规则兜底，也需要边界测试。

## 阶段：LLM 正常回复被离线逻辑覆盖

### 做了什么

Agent 需要对非购物问题礼貌拒答，例如“今天天气怎么样”。系统提示中明确“只能回答购物、商品推荐和订单相关问题”，如果 LLM 正常选择不调用工具并给出拒答，这应该是成功路径。

### 遇到的问题（如果有）

曾经的逻辑把“不调用工具”误判为失败，然后用离线固定模板覆盖掉 LLM 本来合理生成的回复。这样会让已配置 LLM 的情况下，模型输出仍被规则兜底覆盖。

### 问题的根本原因

工具调用不是每个问题都必须发生。对非购物问题，正确行为就是不调用工具并拒答。把“未调用工具”等同于“Agent 失败”，混淆了业务意图和系统异常。

### 解决方案

当前 `handle_chat` 只在 `run_langchain_agent` 抛异常时进入 `offline_agent`，或者在 `reply` 为空时兜底。`docs/bug-list.md` 的 B007 记录为 Fixed。Agent smoke 脚本也验证天气问题 `toolsUsed` 应为空。

### 这次解决方案带来的启示/原则

Agent 的成功标准不是“每轮都调用工具”，而是“该调用时调用，不该调用时拒答”。工具调用策略要结合用户意图判断。

## 阶段：Agent 响应耗时、流式输出与 Markdown 渲染

### 做了什么

Agent 对话曾有 20 余秒响应体验问题。当前代码加入了耗时拆分和流式接口：`AgentTiming` 记录 LLM、工具和偏好提取耗时；`/agent/chat/stream` 用 SSE 推送 token 和工具状态；前端 `ChatWidget.vue` 用 `fetch` 读取流，展示“正在连接导购助手”“正在检索真实商品和库存”等状态。

### 遇到的问题（如果有）

如果只用普通 `/agent/chat`，用户需要等完整回复返回，20 秒左右会感觉卡住。另一个问题是 AI 回复可能包含 Markdown，如果直接 `v-html` 渲染会有 XSS 风险。

### 问题的根本原因

Agent 调用至少可能包含一次工具调用、一次或多次 LLM 调用、一次偏好提取和数据库写入，天然比普通 API 慢。Markdown 渲染把文本变成 HTML，如果不做清洗，恶意内容可能进入页面。

### 解决方案

后端 `chat_stream` 使用 `StreamingResponse` 输出 `text/event-stream; charset=utf-8`，回调 `QueueStreamCallback` 推送 `token`、`tool_start`、`tool_end`、`done`。前端引入 `markdown-it` 和 `dompurify`，`renderMarkdown` 中用 `DOMPurify.sanitize(md.render(text || ''))` 清洗后再渲染。`timing breakdown` 日志可用于定位慢在 LLM、工具还是偏好提取。

### 这次解决方案带来的启示/原则

体验优化不能只靠“把模型换快”。先把耗时拆开，再决定是做流式、减少模型调用、优化工具超时还是缓存偏好。

## 阶段：Agent 与主系统服务间鉴权

### 做了什么

Agent 查询订单不直接连业务表，而是调用后端内部接口。后端 `OrderController` 提供 `/api/internal/orders` 和 `/api/internal/orders/{id}`，只返回订单摘要。Agent 通过 `_internal_headers()` 带 `X-Internal-Service: agent` 和 `X-Internal-Secret`。

### 遇到的问题（如果有）

如果 Agent 直接使用普通用户 JWT，会涉及 Token 传递和泄露风险；如果 Agent 直接连接业务库查询订单，又绕过了主系统权限边界。

### 问题的根本原因

Agent 是独立服务，需要服务间权限。普通用户身份和服务身份是两类凭据，混用会让安全边界模糊。

### 解决方案

使用共享密钥请求头作为轻量服务间鉴权。`docker-compose.yml` 同时给后端和 Agent 注入 `INTERNAL_SERVICE_SECRET`。后端 `checkInternal` 校验服务名和密钥，不通过则返回 403。查询结果使用 `InternalOrderResponse`，不暴露用户密码等敏感信息。

### 这次解决方案带来的启示/原则

实训项目可以用共享密钥，但答辩时要承认这不是生产级最佳实践。生产环境可以升级为服务 Token、mTLS 或 API Gateway 权限策略。

## 阶段：本地端口与代理配置修复

### 做了什么

Compose 中后端容器内部仍是 8080，但宿主机映射到 8081。前端开发服务器和 Agent 本地启动脚本需要访问宿主端口，而容器内服务访问需要使用容器服务名和内部端口。

### 遇到的问题（如果有）

`docs/bug-list.md` 记录 B013：后端在 docker 中映射到宿主 8081，Agent 本地跑时如果仍连 8080，会被拒绝。B014 记录 Vite dev 代理如果仍指向 8080，`npm run dev` 时 `/api` 也连不上后端。

### 问题的根本原因

容器内地址和宿主机地址是两套网络视角。`http://backend:8080` 只在 compose 网络内有效；本地进程访问后端要走 `http://127.0.0.1:8081`。

### 解决方案

当前 `agent-service/start_agent_local.py` 设置 `BACKEND_BASE` 为 `http://127.0.0.1:8081`，`frontend/vite.config.js` 把 `/api` 代理到 `http://localhost:8081`，`frontend/nginx.conf` 在容器内仍代理到 `http://backend:8080`。B013、B014 均为 Fixed。

### 这次解决方案带来的启示/原则

本地开发和容器部署要分别说明访问地址。答辩时如果用浏览器访问 Swagger，应说 `http://localhost:8081/swagger-ui/index.html`，不是 8080。

## 阶段：测试、缺陷清单与当前风险

### 做了什么

项目提供 JUnit 测试、Agent smoke 脚本、并发订单脚本和 `docs/bug-list.md` 缺陷表。`PasswordEncoderTest` 验证 BCrypt，`OrderStockSqlTest` 验证库存扣减的 version 条件，`agent_smoke.py` 验证商品工具、订单工具、长期记忆和非购物拒答。

### 遇到的问题（如果有）

测试覆盖还不完整。最明显的是没有完整 RestAssured 或 Newman 自动化链路去覆盖“注册-登录-浏览商品-下单-查询订单”。并发脚本只统计 success/failed，没有自动检查数据库最终库存和订单数量。B001 仍为 Open。

### 问题的根本原因

实训时间优先用于五大模块功能和 Agent 集成，自动化测试更多覆盖高风险点而不是全量覆盖。并发测试还依赖启动环境和 seed 数据状态，如果旧 MySQL volume 没重置，抢购商品库存可能不是 80。

### 解决方案

答辩前要按 README 提醒确认 seed 数据：`alice` 有演示订单 `SM202606300001`，抢购测试商品库存为 80。运行并发脚本时传正确 BaseUrl，结束后手动或补脚本查询商品 21 的 stock 和成功订单数。文档中如实标注 B001 仍需联调回归，不夸大测试覆盖。

### 这次解决方案带来的启示/原则

缺陷清单不是“扣分项”，而是质量管理证据。关键是清楚说明哪些已修复，哪些仍是风险，下一步如何验证。

## 阶段：Git 与 AI 辅助开发反思

### 做了什么

仓库提交记录体现从初始骨架到 Agent 集成的演进：`Initial commit`、`init: 完整项目骨架，含五大模块代码`、`feat: integrate DeepSeek AI shopping agent`、`feat: 完成AI导购Agent功能开发`。根目录 `.gitignore` 排除 `.env`、`node_modules/`、`target/`、`__pycache__/` 等。AI 辅助用于代码生成、排错、文档整理和答辩材料生成。

### 遇到的问题（如果有）

当前仓库只看到 `main` 和 `origin/main`，没有保留多个 feature 分支或 PR 记录。AI 辅助开发过程中也出现过误判，例如 Docker 权限问题误判、后端 Docker 构建方案在不同阶段反复调整。

### 问题的根本原因

实训项目常为了赶进度直接在 main 合并，导致协作过程证据不如规范团队项目完整。AI 工具擅长生成方案，但对本地环境、网络状态和历史上下文可能判断不稳。

### 解决方案

答辩时不要编造 PR 流程。可以诚实说明当前 Git 证据主要是 main 提交历史和模块目录，团队分工通过 README 和代码结构说明。如果老师问 AI 辅助开发的风险，可以用 Docker 构建方案演进、预算正则误伤、LLM 兜底覆盖等真实案例说明“AI 生成后必须读代码、跑测试、看日志验证”。

### 这次解决方案带来的启示/原则

AI 是提效工具，不是质量保证工具。真正能防止答辩被追问露馅的，是源码核查、测试证据和诚实的缺陷记录。
