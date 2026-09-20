# 认证、角色与权限基线

## 1. 主体与信任边界

系统只有三类主体：

- `CONSUMER`：用户端消费者，只能访问本人资源。
- `MERCHANT_STAFF`：商户/场馆人员，必须绑定 `merchantId`，可选绑定一组 `venueIds`。
- `OPERATOR`：平台运营人员，按组织、城市/区域和职责授权。

访问令牌最少携带 `subjectType`、`subjectId`、`sessionId`、`clientId`、`issuedAt`、`expiresAt`。角色、权限和数据范围以服务端缓存/数据库为准，令牌只携带版本号或最小声明，权限变更后可立即使会话失效。

公共接口白名单仅包括：登录/刷新令牌、验证码、公开城市/运动分类、已发布场馆/商品/内容查询、支付渠道回调。回调使用渠道验签，不属于匿名业务接口。其余接口默认认证。

## 2. 权限模型

采用 `RBAC + DataScope + ResourceOwnership`：

1. **功能权限**：格式为 `<模块>:<资源>:<动作>`，如 `order:order:read`、`refund:refund:approve`。
2. **数据范围**：
   - `SELF`：本人数据；
   - `VENUE`：授权场馆集合；
   - `MERCHANT`：本商户全部场馆；
   - `CITY`：授权城市集合；
   - `REGION`：授权行政区域；
   - `ALL`：全平台，仅少数平台角色可用。
3. **资源归属**：服务层根据订单/退款/结算实体反查真实 `consumerId/merchantId/venueId`，禁止依赖请求参数判断归属。
4. **敏感约束**：退款执行、结算打款、角色授权、结算账户变更需二次认证；大额阈值以上采用申请人与复核人分离。

## 3. 标准角色

| 角色码 | 主体 | 说明 | 默认数据范围 |
|---|---|---|---|
| `CONSUMER` | 消费者 | 用户端默认角色 | SELF |
| `MERCHANT_VIEWER` | 商户人员 | 只读经营数据 | VENUE |
| `VENUE_CLERK` | 商户人员 | 前台查单、核销 | VENUE |
| `MERCHANT_OPERATOR` | 商户人员 | 商品、库存、订单运营 | MERCHANT |
| `MERCHANT_FINANCE` | 商户人员 | 流水、退款申请、结算查看 | MERCHANT |
| `MERCHANT_ADMIN` | 商户人员 | 本商户员工和业务配置管理 | MERCHANT |
| `OPS_SERVICE` | 平台运营 | 用户/订单客服与工单 | CITY/REGION |
| `OPS_CONTENT` | 平台运营 | 内容和营销运营 | CITY/REGION |
| `OPS_FINANCE` | 平台运营 | 退款复核、对账、结算 | CITY/REGION |
| `OPS_AUDITOR` | 平台运营 | 全局只读审计 | ALL |
| `OPS_ADMIN` | 平台运营 | 平台配置、账号和角色管理 | ALL |

标准角色是模板，可复制为自定义角色，但系统保护角色不可删除、不可授予超出创建者自身的权限和数据范围。

## 4. 功能权限矩阵

图例：`读`=查询，`写`=新增/修改，`审`=审核，`执`=执行资金动作，`配`=授权配置，`—`=无权。所有能力仍受数据范围限制。

| 业务能力 | 消费者 | 场馆前台 | 商户运营 | 商户财务 | 商户管理员 | 客服运营 | 内容运营 | 平台财务 | 审计员 | 平台管理员 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 本人资料/会话 | 读写 | — | — | — | — | 受控读 | — | — | 只读审计 | 配 |
| 商户/场馆资料 | 公开读 | 读 | 读写 | 读 | 读写 | 读 | 读 | 读 | 读 | 审/配 |
| 商品与价格 | 公开读 | 读 | 读写 | 读 | 读写 | 读 | 读 | 读 | 读 | 审/配 |
| 时段库存 | 公开读 | 读 | 读写 | 读 | 读写 | 读 | — | — | 读 | 配 |
| 创建/支付订单 | 本人写 | — | — | — | — | — | — | — | 读 | — |
| 订单查询 | 本人读 | 场馆读 | 商户读 | 商户读 | 商户读 | 范围读 | — | 范围读 | 全部读 | 全部读 |
| 取消订单 | 本人写 | 受控写 | 商户写 | — | 商户写 | 受控写 | — | — | — | 受控写 |
| 核销/撤销核销 | — | 写/申请 | 写/申请 | — | 写/申请 | 审 | — | — | 读 | 审 |
| 退款申请 | 本人写 | 代申请 | 代申请 | 商户申请 | 代申请 | 代申请 | — | — | 读 | — |
| 退款审核 | — | — | — | — | — | 小额审 | — | 审 | 读 | 审 |
| 渠道退款执行 | — | — | — | — | — | — | — | 执/复核 | 读 | 应急执 |
| 支付流水/对账 | 本人摘要 | — | 摘要读 | 商户读 | 商户读 | 摘要读 | — | 读写 | 全部读 | 配 |
| 结算单 | — | — | 摘要读 | 商户读/确认 | 商户读 | 摘要读 | — | 读写/执 | 全部读 | 配 |
| 优惠券/积分 | 本人读用 | — | 商户读 | — | 商户读 | 受控调整 | 读写/发布 | 对账读 | 全部读 | 配 |
| 公告/资讯/广告 | 公开读 | 读 | 读 | — | 读 | 读 | 读写/发布 | — | 读 | 配 |
| 用户/员工账号 | 本人安全设置 | — | — | — | 本商户配 | 范围读/冻结申请 | — | — | 读 | 审/配 |
| 角色与权限 | — | — | — | — | 本商户受限配 | — | — | — | 读 | 配 |
| 审计日志 | 本人安全记录 | — | — | 商户资金审计读 | 商户审计读 | 范围读 | 自身操作读 | 资金审计读 | 全部读 | 全部读 |

## 5. 权限点清单

实现时以下权限码必须作为初始化数据进入数据库，菜单只引用权限码，不能把菜单本身当权限：

- 身份：`identity:account:read|create|update|freeze`、`identity:role:read|create|update|grant`、`identity:audit:read`
- 商户：`merchant:merchant:read|update|approve`、`merchant:venue:read|create|update|approve`、`merchant:staff:read|manage`
- 目录：`catalog:product:read|create|update|publish`、`catalog:category:manage`
- 库存：`inventory:slot:read|update|close`
- 订单：`order:order:read|create|cancel`、`order:verification:read|execute|revoke|approve`
- 支付：`payment:payment:read`、`payment:reconciliation:read|handle`
- 退款：`refund:refund:read|request|approve|execute|review`
- 结算：`settlement:settlement:read|generate|confirm|pay|review`、`settlement:account:read|update|approve`
- 营销：`marketing:coupon:read|create|publish|revoke`、`marketing:points:read|adjust|approve`
- 内容：`content:article:read|create|update|publish`、`content:advertisement:manage`
- 运维：`ops:job:read|retry`、`ops:config:read|update`、`ops:audit:read`

`read` 不自动包含敏感字段。手机号、支付账号、身份证明、结算账户默认脱敏；查看明文需要额外 `*:sensitive:read` 权限并记录原因和审计。

## 6. 数据权限判定规则

接口层只判断功能权限；应用服务必须调用统一 `DataScopeGuard`：

```text
允许访问 =
  有功能权限
  AND 主体类型匹配
  AND 实体归属落在有效数据范围
  AND 实体未被租户/商户隔离规则排除
```

- 消费者：路径中的订单 ID 必须反查属于当前 `subjectId`；不接受 `userId` 代替校验。
- 商户人员：账号必须处于启用状态且仍属于商户；若范围为 VENUE，实体 `venueId` 必须在授权集合。
- 平台人员：CITY/REGION 范围按实体下单时的城市快照判断，避免场馆迁移后历史数据权限漂移。
- 导出权限与页面查询权限分离；导出需 `*:export`、限制时间范围和行数，并生成异步任务与下载审计。
- 列表查询必须在 SQL 条件中应用数据范围，不能查全量后在内存过滤。

## 7. 高风险操作控制

| 操作 | 必需控制 |
|---|---|
| 退款审核/执行 | 申请人与审核人分离；大额双人复核；金额和收款原路不可由客户端修改 |
| 结算账户变更 | 二次认证、旧值/新值加密审计、至少一名平台财务复核、生效冷静期 |
| 结算打款 | 结算单冻结、借贷平衡、操作者与复核者分离、渠道结果主动查询 |
| 核销撤销 | 仅在未结算且规则允许时申请，由有审批权人员处理；保留正反事件 |
| 角色授权 | 不得提升到授权者不具备的权限或数据范围；平台管理员不能修改自己的关键权限 |
| 人工调账 | 工单、原因、附件、双人复核，只生成冲正/调整分录，不改历史分录 |

## 8. 前端与后端执行要求

- 两个 Vue 应用可以用权限码控制菜单和按钮显示，但这只用于体验；后端每个命令仍必须鉴权。
- 管理端路由元数据使用 `requiredPermissions`，后端返回当前用户有效权限和数据范围摘要。
- 返回 `401` 时尝试一次安全刷新令牌；返回 `403` 不得循环刷新，应显示无权并记录 traceId。
- 权限缓存键必须包含 `permissionVersion`；授权变更提交后发布事件清理相关账号会话/缓存。
- 每个权限点至少有四类自动化测试：无令牌 401、无权限 403、越数据范围 404、合法访问成功。
