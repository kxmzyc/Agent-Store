# API Spec

## 基础地址

- 后端 API：`http://127.0.0.1:8081`
- Agent API：`http://127.0.0.1:8000`

## 认证

除游客可访问的分类、商品列表、商品详情和商品搜索接口外，业务接口都需要：

```http
Authorization: Bearer <accessToken>
```

管理员接口需要登录用户角色为 `ADMIN`。

## Auth

- `POST /api/auth/register`
  - 请求：`{ "username": "alice", "password": "123456", "phone": "13800000001" }`
  - 响应：`201 { "userId": 1, "username": "alice" }`

- `POST /api/auth/login`
  - 请求：`{ "username": "alice", "password": "123456" }`
  - 响应：`{ "accessToken": "...", "refreshToken": "...", "userInfo": {...} }`

- `POST /api/auth/refresh`
  - 请求：`{ "refreshToken": "..." }`
  - 响应：`{ "accessToken": "..." }`

- `GET /api/user/profile`
- `PUT /api/user/profile`
  - 请求：`{ "phone": "13800000001", "avatarUrl": "https://..." }`

- `GET /api/user/overview`
  - 响应：账户概览数据，包含购物车数量、收藏数量、浏览足迹数量、各订单状态数量、最近浏览和近期收藏。
  - 用途：支撑前端个人中心工作台。

- `GET /api/user/favorites?size=20`
  - 响应：当前用户收藏商品列表。
- `GET /api/user/favorites/{productId}`
  - 响应：`{ "favorited": true }`
- `POST /api/user/favorites/{productId}`
  - 收藏商品。
- `DELETE /api/user/favorites/{productId}`
  - 取消收藏。

- `GET /api/user/view-history?size=20`
  - 响应：当前用户最近浏览商品列表，商品详情页访问时自动记录。
- `DELETE /api/user/view-history`
  - 清空当前用户浏览足迹。

## Product

- `GET /api/categories`
- `GET /api/products?categoryId=1&page=1&size=20&sort=sales_desc`
- `GET /api/products/search?keyword=键盘&page=1&size=20`
- `GET /api/products/{id}`
- `POST /api/products/{id}/view`
  - 游客和登录用户都可调用；前端商品详情页 5 分钟内同商品只上报一次。
- `GET /api/products/recommendations?limit=8`
  - 响应：`{ "source": "personalized", "list": [ { "id": 1, "name": "...", "tags": [{ "id": 3, "name": "爆款" }] } ] }`
  - `source=personalized` 表示使用 Agent 长期偏好和浏览分类；`source=fallback` 表示冷启动销量兜底。
- `GET /api/products/admin?keyword=键盘&categoryId=4&status=1&page=1&size=20`，管理员
- `POST /api/products`，管理员
  - 可选追加：`tagIds: [1, 3]`
- `PUT /api/products/{id}`，管理员
  - 可选追加：`tagIds: [1, 3]`
- `DELETE /api/products/{id}`，管理员，逻辑下架

商品列表和详情响应追加 `tags: [{ "id": 1, "name": "新品" }]`，不改变原字段。

排序参数：

- `sales_desc`
- `price_asc`
- `price_desc`
- `new_desc`

## Admin

- `GET /api/admin/dashboard`，管理员
- `GET /api/admin/orders?status=all&page=1&size=20`，管理员

响应示例：

```json
{
  "metrics": {
    "users": 3,
    "products": 21,
    "soldOutProducts": 1,
    "orders": 5,
    "pendingPaymentOrders": 2,
    "paidOrders": 1,
    "shippedOrders": 1,
    "completedOrders": 1,
    "effectiveRevenue": 1299.00
  },
  "lowStockProducts": [
    { "id": 21, "name": "并发测试商品", "price": 19.90, "stock": 5, "salesCount": 80, "status": 1, "imageUrl": "..." }
  ],
  "topProducts": [],
  "recentOrders": [
    { "id": 1, "orderNo": "SM202606300001", "status": "PAID", "totalAmount": 399.00, "createdAt": "2026-06-30T10:00:00" }
  ]
}
```

该接口支撑前端 `/admin` 运营工作台，用于展示总览指标、低库存预警、热销商品和最近订单。

## Batch 5 Experience APIs

- `GET /api/addresses`
- `POST /api/addresses`
  - 请求：`{ "receiverName": "Alice", "phone": "13800000001", "province": "上海市", "city": "上海市", "district": "浦东新区", "detailAddress": "软件园 1 号楼", "isDefault": true }`
- `PUT /api/addresses/{id}`
- `DELETE /api/addresses/{id}`
- `PUT /api/addresses/{id}/default`

- `GET /api/banner-slots/active`
  - 前台 Banner 调用，返回启用推荐位对应的商品列表。
- `GET /api/admin/banner-slots`
- `POST /api/admin/banner-slots`
  - 请求：`{ "productId": 1, "sortOrder": 0, "active": true }`
- `PUT /api/admin/banner-slots/{id}`
- `DELETE /api/admin/banner-slots/{id}`

- `GET /api/search/hot-keywords`
  - 响应：`[{ "keyword": "机械键盘", "count": 2, "blocked": false, "lastSearchedAt": "2026-07-06T12:20:00" }]`
- `GET /api/admin/search-keywords?page=1&size=20`
- `PUT /api/admin/search-keywords/{keyword}/block`
  - 请求：`{ "blocked": true }`

- `GET /api/admin/tags`
- `POST /api/admin/tags`
  - 请求：`{ "name": "新品" }`
- `PUT /api/admin/tags/{id}`
- `DELETE /api/admin/tags/{id}`

- `GET /api/admin/operation-logs?page=1&size=20&adminId=1&action=product`
  - 响应：`{ "total": 1, "list": [{ "adminId": 1, "action": "product.update", "targetType": "product", "targetId": 1, "createdAt": "..." }] }`

## Cart And Orders

- `GET /api/cart`
- `POST /api/cart`
  - 请求：`{ "productId": 1, "quantity": 2 }`
- `PUT /api/cart/{id}`
  - 请求：`{ "quantity": 3 }`
- `DELETE /api/cart/{id}`

- `POST /api/orders`
  - 请求：`{ "cartItemIds": [1, 2], "shippingAddress": "北京市..." }`
- `POST /api/orders/direct`
  - 请求：`{ "productId": 21, "quantity": 1, "shippingAddress": "并发测试地址" }`
  - 用途：实训并发扣库存验收端点。
- `GET /api/orders?status=all`
- `GET /api/orders/{id}`
- `PUT /api/orders/{id}/pay`
- `PUT /api/orders/{id}/cancel`
- `PUT /api/orders/{id}/ship`，管理员
- `PUT /api/orders/{id}/confirm`
- `POST /api/orders/{id}/rebuy`
  - 用途：把历史订单中的商品重新加入购物车，复用购物车库存校验，用户仍需进入结算页确认地址和金额。

- `POST /api/orders/{id}/after-sale`
  - 请求：`{ "type": 2, "reason": "商品不合适，申请退货退款" }`
  - `type=1` 表示仅退款，`type=2` 表示退货退款；仅允许已发货或已完成订单申请。
- `GET /api/user/after-sales`
  - 响应：当前用户的售后申请列表，包含订单号、退款金额、审核状态和订单明细。
- `GET /api/admin/after-sales?status=0&page=1&size=20`，管理员
  - `status=0` 待审核，`2` 已拒绝，`3` 已完成，`all` 全部。
- `PUT /api/admin/after-sales/{id}/approve`，管理员
  - 请求：`{ "remark": "同意售后申请" }`
  - 退货退款会回补库存，订单使用过的优惠券会恢复为可用，订单状态改为 `REFUNDED`。
- `PUT /api/admin/after-sales/{id}/reject`，管理员
  - 请求：`{ "remark": "不符合售后条件" }`

订单状态：

- `PENDING_PAYMENT`
- `PAID`
- `SHIPPED`
- `COMPLETED`
- `CANCELLED`
- `REFUNDED`

## Internal Agent Endpoints

以下接口只给 Agent 服务调用，不给前端直接使用：

- `GET /api/internal/orders?userId=2&size=3`
- `GET /api/internal/orders/{id}?userId=2`
- `POST /api/internal/cart`
  - 请求：`{ "userId": 2, "productId": 1, "quantity": 1 }`
  - 用途：Agent 在用户明确确认后加入购物车；仍由主后端执行库存校验和购物车写入。

请求头：

```http
X-Internal-Service: agent
X-Internal-Secret: <INTERNAL_SERVICE_SECRET>
```

## Agent

Agent 接口需要携带用户登录 JWT。Agent 会调用主后端 `/api/user/profile` 校验 Token，并以校验出的用户 ID 作为唯一可信身份，不信任请求体中的 `userId`。

- `POST /agent/chat`
  - Header：`Authorization: Bearer <accessToken>`
  - 请求：`{ "sessionId": "uuid", "message": "有没有适合敲代码的键盘，预算500" }`
  - 响应：`{ "reply": "...", "toolsUsed": ["search_products"], "sessionId": "uuid" }`

- `POST /agent/chat/stream`
  - Header：`Authorization: Bearer <accessToken>`
  - Server-Sent Events 流式接口，前端聊天窗可使用。

- `GET /agent/history?sessionId=uuid`
- `GET /agent/preferences`
  - 响应：`[{ "tag": "机械键盘", "weight": 1.2 }]`
- `DELETE /agent/preferences/{tag}`
  - 删除当前登录用户的一条长期偏好标签。

- `GET /health`
  - 响应包含 `tools`、`shortTermMemory`、`longTermMemory`、`llmEnabled`。

Agent 工具清单：

- `search_products`：搜索真实商品，支持预算过滤。
- `get_product_detail`：查询商品详情。
- `compare_products`：对比多个商品。
- `recommend_by_preference`：根据长期偏好标签推荐商品。
- `add_to_cart`：用户明确确认后加入购物车。
- `query_order_status`：查询当前用户订单状态。

Agent 烟测：

```bash
python scripts/agent_smoke.py --base-url http://127.0.0.1:8000 --backend-url http://127.0.0.1:8081
```
