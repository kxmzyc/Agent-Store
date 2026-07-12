# 技术栈深度解析

本文档用于答辩前复习。每个技术点都分为通用原理和本项目实现，避免把“理论上应该这样做”和“代码里实际这样做”混在一起。

## 前后端分离与独立 Agent 微服务架构

### 通用原理

前后端分离是把用户界面、业务接口和数据存储分成不同职责边界的架构方式。前端负责页面渲染、交互状态和调用接口；后端负责认证、业务规则、事务一致性和数据访问；数据库只作为持久化层，不直接暴露给浏览器。微服务不是简单地把目录拆开，而是把运行时也拆成可以独立启动、独立部署、通过网络协议通信的服务。本项目里的 Agent 服务采用独立 Python 微服务，是一种“小规模微服务”实践：它没有引入注册中心、网关和消息队列，但已经具备独立进程、独立依赖和 HTTP 服务间调用的基本特征。

### 在本项目中的具体应用场景

智能商城本身有用户、商品、订单、搜索这些典型电商模块，同时还要求接入 LangChain 智能导购。把商城主业务放在 Spring Boot 后端、把 Agent 放在 FastAPI 服务里，可以让 Java 业务系统专注事务和权限，让 Python 服务使用 LangChain 生态处理工具调用和记忆机制。前端只访问两个后端入口：业务 API 和 Agent API，不直接连接 MySQL。这样答辩时可以清楚说明“业务系统”和“智能体系统”的边界。

### 实际实现方式（必须引用真实代码）

当前编排文件是 `docker-compose.yml`：`frontend` 构建 Vue 静态站点，`backend` 构建 Spring Boot 服务，`agent-service` 构建 FastAPI/LangChain 服务，`mysql` 使用 MySQL 8.0。容器内部通信使用服务名，例如 Agent 的 `BACKEND_BASE` 是 `http://backend:8080`，后端数据库连接是 `jdbc:mysql://mysql:3306/...`。前端 Nginx 代理在 `frontend/nginx.conf` 中定义：`/api/` 转发到 `http://backend:8080/api/`，`/agent/` 转发到 `http://agent-service:8000/agent/`。架构说明文档 `docs/architecture.md` 也明确写到“Spring Boot 是用户、商品、购物车、订单的唯一写入口；Agent 服务不直接写业务表，只通过 REST 工具查询商品和订单”。

### 应用效果

该架构让五大模块可分别演示：商品浏览不依赖登录，购物车和订单依赖 JWT，AI 导购通过工具调用读取真实商品和订单数据。Docker 启动后，前端对外端口是 `80`，后端宿主端口是 `8081`，Agent 端口是 `8000`，MySQL 宿主端口是 `3307`。需要注意：项目规格最初写的是后端宿主 `8080`，但当前 `docker-compose.yml` 实际映射为 `8081:8080`，README 也已说明这个差异。

### 可能被追问的延伸问题预判

老师可能会问：为什么 Agent 不直接连业务库？为什么不把 Agent 写进 Spring Boot？如果 Agent 挂了商城还能不能下单？为什么没有引入 Spring Cloud Gateway 或注册中心？

## Vue3 组合式 API 与前端组件化实践

### 通用原理

Vue3 的组合式 API 是用 `setup` 思路组织逻辑，把状态、生命周期、计算属性和方法按功能聚合，而不是按 Options API 的 `data/methods/computed` 分散。`<script setup>` 是 Vue3 推荐的单文件组件写法，编译时自动暴露模板变量，代码更短，也更适合把接口调用、表单状态、页面加载状态写在同一个组件上下文里。组件化的价值在于把页面拆成可维护的单元，同时把全局能力，如路由、请求封装、Toast、聊天窗，沉淀成公共模块。

### 在本项目中的具体应用场景

本项目需要多个独立页面：登录、注册、商品列表、商品详情、购物车、订单、个人中心、管理员商品管理，还需要一个全局悬浮聊天窗。用 Vue3 可以快速组织这些页面，用路由控制登录态和管理员权限，用局部状态管理表单和加载状态，用公共 axios 封装统一处理 JWT。这样前端可以直接演示“现代前端框架 + AJAX + 组件化代码”的教学目标。

### 实际实现方式（必须引用真实代码）

路由集中在 `frontend/src/router.js`，其中 `/cart`、`/orders`、`/profile` 带 `meta: { auth: true }`，`/admin` 还带 `admin: true`。路由守卫 `router.beforeEach` 检查 `store.token` 和 `store.user?.role`，未登录跳 `/login`，非管理员访问后台跳首页。页面组件均使用 `<script setup>`，例如 `frontend/src/views/ProductListView.vue` 使用 `ref` 保存分类、商品、关键词、页码等状态，并调用 `api.get('/products')` 或 `api.get('/products/search')` 加载数据。`frontend/src/components/ChatWidget.vue` 是全局悬浮 AI 导购组件，使用 `ref`、`reactive`、`MarkdownIt`、`DOMPurify` 和 `fetch` 读取 SSE 流式回复。

### 应用效果

前端页面刷新后登录态能保留，因为 `frontend/src/store.js` 从 `localStorage` 读取 `userInfo` 和 `accessToken`。商品列表支持分类、搜索、排序、分页和加购；详情页在 `product.stock <= 0` 时禁用“加入购物车”和“立即购买”；订单页有状态 tab、模拟支付、取消、确认收货；管理员页可以新增、编辑、下架和重新上架商品。聊天窗不仅能展示文本，还能显示工具调用状态，比如“正在检索真实商品和库存”。

### 可能被追问的延伸问题预判

老师可能会问：为什么 Token 放 localStorage 有风险？Vue 路由守卫和后端权限校验有什么区别？为什么聊天窗不用 axios 而用了 fetch？Markdown 渲染为什么要配 DOMPurify？

## AJAX 与 axios 请求封装

### 通用原理

AJAX 是浏览器在不刷新整个页面的情况下通过 HTTP 与后端交换数据的方式。现代项目通常不用原生 `XMLHttpRequest`，而是使用 `fetch` 或 axios。axios 的优势是拦截器、统一 baseURL、请求超时、错误处理和响应转换更方便。前端项目如果每个组件都自己拼 Token、自己处理 401，会造成重复代码和行为不一致，所以通常把请求封装在统一模块中。

### 在本项目中的具体应用场景

商城前端需要大量调用后端接口：登录注册、商品列表、购物车、订单、个人中心、管理员后台。除游客商品浏览外，大部分业务请求都需要 JWT。通过 axios 请求拦截器统一注入 `Authorization`，通过响应拦截器统一刷新 Token 或跳转登录，可以保证各页面行为一致。Agent 普通接口也有 `agentApi`，但当前聊天窗实际使用 `/agent/chat/stream` 的 SSE 流式接口，所以那里使用 `fetch` 读取流。

### 实际实现方式（必须引用真实代码）

`frontend/src/api/http.js` 创建了两个 axios 实例：`api` 的 `baseURL` 是 `/api`，超时 10 秒；`agentApi` 的 `baseURL` 是 `/agent`，超时 120 秒。请求拦截器中，如果 `store.token` 存在，就设置 `config.headers.Authorization = Bearer ...`。响应拦截器中，如果响应状态是 401 且本地有 `refreshToken`，会调用 `/api/auth/refresh` 获取新的 accessToken，并用“单飞锁”变量 `refreshing` 保证并发 401 只触发一次刷新，其余请求复用同一个 Promise。错误提示统一通过 `errorMessage(error)` 返回后端 `msg` 或默认文案。

### 应用效果

登录成功后 `store.setAuth` 会写入 `accessToken`、`refreshToken` 和 `userInfo`，后续购物车、订单、个人中心请求自动携带 Token。Access Token 过期但 Refresh Token 仍有效时，用户不需要手动重新登录；刷新失败时统一 `store.logout()` 并跳 `/login`。这满足验收里“axios 请求拦截器统一注入 Authorization header，响应拦截器统一处理 401”的要求。

### 可能被追问的延伸问题预判

可能被问：刷新 Token 时为什么要避免多个请求同时刷新？为什么 Agent 流式接口没有走 axios？如果 Refresh Token 也过期了前端如何处理？跨域问题在开发和容器部署中分别怎么解决？

## RESTful API 设计规范

### 通用原理

RESTful API 以资源为中心设计接口，用 HTTP 方法表达操作语义：`GET` 查询、`POST` 创建、`PUT` 更新、`DELETE` 删除。相比把动作都写成 `/doSomething`，RESTful 接口更容易理解和测试，也便于生成接口文档。规范设计还包括正确使用状态码，例如创建成功返回 201，未认证返回 401，无权限返回 403，资源不存在返回 404，业务冲突返回 409。

### 在本项目中的具体应用场景

智能商城需要给前端、Postman/Swagger 和 Agent 工具提供稳定 API。用户模块提供 `/api/auth/register`、`/api/auth/login`、`/api/user/profile`；商品模块提供 `/api/products`、`/api/products/search`、`/api/categories`；订单模块提供 `/api/cart`、`/api/orders` 和状态动作；Agent 服务提供 `/agent/chat`、`/agent/chat/stream`、`/agent/history`、`/health`。这些接口对应五大模块，可以直接作为答辩演示顺序。

### 实际实现方式（必须引用真实代码）

后端 Controller 分布在 `backend/src/main/java/com/example/smartmall/api/`。`AuthController` 用 `@RequestMapping("/api")` 管理认证和用户资料；`ProductController` 定义 `GET /categories`、`GET /products`、`GET /products/search`、`POST /products`、`PUT /products/{id}`、`DELETE /products/{id}`；`OrderController` 定义购物车、下单、支付、取消、发货、确认收货和内部订单查询。DTO 和统一响应结构在 `ApiSupport.java`，例如 `PageResponse<T>(long total, List<T> list)` 和 `ErrorResponse(String msg)`。`GlobalExceptionHandler` 将业务异常转为 JSON 错误。

### 应用效果

接口行为比较稳定：注册成功返回 201，重复用户名通过 `BizException.conflict("用户名已存在")` 返回 409；未登录访问受保护接口由 `SecurityConfig` 的 `authenticationEntryPoint` 返回 401 和中文 JSON；普通用户访问管理员商品管理会被 `@PreAuthorize("hasRole('ADMIN')")` 拒绝为 403。`docs/api-spec.md` 已列出主要接口，可作为答辩时 Swagger 之外的静态接口文档。

### 可能被追问的延伸问题预判

可能被问：`PUT /orders/{id}/pay` 是否完全 RESTful？为什么状态流转不是直接 `PUT /orders/{id}` 修改 status？内部接口 `/api/internal/orders` 为什么允许匿名路径但靠请求头鉴权？

## JWT 令牌认证

### 通用原理

JWT（JSON Web Token）是一种自包含令牌格式，由 Header、Payload、Signature 三部分组成。服务端用密钥对 Header 和 Payload 签名，客户端之后每次请求带上 Token。服务端验证签名和过期时间后，可以从 Payload 里读取用户身份。JWT 的优势是无状态，不需要服务端保存 session；风险是 Token 一旦泄露，在过期前可能被冒用，因此要设置合理有效期、使用 HTTPS、避免把敏感信息放在 Payload 中。

### 在本项目中的具体应用场景

商城用户登录后要访问个人中心、购物车、订单和管理员功能。后端不能让每个 Controller 自己解析 Token，所以项目通过过滤器统一解析 JWT，把当前用户放进 Spring Security 上下文。Access Token 有效期 2 小时，Refresh Token 有效期 7 天，满足项目规格里“登录状态保持”和“受保护接口返回 401”的要求。

### 实际实现方式（必须引用真实代码）

`backend/src/main/java/com/example/smartmall/security/JwtService.java` 用 `io.jsonwebtoken` 生成和解析 Token。`accessToken` 调用 `token(..., "access", 2 * 60 * 60)`，`refreshToken` 调用 `token(..., "refresh", 7 * 24 * 60 * 60)`，Payload 里包含 `subject=userId`、`username`、`role`、`type`、`issuedAt`、`expiration`。密钥来自配置 `app.jwt-secret`，最终由 `Keys.hmacShaKeyFor` 生成 HMAC key。`JwtAuthenticationFilter.java` 继承 `OncePerRequestFilter`，读取 `Authorization: Bearer ...`，只接受 `type=access` 的令牌，并构造 `UsernamePasswordAuthenticationToken` 放进 `SecurityContextHolder`。

### 应用效果

Controller 可以用 `@AuthenticationPrincipal CurrentUser currentUser` 直接拿到用户 id、用户名和角色，例如 `AuthController.profile`、`OrderController.createOrder` 都是这样实现。未登录访问受保护接口时，`SecurityConfig` 返回 `{"msg":"未登录或Token无效"}` 和 401。刷新令牌接口 `AuthController.refresh` 会检查 `type` 是否为 `refresh`，不是 Refresh Token 就返回“Refresh Token无效”。

### 可能被追问的延伸问题预判

可能被问：JWT 为什么不能主动失效？Token 被截获怎么办？为什么 Access 和 Refresh 要分开？Payload 里放 role 会不会被篡改？密钥默认值是否适合生产？

## BCrypt 密码加密

### 通用原理

BCrypt 是面向密码存储的单向哈希算法，核心特点是带盐和可调成本。带盐可以避免同一个密码生成固定哈希，抵抗彩虹表；成本参数让计算过程有意变慢，提高暴力破解成本。MD5、SHA-1 这类快速哈希适合校验文件完整性，不适合密码存储，因为攻击者可以高速枚举密码。密码系统的正确做法是不保存明文，也不保存可逆加密结果，只保存密码哈希。

### 在本项目中的具体应用场景

用户注册登录是整个商城鉴权的基础。如果数据库里存明文或 MD5，答辩时会被认为安全设计不合格。项目使用 Spring Security 的 `BCryptPasswordEncoder`，注册时编码，登录时用 `matches` 校验，用户不存在、密码错误和账号禁用都统一走“用户名或密码错误”的 401，避免通过错误信息枚举用户名。

### 实际实现方式（必须引用真实代码）

`backend/src/main/java/com/example/smartmall/security/SecurityConfig.java` 暴露 `PasswordEncoder passwordEncoder()`，返回 `new BCryptPasswordEncoder()`。`AuthController.register` 中设置 `user.passwordHash = passwordEncoder.encode(request.password())`，`AuthController.login` 中通过 `passwordEncoder.matches(request.password(), u.passwordHash)` 验证密码。`backend/src/test/java/com/example/smartmall/PasswordEncoderTest.java` 专门验证编码结果以 `$2a$10$` 开头，正确密码能匹配，错误密码不能匹配。

### 应用效果

`docs/seed.sql` 中三名测试用户的 `password_hash` 均为 `$2a$10$...` 格式，测试账号密码是 `123456`，但数据库不存明文。注册新用户时也会生成 BCrypt 哈希。密码长度校验由 `ApiSupport.RegisterRequest(@Size(min = 6) String password)` 负责，不满足时 `GlobalExceptionHandler` 返回“请求参数不合法”。

### 可能被追问的延伸问题预判

可能被问：BCrypt 的盐在哪里？为什么同一个密码每次 hash 不一样？成本参数为什么默认 10 足够？密码忘记了能不能从 hash 反推出原文？

## Spring 事务管理与订单一致性

### 通用原理

事务用于保证一组数据库操作要么全部成功，要么全部失败，常见特性是 ACID：原子性、一致性、隔离性、持久性。订单创建不是单条 SQL，而是扣库存、写订单、写订单项、清购物车的组合操作。如果没有事务，就可能出现库存扣了但订单没生成、订单生成了但库存没扣、购物车清空但订单失败等脏状态。

### 在本项目中的具体应用场景

本项目订单模块是验收重点，尤其是“100 人抢 80 份商品不能超卖”。下单时必须同时扣库存和保存订单信息。取消订单也要回补库存。项目把创建订单、直购下单、支付、发货、确认收货、取消订单都放在 Service 层事务方法中，确保状态变化和库存变化受同一事务管理。

### 实际实现方式（必须引用真实代码）

`backend/src/main/java/com/example/smartmall/service/OrderService.java` 中 `create`、`createDirect`、`pay`、`ship`、`confirm`、`cancel` 方法都标注了 `@Transactional`。`create` 先通过 `carts.findByUserIdAndIdIn` 校验购物车项，再创建 `Order`，循环读取 `Product`，调用 `products.deductStock(product.id, cart.quantity, product.version)`，写 `OrderItem` 快照，最后更新订单总额并 `carts.deleteByUserIdAndIdIn`。如果扣库存影响行数为 0，会抛出 `BizException.badRequest("商品「...」库存不足")`，整个事务回滚。

### 应用效果

即使 `create` 中先保存了订单主表，只要后续扣库存或写订单项失败，事务都会回滚，数据库不会留下脏订单。`createDirect` 是实训并发验收端点 `/api/orders/direct` 的服务方法，减少购物车唯一键对并发测试的干扰，专门验证库存事务。后端日志会在下单成功、库存不足、支付、发货、取消等节点打 INFO 日志，便于答辩现场用 `docker compose logs backend` 观察。

### 可能被追问的延伸问题预判

可能被问：为什么事务放在 Service 而不是 Controller？运行时异常和受检异常对回滚有什么区别？如果扣库存成功后插入订单项失败会怎样？事务隔离级别有没有显式配置？

## MySQL 三范式与表结构设计

### 通用原理

数据库三范式（3NF）要求表字段围绕单一实体设计，非主属性依赖主键，不能依赖其他非主属性，避免冗余和更新异常。第一范式强调字段原子性，第二范式强调非主属性完全依赖主键，第三范式强调非主属性之间不能传递依赖。工程上并不是绝对禁止所有冗余，历史快照、审计字段等可以为了业务事实保留，但必须能解释清楚不是随意重复字段。

### 在本项目中的具体应用场景

商城涉及用户、分类、商品、购物车、订单、订单项、Agent 会话、用户偏好。用户信息不放在订单项里，商品分类独立成表，购物车使用 `(user_id, product_id)` 唯一约束，订单主表只保存订单整体信息，订单项保存下单时的商品快照。Agent 专属记忆表与业务表分开，避免把 AI 偏好塞进用户主表造成职责混乱。

### 实际实现方式（必须引用真实代码）

表结构在 `docs/seed.sql` 和 `docs/er-diagram.md`。`user` 表包含 `username`、`password_hash`、`phone`、`role`、`status` 等用户属性；`product` 表通过 `category_id` 外键关联分类，含 `stock`、`sales_count`、`version`；`order` 表保存 `order_no`、`user_id`、`total_amount`、`status`、`shipping_address`、`paid_at`；`order_item` 表保存 `product_name_snapshot` 和 `price_snapshot`。JPA 实体在 `backend/src/main/java/com/example/smartmall/domain/`，例如 `Order.java` 映射到反引号包裹的 `order` 表，`Product.java` 映射 `sales_count`、`image_url` 等字段。

### 应用效果

ER 图能解释全部外键关系。`order_item.product_name_snapshot` 和 `price_snapshot` 是“下单时刻历史事实”，不是从当前 `product` 表可可靠推导的数据，因为商品后续可能改名或改价；这在答辩中要明确说明。`product.version` 不是业务派生字段，而是并发控制字段。`user_preference` 用 `(user_id, preference_tag)` 唯一键表达长期偏好，不破坏业务表的范式边界。

### 可能被追问的延伸问题预判

可能被问：订单总金额是否违反三范式？价格快照为什么合理？购物车为什么不放商品名？Agent 记忆表为什么没有外键到 user？分类 parent_id 自关联怎么解释？

## 乐观锁与并发库存控制

### 通用原理

乐观锁假设冲突不是常态，更新时通过版本号或条件判断发现冲突。它不在读取阶段长时间锁住数据，而是在写入阶段检查“我读到的数据是否仍然有效”。悲观锁则假设冲突常发生，读取时就加锁。电商下单扣库存可以用原子条件更新实现并发安全：只有库存足够且版本匹配时才扣减，影响行数为 0 就说明库存不足或版本已变化，需要失败重试或提示用户。

### 在本项目中的具体应用场景

实训 PPT 的“100 人订红烧肉只有 80 份”对应到本项目的抢购测试商品，库存为 80。项目不引入 Redis 预扣库存、消息队列或秒杀限流，而是用 MySQL 原子 UPDATE + `version` 乐观锁解决实训规模并发。取消订单时也要原子回补库存，避免并发取消和新下单竞争导致库存不一致。

### 实际实现方式（必须引用真实代码）

`backend/src/main/java/com/example/smartmall/repo/ProductRepository.java` 中 `deductStock` 是 `@Modifying(flushAutomatically = true, clearAutomatically = true)` JPQL 更新：`set p.stock = p.stock - :quantity, p.version = p.version + 1, p.salesCount = p.salesCount + :quantity where p.id = :id and p.stock >= :quantity and p.version = :version`。`restoreStock` 则是 `stock = stock + :quantity, version = version + 1, salesCount = case when ...`，用于取消订单回补。`OrderService.create` 和 `createDirect` 都检查 `affected == 0` 并抛出库存不足业务异常。

### 应用效果

正常扣减时库存减少、版本号增加、销量增加；版本过期或库存不足时影响行数为 0，事务回滚。`backend/src/test/java/com/example/smartmall/OrderStockSqlTest.java` 验证了一个关键场景：第一次用版本 0 扣 2 件成功，第二次继续用旧版本 0 扣库存失败，最终 stock 为 0、version 为 1。并发验收脚本是 `scripts/concurrent_order_test.ps1`，打 `/api/orders/direct`。注意当前 compose 后端宿主端口是 8081，运行脚本时建议传 `-BaseUrl http://localhost:8081`。

### 可能被追问的延伸问题预判

可能被问：为什么同时用了 `stock >= quantity` 和 `version = version`？如果两个用户读到同一 version 谁成功？取消订单回补是否需要 version 条件？JPA bulk update 为什么要 `clearAutomatically`？

## 商品搜索与排序分页

### 通用原理

搜索模块通常包括关键词匹配、排序和分页。中文全文检索可以用 MySQL ngram 全文索引，也可以在教学项目中降级为 `LIKE` 模糊匹配。分页的核心是把一次大查询拆成页，后端返回总数和当前页列表，前端根据总数控制上一页/下一页。排序则把业务优先级体现在查询里，例如销量优先、价格升降、最新上架。

### 在本项目中的具体应用场景

商品模块和 Agent 都依赖搜索。前端商品中心需要搜索“键盘”、按销量或价格排序并分页；Agent 的 `search_products` 工具也调用后端 `/api/products/search`，确保推荐基于真实商品数据。项目 seed SQL 创建了 MySQL `FULLTEXT INDEX ft_name_desc ... WITH PARSER ngram`，但当前后端实际查询实现采用了 JPA `LIKE` 兜底方案。

### 实际实现方式（必须引用真实代码）

`ProductRepository.search` 使用 JPQL：`where p.status = 1 and (:keyword is null or lower(p.name) like lower(concat('%', :keyword, '%')) or lower(p.description) like lower(concat('%', :keyword, '%')))`。`ProductController.search` 创建 `PageRequest`，页码用 `Math.max(page, 1) - 1`，size 限制在 1 到 100，排序是 `salesCount desc, id desc`。普通列表 `ProductController.list` 支持 `sort=sales_desc/price_asc/price_desc/new_desc`，由私有方法 `toSort` 转换成 Spring Data `Sort`。

### 应用效果

搜索“键盘”能命中名称或描述包含“键盘”的商品，结果结构为 `{ total, list }`。page 超出最大页时，Spring Data 返回空 `content` 而不是抛错。前端 `ProductListView.vue` 中 `searchMode` 控制走搜索接口还是列表接口，分页器根据 `page * size >= total` 禁用下一页。这里需要诚实说明：当前代码没有使用 `MATCH ... AGAINST` 执行全文检索，而是保留索引 DDL、实际用 LIKE 兜底，符合项目规格中允许简化的路线。

### 可能被追问的延伸问题预判

可能被问：LIKE 和 FULLTEXT 的性能差异是什么？为什么保留全文索引但代码没用？分页为什么要限制 size 最大值？中文搜索为什么需要 ngram？

## 订单状态机

### 通用原理

状态机是用有限状态和合法转移约束业务流程的方式。订单不是任意字符串，它应该按业务规则从待付款到已支付、已发货、已完成，或者从待付款取消。状态机的好处是防止非法跳转，例如未付款订单不能直接确认收货，已完成订单不能再取消。

### 在本项目中的具体应用场景

项目要求订单状态流转为 `PENDING_PAYMENT -> PAID -> SHIPPED -> COMPLETED`，以及 `PENDING_PAYMENT -> CANCELLED`。支付使用模拟支付，不接真实支付宝/微信。前端订单页按状态筛选并显示对应操作按钮，后端负责最终约束，不能只依赖前端隐藏按钮。

### 实际实现方式（必须引用真实代码）

`OrderService` 中 `pay` 调用 `requireStatus(order, "PENDING_PAYMENT")` 后设置 `PAID` 和 `paidAt`；`ship` 要求 `PAID` 后设置 `SHIPPED`，并由 `OrderController.ship` 加 `@PreAuthorize("hasRole('ADMIN')")`；`confirm` 要求 `SHIPPED` 后设置 `COMPLETED`；`cancel` 要求 `PENDING_PAYMENT` 后设置 `CANCELLED` 并遍历订单项回补库存。私有方法 `requireStatus` 如果状态不匹配，抛出 `BizException.badRequest("订单状态不允许该操作")`。

### 应用效果

非法状态跳转会被后端拒绝。订单列表 `frontend/src/views/OrdersView.vue` 定义状态 tab：全部、待付款、待发货、待收货、已完成、已取消；待付款订单显示“模拟支付”和“取消订单”，已发货订单显示“确认收货”。这让答辩演示可以按业务流程操作，而不是直接改数据库状态。

### 可能被追问的延伸问题预判

可能被问：为什么取消订单只允许待付款？已支付取消是否需要退款流程？发货为什么只有管理员？状态字段为什么不用枚举类型映射？

## 单元测试、烟测与缺陷管理

### 通用原理

单元测试关注小范围逻辑，例如密码哈希和库存扣减 SQL；集成测试关注多个模块串联，例如注册、登录、下单、查询订单；缺陷管理记录问题、复现步骤、状态和修复说明。答辩时测试不一定要覆盖所有分支，但必须能说明测试覆盖了项目最高风险处，并承认仍有哪些测试缺口。

### 在本项目中的具体应用场景

最高风险是订单并发和认证安全，其次是 Agent 工具调用是否真的基于真实数据。项目用 JUnit 测 BCrypt 和库存 SQL，用 PowerShell 脚本做并发下单验收，用 Python 脚本做 Agent 商品工具、订单工具、长期记忆和非购物拒答烟测。缺陷记录集中在 `docs/bug-list.md`。

### 实际实现方式（必须引用真实代码）

`PasswordEncoderTest` 直接使用 `BCryptPasswordEncoder`，断言 hash 以 `$2a$10$` 开头，正确密码匹配、错误密码不匹配。`OrderStockSqlTest` 使用 `@DataJpaTest` 和 H2，创建 Product 后调用 `products.deductStock`，验证旧 version 更新失败。`scripts/concurrent_order_test.ps1` 登录获取 Token 后启动多个 PowerShell Job 并发请求 `/api/orders/direct`。`scripts/agent_smoke.py` 依次请求 `/agent/chat`，要求 `toolsUsed` 包含 `search_products` 或 `query_order_status`，并验证天气问题不调用工具。

### 应用效果

这些测试能覆盖答辩中最容易被追问的点：密码不是明文、乐观锁确实生效、Agent 确实有工具调用和记忆演示。但也要诚实说明当前测试仍有缺口：没有完整的 RestAssured “注册-登录-浏览-下单-查询”自动化脚本；并发脚本统计成功失败，但没有自动查询最终 stock；`docs/bug-list.md` 中 B001 仍标为 Open，说明并发扣库存需要在联调环境继续回归。

### 可能被追问的延伸问题预判

可能被问：为什么库存测试用 H2 也能说明问题？并发测试成功数如何核对为 80？哪些模块最需要补测试？缺陷状态 Open 和 Fixed 分别代表什么？

## LangChain 智能体架构

### 通用原理

Agent 是能在模型推理过程中选择工具、执行工具并基于结果继续生成答案的智能体。LangChain 提供了 Agent、Tool、Prompt、Memory 等抽象。工具调用不是简单地把数据库结果拼给模型，而是让模型在系统提示和工具描述约束下决定是否调用工具、调用哪个工具、传什么参数，然后把工具返回结果作为上下文继续回答。

### 在本项目中的具体应用场景

智能商城要求 AI 导购能回答商品推荐和订单查询，还要有短期/长期记忆。只调用一次大模型回答“推荐键盘”容易编造商品名，不满足验收。项目把 Agent 做成 FastAPI 服务，用 LangChain 绑定 `search_products` 和 `query_order_status` 两个工具，让推荐和订单查询都落到真实后端接口。

### 实际实现方式（必须引用真实代码）

`agent-service/app/main.py` 引入 `create_tool_calling_agent`、`AgentExecutor`、`ChatPromptTemplate` 和 `@tool`。`TOOLS = [search_products, query_order_status]`。`run_langchain_agent` 创建 `ChatOpenAI` 模型，构造 system prompt，加入 `MessagesPlaceholder("agent_scratchpad")`，再用 `create_tool_calling_agent(model, TOOLS, prompt)` 和 `AgentExecutor(..., return_intermediate_steps=True)` 执行。执行结果中 `intermediate_steps` 被提取为 `tools_used`，最终返回给前端。

### 应用效果

当用户问“有没有适合敲代码的键盘，预算500”，系统提示要求商品问题必须调用商品搜索工具，返回的 `toolsUsed` 应包含 `search_products`。当用户问“我上次买的东西到哪了”，应调用 `query_order_status`。如果 `LLM_API_KEY` 没配置或 LangChain 调用异常，`handle_chat` 会记录 `langchain agent unavailable, using offline fallback`，然后走规则兜底，但兜底仍然调用真实工具，保证答辩主链路可演示。

### 可能被追问的延伸问题预判

可能被问：Agent 和普通 ChatBot 的区别是什么？工具调用决策是代码 if else 还是模型推理？如果模型不按要求调用工具怎么办？离线兜底算不算真正 Agent？

## Tool Calling 工具调用机制

### 通用原理

工具调用把外部能力包装成模型可理解的函数接口。工具需要有名称、参数和自然语言描述，模型根据用户问题生成工具调用请求，程序执行真实函数并把结果交回模型。工具调用的关键不是“能不能调用函数”，而是让模型答案受到真实数据约束，降低幻觉。

### 在本项目中的具体应用场景

商品推荐必须基于后端商品表，订单查询必须基于用户真实订单。项目至少实现两个工具：商品搜索和订单状态查询。商品工具可带预算过滤，订单工具用内部服务鉴权请求主后端，避免 Agent 直接查业务库或伪造普通用户 Token。

### 实际实现方式（必须引用真实代码）

`agent-service/app/main.py` 中 `search_products(keyword: str, max_price: float | None = None)` 被 `@tool` 装饰，内部用 `httpx.Client` 请求 `${BACKEND_BASE}/api/products/search`，取响应 JSON 的 `list`，按 `max_price` 可选过滤，返回“商品名 - 价格 - 库存”的文本。`query_order_status(user_id: int, order_id: int | None = None)` 请求 `/api/internal/orders` 或 `/api/internal/orders/{id}`，带 `_internal_headers()` 返回的 `X-Internal-Service: agent` 和 `X-Internal-Secret`。工具调用会通过 logger 打 INFO 日志。

### 应用效果

前端聊天气泡底部会显示 `tools: search_products` 或 `tools: query_order_status`，便于现场证明不是静态回复。Agent smoke 脚本也用 `toolsUsed` 判断工具是否被调用。预算提取由 `extract_budget` 负责，已经修复“推荐3款键盘”被误判为 3 元预算的问题：当前正则要求出现“预算、不超过、元、以内、¥”等预算语境才返回金额。

### 可能被追问的延伸问题预判

可能被问：工具返回的是字符串还是结构化 JSON？为什么最多取 5 个商品？如果后端接口超时怎么办？Agent 是否可能用用户 A 的 id 查用户 B 的订单？

## 短期记忆与长期记忆

### 通用原理

短期记忆通常保存当前会话最近几轮对话，帮助模型理解上下文；长期记忆保存跨会话稳定偏好，如用户喜欢机械键盘、预算敏感。短期记忆可以放内存，重启后丢失也能接受；长期记忆必须持久化，下一次新会话仍能读取。实际系统常把短期记忆和长期记忆结合使用。

### 在本项目中的具体应用场景

用户在一个会话里说“我喜欢机械键盘，预算 500”，后续问“那有没有更安静一点的”需要短期上下文。用户开启新 session 后问“有什么推荐”，系统要能从长期偏好中知道他偏向键盘或办公学习。项目用 `agent_conversation` 保存对话兜底，用 `user_preference` 保存偏好标签和权重。

### 实际实现方式（必须引用真实代码）

`agent-service/app/main.py` 定义 `memory: dict[str, deque[dict[str, str]]] = defaultdict(lambda: deque(maxlen=12))`，key 是 `userId:sessionId`。`load_history(session_id, user_id)` 先查内存，没有则从 `agent_conversation` 读取最近 12 条。`save_message` 同时写内存和 MySQL。长期记忆由 `preference_tags(user_id)` 查询 `user_preference` 权重最高的 3 个标签；`upsert_preferences` 用 `ON DUPLICATE KEY UPDATE weight = LEAST(2.0, weight + 0.1)` 更新权重。`extract_preferences` 先用规则提取，再在有偏好信号时调用 LLM 提炼 JSON 标签。

### 应用效果

`/health` 返回 `shortTermMemory: "in-memory window plus agent_conversation fallback"` 和 `longTermMemory: "user_preference"`。新对话生成 system prompt 时，`system_prompt(tags)` 会写入“该用户的历史偏好标签：...”，推荐商品时优先考虑这些偏好。`docs/seed.sql` 已给用户 2 写入 `机械键盘` 偏好，方便答辩演示长期记忆。

### 可能被追问的延伸问题预判

可能被问：短期记忆为什么 maxlen=12？长期偏好如何避免越积越多？规则提取和 LLM 提取哪个优先？服务重启后短期记忆还能恢复吗？

## Agent 与主系统的服务间鉴权

### 通用原理

服务间鉴权用于控制微服务之间的内部 API 调用。用户 JWT 代表用户本人，不能随便交给后端服务互相使用；服务间调用应使用独立凭据，并限制可访问范围。简单项目可用共享密钥请求头，复杂系统可用 mTLS、OAuth2 client credentials 或专用服务 Token。

### 在本项目中的具体应用场景

Agent 查询订单时需要知道“该用户最近订单”，但前端没有把用户 JWT 交给 Agent 作为主后端访问凭据。项目采用共享密钥方案：Agent 请求主后端内部订单接口时带 `X-Internal-Service: agent` 和 `X-Internal-Secret`，主后端校验后只返回订单摘要，不返回密码等敏感字段。

### 实际实现方式（必须引用真实代码）

后端 `OrderController` 注入 `@Value("${app.internal-service-secret}") String internalSecret`，内部接口为 `GET /api/internal/orders` 和 `GET /api/internal/orders/{id}`。私有方法 `checkInternal(String service, String secret)` 要求 `service` 等于 `agent` 且 `secret` 等于配置值，否则抛出 403“内部服务认证失败”。Agent 端 `_internal_headers()` 在 `agent-service/app/main.py` 生成相同请求头，`query_order_status` 调用内部接口时附带这些 header。`docker-compose.yml` 同时给后端和 Agent 注入 `INTERNAL_SERVICE_SECRET`。

### 应用效果

Agent 不直接读取 `order` 表，不绕过主系统权限边界；后端只暴露 `InternalOrderResponse`，字段是 `id`、`orderNo`、`status`、`totalAmount`、`createdAt`。`SecurityConfig` 目前对 `/api/internal/**` 是 `permitAll`，但真实鉴权在 Controller 方法内完成。这是教学项目可接受的轻量方案，答辩时要主动说明它不是生产级零信任方案。

### 可能被追问的延伸问题预判

可能被问：共享密钥泄露怎么办？为什么 `/api/internal/**` 在 Security 里 permitAll？Agent 传入 userId 是否可能被前端篡改？生产环境会怎么改？

## Docker 容器化与 docker-compose 编排

### 通用原理

Docker 把应用和运行环境打包成镜像，减少“我电脑能跑、你电脑不能跑”的问题。docker-compose 用一个 YAML 文件编排多个容器、网络、端口、环境变量和 volume。容器之间可以用服务名通信，宿主机通过端口映射访问容器。健康检查和日志挂载有助于部署验收。

### 在本项目中的具体应用场景

本项目有 MySQL、Spring Boot、FastAPI Agent、Vue/Nginx 四个服务。人工分别启动容易出错，所以用 compose 一键启动。MySQL 初始化挂载 `docs/seed.sql`，后端等待 MySQL healthcheck，Agent 等待 MySQL 和后端，前端依赖后端和 Agent。日志方面，后端 `logging.file.path: logs`，compose 挂载 `backend_logs:/app/logs`。

### 实际实现方式（必须引用真实代码）

`docker-compose.yml` 定义 `mysql`、`backend`、`agent-service`、`frontend` 和三个 volumes。`mysql` 使用 `--character-set-server=utf8mb4`、`--collation-server=utf8mb4_unicode_ci` 和 `--ngram-token-size=2`，宿主端口 `3307`。`backend/Dockerfile` 当前是多阶段 Maven 构建：第一阶段 `maven:3.9-eclipse-temurin-17` 执行 `dependency:go-offline` 和 `clean package -DskipTests`，第二阶段 `eclipse-temurin:17-jre` 复制 jar。`frontend/Dockerfile` 用 Node 构建再复制到 Nginx。`agent-service/Dockerfile` 用 Python 3.11 slim 安装 requirements 后启动 uvicorn。

### 应用效果

当前 clean checkout 能通过镜像内构建后端，而不是依赖宿主已有 `target/*.jar`。这和项目过程中一度采用的“本机预编译 + 单阶段 Dockerfile”不同：`docs/bug-list.md` 的 B011 记录该方案导致 clean checkout 无 jar 时 compose 构建失败，后来修复为多阶段 Maven 构建。前端 `.dockerignore` 排除了 `node_modules` 和 `dist`，避免 Windows 宿主依赖污染 Linux 镜像。

### 可能被追问的延伸问题预判

可能被问：compose 的 `depends_on` 是否等于服务完全可用？为什么后端宿主端口是 8081？容器内服务名如何解析？多阶段构建和本机预编译的权衡是什么？

## 应用日志与基础监控

### 通用原理

应用监控不一定一开始就上 Prometheus/Grafana。实训项目更重要的是关键业务日志：请求失败原因、下单成功/失败、Agent 工具调用、模型调用耗时。日志能帮助定位问题，也能在答辩时证明系统确实执行了某些动作。

### 在本项目中的具体应用场景

订单和 Agent 是最需要日志的模块。订单要能看到扣库存失败、下单成功、支付、发货、取消回补库存；Agent 要能看到服务是否启用 LLM、调用了哪个工具、耗时在哪里。部署后可以用 `docker compose logs backend` 和 `docker compose logs agent-service` 查看。

### 实际实现方式（必须引用真实代码）

后端 `application.yml` 配置 `logging.file.path: logs` 和 `logging.level.com.example.smartmall: INFO`。`OrderService` 使用 `LoggerFactory.getLogger(OrderService.class)`，在 `create order failed`、`create order success`、`direct order success`、`order paid`、`order shipped`、`order completed`、`order cancelled and stock restored` 等节点输出 INFO。Agent 的 `main.py` 用 `logging.basicConfig(level=logging.INFO, ...)`，`search_products`、`query_order_status`、`run_langchain_agent`、`handle_chat` 都会记录工具、完成状态和 `timing breakdown`。

### 应用效果

答辩现场可以边操作前端边看日志，说明请求确实经过了后端事务和 Agent 工具，而不是静态假数据。Agent 的 `/health` 还能返回 `llmEnabled`、工具列表和记忆机制说明，快速确认模型配置是否生效。项目没有引入重型监控系统，这是符合实训范围的简化。

### 可能被追问的延伸问题预判

可能被问：日志里会不会泄露 Token 或密码？如何根据日志定位下单失败？Agent 20 秒慢在哪里？生产环境如何扩展成指标监控？

## Git 版本控制实践

### 通用原理

Git 记录代码变更历史，支持分支、提交、回滚、协作和代码审查。实训项目不一定要完整 CI/CD，但至少要能通过提交记录说明项目从骨架到模块集成再到 Agent 完成的演进过程。`.gitignore` 用来防止提交依赖目录、构建产物和密钥。

### 在本项目中的具体应用场景

项目需要证明团队协作和 AI 辅助开发过程。当前仓库最近提交包括 `Initial commit`、`init: 完整项目骨架，含五大模块代码`、`feat: integrate DeepSeek AI shopping agent`、`feat: 完成AI导购Agent功能开发`。当前只有 `main` 和 `origin/main` 分支可见，没有本地 feature 分支残留，因此答辩时不要声称仓库里保留了多个模块分支。

### 实际实现方式（必须引用真实代码）

根目录 `.gitignore` 已排除 `node_modules/`、`target/`、`.env`、`__pycache__/` 等敏感或生成内容。`git log --oneline --decorate -n 12` 显示当前 HEAD 在 `main` 和 `origin/main`，最新提交是 `4d51450 feat: 完成AI导购Agent功能开发`。`docs/bug-list.md` 用缺陷表格记录 B001 到 B014，弥补了没有外部缺陷系统的问题。

### 应用效果

Git 记录能支撑“从项目骨架到 Agent 集成”的演进叙述，但分支协作证明较弱。答辩时建议诚实说：项目当前仓库保留的是 main 分支提交历史，团队分工可以通过 README 的分工建议、模块目录和提交记录解释；如果老师严格追问 PR 记录，需要承认这是实训简化，没有完整保留 PR 流程证据。

### 可能被追问的延伸问题预判

可能被问：为什么没有 feature 分支？`.env` 为什么不能提交？如何回滚一次错误提交？AI 生成代码如何进入 Git 审核流程？

## Claude Code / Codex 辅助开发方式

### 通用原理

AI 辅助开发不是让模型替代工程判断，而是把它用于需求拆解、代码生成、排错、文档生成和测试补充。关键是“生成后验证”：读代码、跑测试、看日志、做端到端验收。AI 可能会误判环境、引入过度设计、把历史方案改回有问题的方案，因此人需要通过版本控制和测试守住质量边界。

### 在本项目中的具体应用场景

本项目适合用 AI 辅助快速搭建多端工程：Spring Boot 接口、Vue 页面、FastAPI Agent、Docker Compose、文档和答辩材料。也确实暴露了 AI 辅助开发的风险：任务文档提到曾经有 Docker 网络、Maven 超时、LLM 未启用、预算正则误伤、个人资料覆盖、库存回滚并发安全等问题，需要通过日志、源码和缺陷表逐项核查，而不是相信生成代码“看起来完整”。

### 实际实现方式（必须引用真实代码）

本次答辩材料生成前实际读取了 `docs/ai-development-record.md`、`docs/*.md`、`backend/src/main/java/...`、`agent-service/app/main.py`、`frontend/src/...`、`docker-compose.yml` 和测试脚本，并把结论绑定到具体文件。项目中 AI 相关实现集中在 `agent-service/app/main.py`，而 AI 辅助开发证据则可用 Git 提交、缺陷清单和本次生成的 `docs/defense` 三份文档展示。一个真实反思点是：历史上“本机预编译 + 单阶段 Dockerfile”曾解决网络慢的问题，但后来又因 clean checkout 无 jar 变成部署缺陷，最终当前代码改为多阶段 Maven 构建。

### 应用效果

AI 明显提升了多模块项目的推进速度，尤其是接口、页面、Docker 和文档的初稿产出。但项目也说明 AI 生成内容必须经过人工审查：例如当前搜索实现是 LIKE 不是真正 MATCH 全文检索，不能在答辩中夸大；B001 并发扣库存仍标 Open，不能说已经完成所有验收；Git 分支证据不足，也不能编造 PR 流程。

### 可能被追问的延伸问题预判

可能被问：AI 帮你写了什么，哪些是你验证过的？有没有 AI 写错的例子？如果不用 AI 这个项目要多久？如何防止 AI 生成代码和实际需求不一致？
