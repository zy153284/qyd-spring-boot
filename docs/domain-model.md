# 领域模型基线

## 1. 统一语言

- **商户（Merchant）**：经营主体和结算主体。
- **场馆/门店（Venue）**：商户下提供服务的线下地点；旧系统 `shop` 在新版统一映射为 `venue`。
- **服务商品（ServiceProduct）**：可被购买的运动服务定义，例如羽毛球场 1 小时。
- **SKU**：服务商品的可定价、可售卖规格。
- **库存单元（InventorySlot）**：`venueId + resourceId + serviceDate + startTime + endTime + skuId` 唯一确定的可售时段。
- **预占（Reservation）**：下单后、支付完成前对库存单元的限时占用。
- **订单（Order）**：用户购买服务的业务合同，保存商品、场馆、价格和优惠快照。
- **支付单（Payment）**：一次支付意图；一次订单可因失败/换渠道产生多张支付单，但最多一张成功。
- **退款单（Refund）**：针对已确认支付的退款请求，允许多次部分退款但累计不得超过可退金额。
- **核销（Verification）**：按兑换码确认服务已履约。
- **结算资格项（SettlementItem）**：已履约且过退款冻结期的商户应收明细。
- **结算单（Settlement）**：一个商户在一个结算周期内的一组结算资格项及打款结果。
- **运营人员（Operator）**、**商户人员（MerchantStaff）**、**消费者（Consumer）**：三类身份主体。

## 2. 限界上下文与聚合

| 模块 | 聚合根 | 拥有的数据与职责 | 允许依赖 |
|---|---|---|---|
| `identity` 身份权限 | `Account`、`Role` | 账号、凭据、令牌、角色、权限、数据范围 | 无业务模块 |
| `merchant` 商户场馆 | `Merchant`、`Venue` | 商户、场馆、员工归属、资质、结算账户引用 | identity |
| `catalog` 商品目录 | `ServiceProduct`、`SportCategory` | 运动类型、服务商品、SKU、上下架、图文快照源 | merchant |
| `inventory` 库存 | `InventorySlot`、`Reservation` | 资源、价格日历、时段容量、预占/确认/释放 | catalog、merchant |
| `order` 订单履约 | `Order`、`Verification` | 订单、订单项、计价快照、取消、履约、兑换码 | inventory、catalog |
| `payment` 支付 | `Payment` | 渠道下单、回调、支付确认、支付流水、对账原文 | order（只读契约） |
| `refund` 退款 | `Refund` | 退款申请、审核、渠道退款、退款确认 | payment、order |
| `settlement` 结算 | `Settlement` | 清分规则、资格项、冻结、结算单、打款、调账 | order、payment、refund、merchant |
| `marketing` 营销 | `CouponDefinition`、`UserCoupon`、`MembershipAccount` | 优惠券、会员卡、积分台账；只输出优惠结果 | identity、catalog |
| `content` 内容 | `Article`、`Advertisement` | 公告、资讯、广告位、发布状态 | identity |
| `ops` 运营支撑 | `AuditLog`、`ReconciliationDiff` | 审计、任务执行、对账差错、人工处理工单 | 订阅各模块事件 |

规则：跨模块只引用对方聚合 ID 和不可变快照，不设置数据库外键；模块内部可以使用外键。任何跨模块反向写表均视为架构违规。

## 3. 核心实体与关系

```text
Account 1---n AccountRole n---1 Role n---n Permission
Merchant 1---n Venue 1---n Resource
Venue 1---n ServiceProduct 1---n SKU
Resource/SKU 1---n InventorySlot 1---n Reservation
Consumer 1---n Order 1---n OrderItem
Order 1---n Payment 1---n PaymentAttempt/CallbackRecord
Payment 1---n Refund
Order 1---n Verification
Merchant 1---n Settlement 1---n SettlementItem
OrderItem/Payment/Refund 1---n LedgerEntry（不可变账务分录）
```

### 3.1 关键字段与不变量

- 全部聚合根使用 64 位分布式 ID；外部单号使用带前缀、日期和随机段的不可猜测字符串，并有唯一索引。
- `Order` 保存 `consumerId`、`merchantId`、`venueId` 及名称/地址/商品/单价/结算规则版本快照。后续主数据变更不得改写历史订单。
- `OrderItem.quantity > 0`；`unitPrice × quantity - discount + fee = payableAmount`，舍入模式固定 `HALF_UP` 到 2 位。
- `Payment.requestAmount = Order.payableAmount - 已确认支付金额`；首期不支持组合支付。一笔订单最多一个 `SUCCEEDED` 支付单，由数据库唯一约束兜底。
- `Refund.confirmedAmount` 累加不得大于 `Payment.succeededAmount - 已确认退款金额`。
- `LedgerEntry` 只追加不更新；冲正必须增加反向分录。每个资金事件的借贷合计必须为零。
- `Verification` 的兑换码仅存哈希和后四位；明文只在创建时返回一次。一个订单项的核销次数不得超过购买数量。
- `SettlementItem` 以 `orderItemId + ruleVersion + itemType` 唯一；退款冲减生成负向项，不删除原资格项。
- 所有聚合含 `version` 乐观锁、`createdAt/createdBy/updatedAt/updatedBy`；资金记录额外含 `occurredAt` 和不可变原始事件号。

## 4. 状态机

状态存储使用稳定英文枚举；中文仅用于展示。每次流转必须校验“当前状态 + 事件 + version”，并写状态历史。

### 4.1 订单状态

```text
CREATED（已创建，库存已预占）
  ├─ 发起支付 → PAYMENT_PENDING
  ├─ 用户取消/预占超时 → CANCELLED
  └─ 计价或库存确认失败 → CLOSED
PAYMENT_PENDING
  ├─ 支付成功确认 → PAID
  ├─ 用户取消且渠道未成功 → CANCELLED
  └─ 支付超时且主动查单未成功 → CLOSED
PAID
  ├─ 生成可核销凭证 → FULFILLMENT_PENDING
  ├─ 全额退款确认且未履约 → REFUNDED
  └─ 风控关闭（仅补偿命令）→ CLOSED
FULFILLMENT_PENDING
  ├─ 全部核销 → FULFILLED
  ├─ 部分核销 → PARTIALLY_FULFILLED
  └─ 全额退款确认且无核销 → REFUNDED
PARTIALLY_FULFILLED
  ├─ 全部核销 → FULFILLED
  └─ 可退未履约部分退款 → 保持 PARTIALLY_FULFILLED
FULFILLED → 终态（退款状态由退款单表达，不倒退订单状态）
CANCELLED / CLOSED / REFUNDED → 终态
```

- 订单状态不包含 `REFUNDING`、`SETTLING`；退款和结算有独立状态机。
- 支付成功回调晚于取消时，先主动查单：渠道成功则执行“迟到支付补偿”，恢复为 `PAID` 并确认库存；无法履约则自动发起全额退款，禁止丢弃成功支付。

### 4.2 支付状态

```text
CREATED → PROCESSING → SUCCEEDED
                  ├─ 渠道明确失败 → FAILED
                  └─ 超时未知 → UNKNOWN
UNKNOWN ├─ 主动查单成功 → SUCCEEDED
        ├─ 主动查单失败 → FAILED
        └─ 超过补偿期限 → CLOSED（需差错单）
CREATED / FAILED → CLOSED
```

- 渠道回调、主动查询、前端同步跳转均调用同一个 `confirmPayment` 用例；前端跳转结果不能作为支付成功依据。
- `SUCCEEDED` 只能由验签成功且订单号、商户号、币种、金额完全匹配的渠道事实触发，之后不可回退。

### 4.3 退款状态

```text
REQUESTED
  ├─ 无需人工审核/审核通过 → APPROVED
  └─ 审核拒绝 → REJECTED
APPROVED → PROCESSING
PROCESSING
  ├─ 渠道确认全额到账 → SUCCEEDED
  ├─ 渠道确认部分到账 → PARTIALLY_SUCCEEDED
  ├─ 明确失败 → FAILED（可按同一退款单重试）
  └─ 结果未知 → UNKNOWN → 主动查询后进入确定状态
REQUESTED → CANCELLED（仅申请人且尚未审核）
SUCCEEDED / PARTIALLY_SUCCEEDED / REJECTED / CANCELLED → 终态
```

- 审核通过和执行退款是两个权限点；大额退款执行支持双人复核。
- 退款确认后发布 `refund.confirmed`，由订单计算可退余额、库存按业务规则恢复、结算生成冲减/调账项。

### 4.4 结算状态

```text
DRAFT（已汇集资格项）
  ├─ 校验平衡 → CONFIRMED
  └─ 作废 → CANCELLED
CONFIRMED → FROZEN（冻结明细，禁止被其他结算单引用）
FROZEN
  ├─ 发起打款 → PAYING
  └─ 发现差异 → EXCEPTION
PAYING
  ├─ 打款成功 → PAID
  ├─ 明确失败 → FAILED（修复后重试）
  └─ 结果未知 → EXCEPTION
PAID → COMPLETED（对账通过）
PAID → EXCEPTION（对账不平）
EXCEPTION → FROZEN / PAYING / COMPLETED（处理工单审批后）
CANCELLED / COMPLETED → 终态
```

- 只有已支付、已履约、超过退款冻结期且未被结算的明细可进入 `DRAFT`。
- `PAID` 后发生退款不回写原结算单，生成下一期负向调账项；冻结期内退款直接使资格项失效或冲减。

## 5. 库存并发规则

### 5.1 真相源与约束

- MySQL `inventory_slot` 是最终真相源，唯一键为库存单元自然键；Redis 只缓存可售量。
- 容量模型字段：`capacity`、`reservedQuantity`、`soldQuantity`、`version`，始终满足 `0 ≤ reserved + sold ≤ capacity`。
- 预占采用单条条件更新：

```sql
UPDATE inventory_slot
SET reserved_quantity = reserved_quantity + :qty, version = version + 1
WHERE id = :slotId
  AND version = :version
  AND capacity - reserved_quantity - sold_quantity >= :qty;
```

- 影响行数为 0 即冲突/售罄，允许最多 2 次带抖动重试；禁止先查后扣。
- 跨多个时段的订单按 `slotId` 升序锁定；任一失败则本地事务全部回滚，防止死锁和部分预占。

### 5.2 预占、确认与释放

- 创建订单的同一本地事务内完成库存预占、`Reservation(HELD)`、订单创建和 Outbox 写入。
- `expiresAt` 使用数据库时间，默认 15 分钟；支付单有效期不得晚于预占到期时间。
- 支付成功：`HELD → CONFIRMED`，原子地执行 `reserved -= qty, sold += qty`。
- 取消/超时：`HELD → RELEASED`，原子地执行 `reserved -= qty`；状态条件保证只释放一次。
- 超时任务采用 `select ... for update skip locked` 分片扫描；每条释放仍校验状态，任务可重复执行。
- 支付与释放并发时以行锁和状态 CAS 决胜；失败方重新读取。若支付已成功但预占已释放，进入迟到支付补偿，不允许静默超卖。

## 6. 幂等与一致性

| 场景 | 幂等键 | 数据库保障 | 重复请求结果 |
|---|---|---|---|
| 创建订单 | `principalId + Idempotency-Key` | `idempotency_record` 唯一键 | 返回首次订单和 HTTP 200 |
| 库存预占/释放 | `orderId + slotId + action` | reservation 唯一键 + 状态 CAS | 返回当前预占状态 |
| 支付下单 | `orderNo + channel + clientRequestNo` | payment 外部单号唯一 | 返回已有支付单 |
| 支付回调 | `channel + channelEventId`；无事件号时用规范化报文摘要 | callback_record 唯一键 | 验签后直接返回渠道成功回执 |
| 退款申请 | `paymentId + clientRefundNo` | clientRefundNo 唯一 | 返回已有退款单 |
| 退款回调 | `channel + channelRefundNo + eventType` | refund_event 唯一 | 返回成功，不重复冲账 |
| 核销 | `verificationCodeHash + occurrence` | 核销次数/事件唯一约束 | 返回已有核销结果 |
| 结算生成 | `merchantId + period + ruleVersion` | settlement 唯一键 | 返回已有结算单 |
| Outbox 消费 | `eventId + consumerName` | consumed_event 唯一键 | 跳过已消费事件 |

通用规则：

1. `POST` 创建、命令和资金接口必须接收 `Idempotency-Key`；同键同主体同端点保存 24 小时，资金类永久保存业务去重事实。
2. 同键请求体摘要不同返回 `40900 IDEMPOTENCY_KEY_REUSED`，不得覆盖首次结果。
3. 幂等记录与业务数据同事务提交；处理中请求返回 `40901 REQUEST_IN_PROGRESS` 和建议重试间隔。
4. 事件至少一次投递，消费者必须幂等；不假设 MQ 恰好一次。
5. 禁止在数据库事务中等待支付渠道网络调用。先落支付单，提交后调用渠道，再用状态机更新。
6. 所有补偿任务均使用稳定幂等键、有限重试和死信/人工工单，不允许无限重试。

## 7. 领域事件最小集合

- `order.created`、`order.cancelled`、`order.paid`、`order.fulfilled`
- `inventory.reserved`、`inventory.released`、`inventory.confirmed`
- `payment.succeeded`、`payment.failed`、`payment.exception.detected`
- `refund.requested`、`refund.approved`、`refund.confirmed`
- `settlement.item.eligible`、`settlement.frozen`、`settlement.paid`、`settlement.completed`
- `merchant.approved`、`venue.changed`、`product.published`
- `permission.changed`、`audit.recorded`

事件信封固定包含 `eventId`、`eventType`、`eventVersion`、`aggregateType`、`aggregateId`、`occurredAt`、`traceId`、`actor`、`payload`。已有版本不可修改，只能新增版本并提供兼容消费者。
