# REST API 与数据约定

## 1. 基础约定

- 基础路径：`/api/v1`；用户端与管理端使用同一领域 API，通过权限隔离，不复制两套业务接口。
- 管理型聚合查询可放 `/api/v1/admin/...`，但必须调用同一应用服务，不能复制状态变更逻辑。
- JSON 编码固定 UTF-8，字段名 `lowerCamelCase`，枚举值 `UPPER_SNAKE_CASE`，ID 一律以字符串输出，防止 JavaScript 64 位精度丢失。
- 请求/响应 DTO 显式定义并校验，禁止 Controller 接收裸 `Map`、数据库实体或任意 JSON。
- OpenAPI 文件是契约真相源；后端 CI 校验破坏性变更，两端 SDK 从契约生成。
- `Content-Type: application/json`；文件上传使用预签名或专用 `multipart/form-data` 接口。

## 2. 资源与 HTTP 方法

| 意图 | 示例 | 方法与语义 |
|---|---|---|
| 列表 | `/api/v1/venues?cityCode=...` | `GET`，无副作用 |
| 详情 | `/api/v1/orders/{orderId}` | `GET` |
| 创建 | `/api/v1/orders` | `POST`，要求幂等键 |
| 部分修改 | `/api/v1/venues/{venueId}` | `PATCH`，要求 `If-Match` |
| 删除/停用 | `/api/v1/coupons/{id}` | `DELETE`；业务数据通常软删除/停用 |
| 业务命令 | `/api/v1/orders/{id}:cancel` | `POST`，使用动词后缀并要求幂等键 |

不允许使用 `/getOrder`、`/addOrder` 等 RPC 式路径。状态流转只暴露显式命令，如 `:cancel`、`:verify`、`:approve`、`:execute`，禁止通用 PATCH 直接改 `status`。

HTTP 状态：

- `200`：查询、更新或重复幂等请求成功；`201`：首次创建成功并返回 `Location`。
- `202`：已受理异步任务；`204`：成功且无响应体。
- `400`：格式/参数错误；`401`：未认证；`403`：无功能权限；`404`：不存在或越数据范围。
- `409`：状态冲突、版本冲突、幂等冲突、库存冲突；`422`：格式正确但业务规则不允许。
- `429`：限流；`500`：未分类内部错误；`503`：依赖不可用或系统保护。

## 3. 响应格式

成功响应不再包一层模糊的 `code/msg/data`；直接返回资源，追踪信息放响应头：

```json
{
  "id": "202609180001234567",
  "orderNo": "ORD20260918K8M4P2",
  "status": "PAYMENT_PENDING",
  "payableAmount": {
    "amount": "128.00",
    "currency": "CNY"
  },
  "createdAt": "2026-09-18T03:15:30.123Z"
}
```

响应头：

```text
X-Request-Id: 客户端请求号（若合法则回显）
X-Trace-Id: 服务端链路追踪号
ETag: "17"
```

错误统一使用 RFC 9457 `application/problem+json`：

```json
{
  "type": "https://qyd.example/problems/inventory-insufficient",
  "title": "库存不足",
  "status": 409,
  "code": "INV_40901",
  "detail": "所选时段剩余数量不足",
  "instance": "/api/v1/orders",
  "traceId": "8f44d74e1a3d4a1b",
  "errors": [
    {
      "field": "items[0].quantity",
      "reason": "AVAILABLE_QUANTITY_EXCEEDED"
    }
  ]
}
```

生产环境 `detail` 不返回堆栈、SQL、密钥、手机号、支付账号或内部类名。

## 4. 错误码规范

格式：`<模块缩写>_<HTTP状态><两位序号>`。错误码一经发布不得改含义；废弃后保留登记。

| 前缀 | 模块 | 代表错误 |
|---|---|---|
| `COM` | 通用 | `COM_40001` 参数校验失败；`COM_40900` 幂等键被不同请求复用；`COM_40901` 请求处理中；`COM_40902` 资源版本冲突；`COM_50000` 内部错误 |
| `AUTH` | 认证授权 | `AUTH_40101` 令牌缺失；`AUTH_40102` 令牌过期；`AUTH_40301` 权限不足；`AUTH_40302` 数据越权 |
| `MER` | 商户场馆 | `MER_40401` 场馆不存在；`MER_42201` 场馆未通过审核 |
| `CAT` | 商品目录 | `CAT_40401` 商品不存在；`CAT_42201` 商品未上架 |
| `INV` | 库存 | `INV_40901` 库存不足；`INV_40902` 预占已失效；`INV_40903` 库存版本冲突 |
| `ORD` | 订单 | `ORD_40401` 订单不存在；`ORD_40901` 非法状态流转；`ORD_42201` 订单金额校验失败 |
| `PAY` | 支付 | `PAY_40901` 已存在成功支付；`PAY_42201` 回调金额不符；`PAY_50301` 渠道暂不可用 |
| `REF` | 退款 | `REF_40901` 可退金额不足；`REF_40902` 退款状态冲突；`REF_42201` 不满足退款规则 |
| `SET` | 结算 | `SET_40901` 明细已被结算；`SET_40902` 借贷不平；`SET_42201` 尚未满足结算资格 |
| `MKT` | 营销 | `MKT_40901` 优惠券已使用；`MKT_42201` 优惠券不适用 |
| `OPS` | 运营 | `OPS_40901` 工单已处理；`OPS_42201` 缺少复核人 |

前端只根据 `code` 做稳定分支，不解析中文 `detail`。新增错误码必须同步本文件、OpenAPI 和前端错误映射测试。

## 5. 请求校验与并发控制

- 字符串在 DTO 层去除首尾空格后校验长度；后端拒绝未知关键枚举，不能静默使用默认值。
- 手机号、验证码、密码、搜索词设置格式和频率限制；富文本经过白名单消毒。
- 更新聚合使用 `ETag`/`If-Match` 对应 `version`；版本不符返回 `409` + `COM_40902`。
- 创建和业务命令必须携带：

```text
Idempotency-Key: UUID/ULID，长度 16~64
X-Request-Id: 可选 UUID/ULID
```

- 幂等作用域为“主体 + HTTP 方法 + 规范化路径 + 幂等键”。同键同请求返回首次状态码与业务响应；同键不同请求体返回 `COM_40900`。
- 支付/退款渠道回调不要求本平台幂等头，使用渠道事件号和报文摘要去重。

## 6. 分页、排序与过滤

- 普通后台列表使用游标分页：`?limit=20&cursor=...`，`limit` 默认 20、最大 100。
- 响应：

```json
{
  "items": [],
  "page": {
    "nextCursor": "opaque-token",
    "hasMore": false
  }
}
```

- 必须跳页的报表可使用 `page=1&pageSize=20`，最大查询 10,000 行；更大范围走异步导出。
- 排序格式：`sort=-createdAt,orderNo`，只允许端点白名单字段。
- 时间区间使用半开区间 `createdAtFrom <= t < createdAtTo`；禁止通过拼接当天 `23:59:59` 表达闭区间。
- 空值和缺省不同：缺省表示“不修改/不过滤”，显式 `null` 只在接口文档允许时表示“清空”。

## 7. 金额规则

- API 中金额统一为对象 `{ "amount": "128.00", "currency": "CNY" }`；`amount` 是十进制定点字符串，禁止 JSON number。
- Java 使用不可变 `Money(BigDecimal amount, Currency currency)`；数据库 `DECIMAL(19,2)` + `CHAR(3)`。计算中可保留更高精度，入账时统一 2 位。
- 币种首期仅 `CNY`，但每个资金字段仍保存币种；不同币种禁止相加。
- 折扣、费率使用 `DECIMAL`，费率版本随订单快照保存；禁止存 `double`。
- 舍入为逐项还是合计后舍入必须由计价规则显式规定；首期采用“订单项金额先 `HALF_UP` 到分，再汇总”，差额记为独立舍入调整。
- 客户端金额仅供展示。服务端按商品、库存价格和优惠规则重新计价，并校验前端确认版本。
- 退款、结算和对账都以不可变账务分录为准，不通过修改订单金额“修正”差异。

## 8. 时间规则

- 数据库业务时刻使用 `TIMESTAMP(6)`/UTC；Java 使用 `Instant`。API 使用 ISO 8601 UTC，如 `2026-09-18T03:15:30.123Z`。
- 仅日期用 `LocalDate`（`2026-09-18`）；场馆营业时段用场馆 IANA 时区 + `LocalDate/LocalTime`，首期默认 `Asia/Shanghai` 但不得硬编码到领域模型。
- 服务端接收带偏移的时刻并转 UTC；无偏移的日期时间请求返回参数错误。
- `createdAt`、支付渠道时间、回调接收时间、业务发生时间分别保存，不相互覆盖。
- 过期和超时判断使用数据库时间或统一时钟 `Clock`；测试必须注入固定 Clock，禁止直接散用 `LocalDateTime.now()`。
- 日志和审计输出 UTC；管理端根据用户时区展示，并明确标注时区。

## 9. 审计与隐私

下列操作必须写不可变审计日志：登录与失败、令牌撤销、账号冻结、商户/场馆审核、商品上下架、库存人工调整、订单取消、核销/撤销、退款申请/审核/执行、结算生成/确认/打款、人工调账、权限/数据范围变更、敏感字段查看、导出和配置变更。

审计事件最少包含：

```text
auditId、occurredAt、traceId、requestId
actorType、actorId、actorIp、clientId
action、resourceType、resourceId
result、reasonCode、beforeDigest、afterDigest
dataScope、userAgent、metadata
```

- 审计写入与业务变更同事务记录必要事实，再由 Outbox 异步扩展；审计失败时高风险命令必须失败。
- `before/after` 只保存允许字段或摘要；密码、令牌、验证码、支付密钥、银行卡完整号永不进入日志。
- 手机号、身份证明、支付账户在 API 默认脱敏；明文查看要求专门权限、用途原因和审计。
- 业务日志不得用手机号/姓名作为关联键，统一使用主体 ID、订单号和 traceId。

## 10. 认证、安全与回调

- 用户端和管理端使用不同 `clientId` 和刷新令牌 Cookie/安全存储策略；访问令牌放 `Authorization: Bearer ...`。
- 浏览器刷新令牌使用 `HttpOnly + Secure + SameSite` Cookie，并启用 CSRF 防护；禁止存 localStorage。
- CORS 使用环境白名单，禁止生产环境 `*` + 凭证。
- 渠道回调流程固定：读取原始字节 → 限制大小 → 验签 → 校验商户号/订单号/金额/币种 → 持久化原文摘要与去重记录 → 快速返回渠道回执 → 异步执行副作用。
- 回调接口按渠道限流但不能用登录鉴权；密钥来自密钥服务，支持版本化轮换。

## 11. API 示例

创建订单：

```http
POST /api/v1/orders
Authorization: Bearer <access-token>
Idempotency-Key: 01K5F9D5VHP3R1A2Z8Z4M7KQ2N
Content-Type: application/json

{
  "venueId": "1800123456789012345",
  "items": [
    {
      "skuId": "1800123456789012350",
      "inventorySlotId": "1800123456789012351",
      "quantity": 1
    }
  ],
  "userCouponId": "1800123456789012360",
  "pricingVersion": "pv-20260918-3"
}
```

取消订单：

```http
POST /api/v1/orders/1800123456789012400:cancel
Authorization: Bearer <access-token>
Idempotency-Key: 01K5F9F4HBN1ZDQ0EAHFYR7DNC
Content-Type: application/json

{
  "reasonCode": "USER_CHANGED_MIND"
}
```

非法状态流转返回：

```json
{
  "type": "https://qyd.example/problems/order-state-conflict",
  "title": "订单状态冲突",
  "status": 409,
  "code": "ORD_40901",
  "detail": "已核销订单不能取消",
  "instance": "/api/v1/orders/1800123456789012400:cancel",
  "traceId": "2b66cf33f2aa4ea9"
}
```
