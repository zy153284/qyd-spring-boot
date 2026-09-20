# 架构与迁移基线

## 1. 架构决策

首期采用 **Spring Boot 3 模块化单体**，不是微服务：

- 单一后端进程、单一部署制品、单一 MySQL 实例，利用本地事务保证订单/库存等强一致操作。
- 代码、表、事件和 API 按领域模块隔离，为未来按支付、结算等高价值边界拆进程保留条件。
- 两个独立 Vue 3 应用：`user-web` 面向消费者，`admin-web` 同时承载商户工作台和平台运营后台。
- 旧系统仅提供业务语义和迁移源数据；新版不沿用旧包结构、URL、状态码、鉴权方式和支付双回调链路。

作出该决策的原因：当前首要风险是交易口径、权限和迁移，不是进程数量。先在同进程内形成稳定边界和自动化约束，避免在领域尚未稳定时引入网络一致性、服务发现和分布式事务成本。

## 2. 目标目录

```text
backend/
├─ pom.xml                         # 父工程/BOM
├─ bootstrap/                      # @SpringBootApplication、配置、模块装配
├─ shared-kernel/                  # Money、ID、Clock、Problem、Actor；禁止业务实体
└─ modules/
   ├─ identity/
   ├─ merchant/
   ├─ catalog/
   ├─ inventory/
   ├─ order/
   ├─ payment/
   ├─ refund/
   ├─ settlement/
   ├─ marketing/
   ├─ content/
   └─ ops/
apps/
├─ user-web/
└─ admin-web/
```

每个后端模块内部采用相同分层：

```text
com.qyd.platform.<module>
├─ api/             # 对外模块契约、公开 DTO、领域事件
├─ application/     # 用例、命令/查询、事务边界、权限调用
├─ domain/          # 聚合、值对象、领域服务、仓储接口
├─ infrastructure/  # Mapper/Repository、渠道客户端、Outbox 适配
└─ web/             # Controller、请求/响应 DTO、异常映射
```

`api` 是其他模块唯一可编译依赖的包。模块实现包使用 Java module/package-private、ArchUnit 和 Spring Modulith 测试共同约束。

## 3. 模块依赖方向

```text
identity       merchant        content
    ↑             ↑               ↑
    └──── catalog ───── marketing ┘
              ↑
          inventory
              ↑
            order
              ↑
           payment
              ↑
            refund
              ↑
          settlement

ops 仅通过公开查询契约或订阅事件观察其他模块
bootstrap 只负责装配，不承载业务规则
```

实际原则比图更严格：

1. 禁止跨模块引用 `infrastructure`、`domain`、Mapper、表对象。
2. 查询对方信息使用公开 Query API；需要历史稳定性时保存快照，不做运行时跨模块拼装。
3. 同一请求内必须立即判定的业务规则可同步调用公开 API；通知、审计、索引、统计等副作用使用事件。
4. 禁止循环依赖。出现反向需求时，通过事件、共享的只读端口或重划聚合边界解决。
5. `shared-kernel` 只允许无业务归属且长期稳定的值对象；不得成为通用工具垃圾场。

## 4. 数据所有权

首期使用同一数据库实例，建议每个模块使用独立逻辑 schema 或统一表前缀：

| 模块 | 表前缀 | 主要表 |
|---|---|---|
| identity | `iam_` | account、credential、role、permission、account_role、role_permission、data_scope、refresh_session |
| merchant | `mer_` | merchant、venue、venue_resource、merchant_staff、settlement_account |
| catalog | `cat_` | sport_category、service_product、sku、product_snapshot |
| inventory | `inv_` | inventory_slot、reservation、price_calendar |
| order | `ord_` | orders、order_item、order_status_history、verification |
| payment | `pay_` | payment、payment_attempt、callback_record、channel_statement、reconciliation_result |
| refund | `ref_` | refund、refund_status_history、refund_event |
| settlement | `set_` | settlement_rule、settlement_item、settlement、settlement_detail、ledger_entry、adjustment |
| marketing | `mkt_` | coupon_definition、user_coupon、membership_account、points_ledger |
| content | `cnt_` | article、advertisement、placement |
| ops | `ops_` | audit_log、outbox_event、consumed_event、job_execution、work_order |

- 迁移脚本按模块存放 `backend/modules/<module>/src/main/resources/db/migration`，版本号全局唯一。
- 一个模块的数据库账号在生产上只拥有本模块表写权限；过渡阶段若共用账号，代码评审和 SQL 检查仍必须执行所有权规则。
- 跨模块外键一律为逻辑 ID，不建立物理外键；模块内部强制外键/唯一键/检查约束。
- 报表跨域查询通过 `ops` 维护的投影表或只读视图，禁止业务命令依赖跨域 join。

## 5. 后端运行基线

- Java 21、Spring Boot 3.x、Spring Security 6、Spring Modulith、Flyway、MySQL 8、Redis。
- 持久化技术在工程初始化时从 MyBatis 3 或 Spring Data JPA 中选定一种；资金和并发 SQL 必须显式可审查。
- 事务默认仅包围一个应用用例；网络调用不置于数据库事务中。
- `@Transactional` 只能位于 application 层公开用例；Controller、定时任务和渠道适配器不自行拼事务。
- Outbox 与业务记录同事务写入，发布器使用抢占锁/`SKIP LOCKED`；消费者以 `eventId + consumer` 去重。
- Redis 用于权限版本、短期令牌状态、验证码、热点只读缓存和限流，不保存唯一业务事实。
- 定时任务支持多实例安全执行：分布式锁只做调度互斥，数据处理仍按记录状态和幂等键兜底。
- 配置通过 `@ConfigurationProperties` 且启动校验；密钥只来自环境变量/密钥服务，不进入 Git。

## 6. 认证、接口和两端边界

- `user-web` 只能发起消费者用例；`admin-web` 根据主体类型加载商户或平台路由，但后端始终独立校验角色与数据范围。
- 身份模块统一颁发令牌，旧 Shiro Session 不进入新版。
- 两端均使用 OpenAPI 生成的 TypeScript 客户端；领域类型可以共享生成包，不共享 Pinia store、路由和业务组件。
- 用户端优先移动端响应式/PWA；管理端桌面优先，资金操作需二次确认、复核和审计。
- 前端不得本地计算最终应付、可退、可结算金额，不得根据页面隐藏代替后端权限。
- API、错误码、金额、时间和审计遵循 `api-conventions.md`；角色矩阵遵循 `permissions.md`。

## 7. 支付、退款、结算集成

- 每个渠道实现 `PaymentChannel`/`RefundChannel` 端口，只处理签名、协议和字段映射，不直接修改订单或结算表。
- 每个渠道只有一个版本化回调入口，如 `/api/v1/payment-callbacks/wechat`；旧 `/order/notif_*` 与 `/pay/*Notify` 不在新版并存。
- 回调先验签和去重，再落渠道事实；支付模块发布事件，订单模块依法推进状态。
- 支付 `UNKNOWN`、退款 `UNKNOWN`、结算 `EXCEPTION` 必须进入主动查询和差错工单流程。
- 对账分为渠道流水对支付、支付/退款对内部账务、内部账务对结算三层；任何金额差异不得自动“改数抹平”。

## 8. 可观测、审计和故障处理

- 所有入口生成/透传 `traceId`，业务日志附加 `actorId`、`orderNo/paymentNo/refundNo/settlementNo`（存在时）。
- 核心指标：下单成功率、库存冲突率、预占积压、支付回调验签失败/延迟/重复率、未知支付数、退款成功率、结算差异额、Outbox 积压和补偿失败数。
- 告警必须关联 Runbook；资金类告警不得只依赖日志关键字。
- 健康检查区分存活和就绪，渠道不可用不应使进程失活，但应阻止相应支付方式继续下单。
- 审计日志与资金分录不可物理删除；业务数据保留期和匿名化策略由合规要求单独落表。

## 9. 测试与质量门禁

### 9.1 后端

- 单元测试：聚合不变量、每条状态转换、金额舍入、权限判定。
- 模块测试：Spring Modulith/ArchUnit 验证无循环依赖和非法包引用。
- 集成测试：Testcontainers 启动 MySQL/Redis，验证唯一约束、条件扣库存、事务和 Outbox。
- 契约测试：OpenAPI、支付渠道签名样例、回调成功/重复/乱序/金额不符。
- 并发测试：同一库存单元至少 100 并发请求，成交量不得超过容量；同一幂等键并发只创建一条业务记录。
- 账务测试：订单、支付、部分退款、跨期退款、结算和调账逐笔平衡。

### 9.2 前端

- TypeScript 严格模式、ESLint、单元测试；关键流程使用 Playwright。
- 用户端覆盖：登录、查时段、下单、支付结果查询、取消、退款申请。
- 管理端覆盖：数据范围、核销、退款审批/复核、结算生成/打款、权限变更。
- CI 顺序：格式/静态检查 → 单测 → 模块结构测试 → 集成测试 → 契约兼容 → 前端构建/E2E → 镜像与 SBOM。

## 10. 一次性迁移边界

### 10.1 来源与目标

- 数据源仅为同级旧项目及其实际生产数据库：`qyd_dongqil` 为后端业务事实，`ATqyd` 和 `qyd_mp` 用于页面流程和字段语义参考。
- 三个旧仓库始终只读；迁移代码、映射、报告和修复 SQL 全部放入新仓库 `migration/`，不得回写旧代码。
- 新版上线采用一次业务切换：切换完成后旧系统只读封存，不长期双写、不把旧系统当备用写主库。

### 10.2 纳入迁移

- 有效消费者账号与资料、微信绑定关系（经合法性校验和重新加密/散列）。
- 已审核商户、场馆、运动分类、有效服务商品/SKU、资源和可解释的价格规则。
- 未完结订单、法定/业务保留期内历史订单及明细、兑换/核销事实。
- 支付成功流水、退款单、结算单、结算明细和支持勾稽的资金事实。
- 有效优惠券、会员卡余额/积分台账（能证明余额来源时）。
- 当前有效运营/商户账号和组织归属；角色权限按新版权限字典重新映射，不原样复制 URL 权限。
- 依法需要保留的公告、资讯、审计证据和附件元数据。

### 10.3 不纳入迁移

- Shiro Session、Redis 缓存、验证码、临时令牌、Quartz 锁/执行现场、API 临时日志。
- 旧支付/短信/微信密钥、明文密码、无法证明算法安全性的密码摘要；相关账号执行重置密码。
- 已失效且无保留价值的草稿、测试账号、测试订单、重复回调、临时导出文件和孤立附件。
- 旧版 URL 权限、菜单树 ID、任意匿名白名单和前端本地登录态。
- 无法确定商户/用户/订单归属的孤儿记录；先进入隔离报告，不为了行数相等制造错误关联。
- 团购、红包、公益、天气、停车/餐饮等未进入新版范围的数据，除非产品在切换前签署新增范围。

### 10.4 状态与标识映射

- 每类旧数据建立 `migration_id_map(sourceSystem, sourceTable, sourceId, targetType, targetId, batchId)`，业务表不依赖旧主键。
- 旧数字/字符串状态映射为新版枚举，必须形成版本化映射表；无法唯一映射的记录进入人工队列。
- `shopId → venueId`，旧服务订单和场地订单统一进入新版 Order，但通过 `orderType` 和快照保留来源差异。
- 金额从旧 `double` 读取后按原始数据库值/渠道账单交叉验证，以渠道实收和财务确认规则定案，不能直接二进制浮点转账。
- 时间按旧系统实际时区解释后转 UTC；无法确定时区的批次不得上线。
- 每条迁移记录保留 `sourceSystem/sourceTable/sourceId/migratedAt/batchId` 审计元数据。

### 10.5 执行步骤

1. **盘点冻结**：导出表、字段、行数、金额、状态、关联和数据质量；冻结映射规则与迁移范围。
2. **目标建模**：Flyway 建表、唯一约束和索引；准备脱敏测试库和可重复 ETL。
3. **全量演练**：至少两次从快照全量迁移；脚本按源主键分片，可安全重跑。
4. **校验**：逐表行数、主外逻辑关系、订单/支付/退款/结算金额、状态终态、孤儿数据和 1,000 笔资金样本。
5. **增量追平**：短期使用 binlog CDC 或只读窗口后的增量脚本；增量按源事件 ID 幂等，不采用长期应用双写。
6. **停写切换**：进入维护窗口，停旧系统写入和任务，记录 binlog 位点，执行末次增量并复核资金总额。
7. **启用新版**：先开放只读，再开放登录/下单，最后开放支付、退款、结算；每阶段有明确止损阈值。
8. **观察封存**：旧库保持只读至少一个完整退款与结算周期；完成对账后归档并撤销旧应用写账号。

### 10.6 验收与回滚

必须同时通过：

- 范围内实体行数差异可解释，主记录无孤儿；账号、商户、场馆映射唯一。
- 按日/渠道/商户汇总的支付成功、退款成功、待结算、已结算金额与旧库及渠道账单一致。
- 所有未完结订单、支付未知单、退款处理中单、结算异常单均有新版对应记录和责任工单。
- 抽样可从订单追到支付、退款、核销、结算及源记录；敏感数据没有明文泄漏。
- 新版权限抽样证明商户、城市、消费者之间无越权。

回滚只允许发生在新版开放写入前，或在受控窗口内将新版产生的少量新交易逐笔导回且完成资金对账后切回。开放大规模新支付后禁止简单切回旧库；此时采用停止新交易、修复/前滚方案，避免两个写主库。

## 11. 未来拆分条件

只有同时满足以下条件才评审微服务化：

- 模块公开 API 和事件至少两个版本周期稳定；
- 没有跨模块表写入和核心跨域 join；
- Outbox、幂等、对账、监控已在线验证；
- 目标模块有独立伸缩/隔离价值和明确负责人；
- 跨模块一致性已能用事件与补偿表达。

支付、退款、结算可以作为候选拆分组，但拆分是后续架构决策，不属于本次一次性迁移范围。
