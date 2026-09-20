# 去运动管理端

Vue 3 + TypeScript + Vite 管理端，直接对接 `qyd-platform/backend` 当前实际接口。页面不会为后端未实现能力伪造数据。

## 启动

```bash
cp .env.example .env
npm install
npm run dev
```

默认前端端口 `3001`，Vite 将 `/api` 代理至 `http://localhost:8080`。后端 dev profile 需要设置 `QYD_ADMIN_PASSWORD`。

```bash
npm test
npm run build
```

## 路由与权限

- `/dashboard`：真实接口聚合仪表盘
- `/venues`：场馆增删改查
- `/catalog`：运动分类、资源、SKU 增删改查
- `/inventory`：时段库存查询和创建
- `/orders`、`/orders/:id`：订单分页、详情、取消
- `/payments`：支付详情查询和退款
- `/redemption`：服务码核销
- `/accounts`、`/content`、`/settlement`、`/risk`：规范占位页，明确“功能建设中”

路由通过 `requiredPermissions` 和角色动态生成菜单，按钮使用 `v-permission`。五类管理角色为 `PLATFORM_ADMIN / OPERATOR / FINANCE / VENUE_ADMIN / VENUE_STAFF`。由于当前后端 JWT 只有 `ADMIN / OPERATOR / MERCHANT / USER`，前端临时映射：

- `ADMIN → PLATFORM_ADMIN`
- `OPERATOR → OPERATOR`
- `MERCHANT → VENUE_ADMIN`
- `USER` 禁止进入管理端

该映射和权限矩阵只是体验层辅助控制。生产环境必须由后端返回有效权限、`permissionVersion`、数据范围及 `venueIds`，且每个命令必须做 RBAC、DataScope、ResourceOwnership 强校验。

## API 对接

- 认证：`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`
- 场馆：`/api/v1/venues`
- 分类/资源/SKU：`/api/v1/catalog/categories|resources|skus`
- 库存：`/api/v1/inventory/slots`
- 订单：`/api/v1/orders`、`/{id}`、`/{id}/cancel`、`/redeem`
- 支付/退款：`GET /api/v1/payments/{id}`、`POST /{id}/refunds`

Axios 自动附加 Bearer token；401 只进行一次共享刷新并重放原请求，403 不刷新；错误展示 ProblemDetail 与 `traceId`。

## 当前后端限制

1. 后端没有 `/me`、权限清单或数据范围接口，JWT 仅含基础角色；前端只能使用兼容默认值。
2. 订单列表、详情、取消和支付/退款均按登录用户本人校验，不是平台或场馆管理接口，缺少订单条件筛选。
3. 支付没有列表接口，只能按支付实体 ID 查询；退款没有申请、审核、复核、执行分离，当前接口会直接调用渠道。
4. 库存只支持查询和创建，不支持修改容量、关闭或删除。
5. 核销接口存在，但后端当前仅要求认证，尚未按场馆归属和核销权限强校验。
6. 账号权限、内容、结算、风控接口尚未实现。
7. 场馆 CRUD 和目录接口目前仅要求登录，后端尚未执行 `docs/permissions.md` 定义的细粒度权限和数据范围。

删除、订单取消、退款、核销及库存创建均有二次确认；这不能替代后端二次认证、双人复核和审计。
