# API Spec

基础地址：`http://localhost:8080`

## Auth

- `POST /api/auth/register` `{ "username": "...", "password": "...", "phone": "..." }`
- `POST /api/auth/login` `{ "username": "...", "password": "..." }`
- `POST /api/auth/refresh` `{ "refreshToken": "..." }`
- `GET /api/user/profile` 需 `Authorization: Bearer <token>`
- `PUT /api/user/profile` `{ "phone": "...", "avatarUrl": "..." }`

## Product

- `GET /api/categories`
- `GET /api/products?categoryId=1&page=1&size=20&sort=sales_desc`
- `GET /api/products/search?keyword=键盘&page=1&size=20`
- `GET /api/products/{id}`
- `POST /api/products` 管理员
- `PUT /api/products/{id}` 管理员
- `DELETE /api/products/{id}` 管理员

## Cart And Orders

- `GET /api/cart`
- `POST /api/cart` `{ "productId": 1, "quantity": 2 }`
- `PUT /api/cart/{id}` `{ "quantity": 3 }`
- `DELETE /api/cart/{id}`
- `POST /api/orders` `{ "cartItemIds": [1,2], "shippingAddress": "..." }`
- `POST /api/orders/direct` `{ "productId": 21, "quantity": 1, "shippingAddress": "..." }` 实训并发验收端点
- `GET /api/orders?status=all`
- `GET /api/orders/{id}`
- `PUT /api/orders/{id}/pay`
- `PUT /api/orders/{id}/cancel`
- `PUT /api/orders/{id}/ship` 管理员
- `PUT /api/orders/{id}/confirm`

## Agent

基础地址：`http://localhost:8000`

- `POST /agent/chat` `{ "userId": 2, "sessionId": "uuid", "message": "有没有适合敲代码的键盘，预算500" }`
- `GET /agent/history?sessionId=uuid`

Agent 调主后端订单接口使用 `X-Internal-Service: agent` 与 `X-Internal-Secret`，主系统只开放受限内部订单查询。
