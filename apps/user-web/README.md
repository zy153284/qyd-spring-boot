# 去运动用户端

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + Vant 4 的响应式用户端。

## 启动

```bash
copy .env.example .env
npm install
npm run dev
```

默认浏览器请求 `/api`，Vite 将其代理到 `http://localhost:8080`。可用 `VITE_DEV_PROXY_TARGET` 修改开发后端地址。生产环境应将 `VITE_API_BASE_URL` 配成网关地址或由 Web 服务器反向代理。环境文件不得保存密码、JWT 或支付签名。

```bash
npm test
npm run build
```

## 路由

- `/login`：账号密码登录
- `/`：首页、城市状态、搜索和分类
- `/venues`、`/venues/:id`：场馆列表、资源、SKU 与可售库存时段
- `/checkout`：订单确认
- `/orders`、`/orders/:id`：订单列表、详情、取消、支付查询与退款
- `/profile`：个人中心
- `/services/:kind`：收藏、优惠券、会员、积分、资讯的建设中页面

除登录外的路由都要求认证。Axios 自动附加 Bearer token；401 时所有并发失败请求共享一次 refresh 请求，刷新失败会清除 token 并返回登录页。

## 实际后端契约

- `POST /api/v1/auth/login|refresh`：响应为 `ApiResponse<Tokens>`，客户端提取 `data`
- `GET /api/v1/venues`、`GET /api/v1/venues/{id}`
- `GET /api/v1/catalog/categories|resources|skus`
- `GET /api/v1/inventory/slots?skuId=...`
- `POST /api/v1/orders`：请求必须带 `Idempotency-Key`
- `GET /api/v1/orders`、`GET /api/v1/orders/{id}`、`POST /api/v1/orders/{id}/cancel`
- `POST /api/v1/payments`、`GET /api/v1/payments/{id}`、`POST /api/v1/payments/{id}/refunds`

支付创建会返回 `mock://` checkout token。用户端不调用支付提供方回调，而是轮询支付查询接口；回调应由模拟支付提供方或后端测试设施触发。当前订单 DTO 不包含支付单 ID，因此只有创建支付后的结果页能直接查询支付及申请退款。

城市、跨字段服务端搜索、收藏、优惠券、会员、积分和资讯暂时没有后端 Controller。对应入口明确显示“功能建设中”，没有模拟成功数据。
